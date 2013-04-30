/*******************************************************************************
 * Copyright 2013 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package emlab.gen.role.investment;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.data.annotation.Transient;
import org.springframework.data.neo4j.annotation.NodeEntity;
import org.springframework.data.neo4j.aspects.core.NodeBacked;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.transaction.annotation.Transactional;

import agentspring.role.Role;
import cern.jet.random.Empirical;
import cern.jet.random.EmpiricalWalker;
import emlab.gen.domain.agent.BigBank;
import emlab.gen.domain.agent.EnergyProducer;
import emlab.gen.domain.agent.EnergyProducerTechnologyPreferences;
import emlab.gen.domain.agent.PowerPlantManufacturer;
import emlab.gen.domain.contract.CashFlow;
import emlab.gen.domain.contract.Loan;
import emlab.gen.domain.gis.Zone;
import emlab.gen.domain.market.ClearingPoint;
import emlab.gen.domain.market.electricity.ElectricitySpotMarket;
import emlab.gen.domain.market.electricity.Segment;
import emlab.gen.domain.market.electricity.SegmentLoad;
import emlab.gen.domain.policy.PowerGeneratingTechnologyTarget;
import emlab.gen.domain.technology.PowerGeneratingTechnology;
import emlab.gen.domain.technology.PowerGeneratingTechnologyNodeLimit;
import emlab.gen.domain.technology.PowerGridNode;
import emlab.gen.domain.technology.PowerPlant;
import emlab.gen.domain.technology.Substance;
import emlab.gen.domain.technology.SubstanceShareInFuelMix;
import emlab.gen.repository.Reps;
import emlab.gen.util.GeometricTrendRegression;
import emlab.gen.util.MapValueComparator;

/**
 * @author Ruben; algorithm extends the current algorithm with technology
 *         preferences based upon selection of criteria.
 * 
 * 
 */

@Configurable
@NodeEntity
public class CopyOfInvestInPowerGenerationTechnologiesWithPreferencesRole<T extends EnergyProducerTechnologyPreferences>
        extends GenericInvestmentRole<T> implements Role<T>, NodeBacked {

    @Transient
    @Autowired
    Reps reps;

    @Transient
    @Autowired
    Neo4jTemplate template;

    // market expectations
    @Transient
    Map<ElectricitySpotMarket, MarketInformation> marketInfoMap = new HashMap<ElectricitySpotMarket, MarketInformation>();

    @Override
    public void act(T agent) {

        long futureTimePoint = getCurrentTick() + agent.getInvestmentFutureTimeHorizon();
        // logger.warn(agent + " is looking at timepoint " + futureTimePoint);

        // ==== Expectations ===

        Map<Substance, Double> expectedFuelPrices = predictFuelPrices(agent, futureTimePoint);

        // CO2
        Map<ElectricitySpotMarket, Double> expectedCO2Price = determineExpectedCO2PriceInclTax(futureTimePoint,
                agent.getNumberOfYearsBacklookingForForecasting());

        // Demand
        Map<ElectricitySpotMarket, Double> expectedDemand = new HashMap<ElectricitySpotMarket, Double>();
        for (ElectricitySpotMarket elm : reps.template.findAll(ElectricitySpotMarket.class)) {
            GeometricTrendRegression gtr = new GeometricTrendRegression();
            for (long time = getCurrentTick(); time > getCurrentTick()
                    - agent.getNumberOfYearsBacklookingForForecasting()
                    && time >= 0; time = time - 1) {
                gtr.addData(time, elm.getDemandGrowthTrend().getValue(time));
            }
            expectedDemand.put(elm, gtr.predict(futureTimePoint));
        }

        // this works
        if (agent.getWeightfactorEfficiency() == 0 && agent.getWeightfactorEmission() == 0
                && agent.getWeightfactorFuelPrice() == 0 && agent.getWeightfactorLifeTime() == 0
                && agent.getWeightfactorMinimalRunningHours() == 0 && agent.getWeightfactorProfit() == 0
                && agent.getWeigthfactorInvestmentCost() == 0) {

            agent.setInvestorIncludeSubjectiveFactor(false);

        } else {

            agent.setInvestorIncludeSubjectiveFactor(true);

        }

        // logger.warn(agent + " has subjective factors " +
        // agent.isInvestorIncludeSubjectiveFactor());

        // Investment decision
        for (ElectricitySpotMarket market : reps.genericRepository.findAllAtRandom(ElectricitySpotMarket.class)) {

            MarketInformation marketInformation = new MarketInformation(market, expectedDemand, expectedFuelPrices,
                    expectedCO2Price.get(market).doubleValue(), futureTimePoint);

            /*
             * if (marketInfoMap.containsKey(market) &&
             * marketInfoMap.get(market).time == futureTimePoint) {
             * marketInformation = marketInfoMap.get(market); } else {
             * marketInformation = new MarketInformation(market,
             * expectedFuelPrices, expectedCO2Price, futureTimePoint);
             * marketInfoMap.put(market, marketInformation); }
             */

            // logger.warn(agent + " is expecting a CO2 price of " +
            // expectedCO2Price.get(market) + " Euro/MWh at timepoint "
            // + futureTimePoint + " in Market " + market);

            double highestValue = Double.MIN_VALUE;
            PowerGeneratingTechnology bestTechnology = null;

            // Variables for MCDA
            // Cumulative variable criteria for MCDA
            double npvTotal = 0d;
            double footprintTotal = 0d;
            double efficiencyTotal = 0d;
            double lifetimeTotal = 0d;
            double investmentCostTotal = 0d;
            double minimalRunningHoursTotal = 0d;
            // double fuelpriceVolatilityTotal = Double.MIN_VALUE;

            // propensities
            double highestpropensity = 0d;
            double lowestpropensity = 0d;

            for (PowerGeneratingTechnology technology : reps.genericRepository.findAll(PowerGeneratingTechnology.class)) {

                PowerPlant plant = new PowerPlant();
                plant.specifyNotPersist(getCurrentTick(), agent, getNodeForZone(market.getZone()), technology);
                // if too much capacity of this technology in the pipeline (not
                // limited to the 5 years)
                double expectedInstalledCapacityOfTechnology = reps.powerPlantRepository
                        .calculateCapacityOfExpectedOperationalPowerPlantsInMarketAndTechnology(market, technology,
                                futureTimePoint);
                PowerGeneratingTechnologyTarget technologyTarget = reps.powerGenerationTechnologyTargetRepository
                        .findOneByTechnologyAndMarket(technology, market);
                if (technologyTarget != null) {
                    double technologyTargetCapacity = technologyTarget.getTrend().getValue(futureTimePoint);
                    expectedInstalledCapacityOfTechnology = (technologyTargetCapacity > expectedInstalledCapacityOfTechnology) ? technologyTargetCapacity
                            : expectedInstalledCapacityOfTechnology;
                }
                double pgtNodeLimit = Double.MAX_VALUE;
                PowerGeneratingTechnologyNodeLimit pgtLimit = reps.powerGeneratingTechnologyNodeLimitRepository
                        .findOneByTechnologyAndNode(technology, plant.getLocation());
                if (pgtLimit != null) {
                    pgtNodeLimit = pgtLimit.getUpperCapacityLimit(futureTimePoint);
                }
                double expectedInstalledCapacityOfTechnologyInNode = reps.powerPlantRepository
                        .calculateCapacityOfExpectedOperationalPowerPlantsByNodeAndTechnology(plant.getLocation(),
                                technology, futureTimePoint);
                double expectedOwnedTotalCapacityInMarket = reps.powerPlantRepository
                        .calculateCapacityOfExpectedOperationalPowerPlantsInMarketByOwner(market, futureTimePoint,
                                agent);
                double expectedOwnedCapacityInMarketOfThisTechnology = reps.powerPlantRepository
                        .calculateCapacityOfExpectedOperationalPowerPlantsInMarketByOwnerAndTechnology(market,
                                technology, futureTimePoint, agent);
                double capacityOfTechnologyInPipeline = reps.powerPlantRepository
                        .calculateCapacityOfPowerPlantsByTechnologyInPipeline(technology, getCurrentTick());
                double operationalCapacityOfTechnology = reps.powerPlantRepository
                        .calculateCapacityOfOperationalPowerPlantsByTechnology(technology, getCurrentTick());

                if ((expectedInstalledCapacityOfTechnology + plant.getActualNominalCapacity())
                        / (marketInformation.maxExpectedLoad + plant.getActualNominalCapacity()) > technology
                            .getMaximumInstalledCapacityFractionInCountry()) {
                    // logger.warn(agent +
                    // " will not invest in {} technology because there's too much of this type in the market",
                    // technology);
                } else if ((expectedInstalledCapacityOfTechnologyInNode + plant.getActualNominalCapacity()) > pgtNodeLimit) {

                } else if (expectedOwnedCapacityInMarketOfThisTechnology > expectedOwnedTotalCapacityInMarket
                        * technology.getMaximumInstalledCapacityFractionPerAgent()) {
                    // logger.warn(agent +
                    // " will not invest in {} technology because there's too much capacity planned by him",
                    // technology);
                } else if ((capacityOfTechnologyInPipeline > operationalCapacityOfTechnology)
                        && capacityOfTechnologyInPipeline > 3000) { // TODO:
                    // reflects that you cannot expand a technology out of zero.
                    // logger.warn(agent +
                    // " will not invest in {} technology because there's too much capacity in the pipeline",
                    // technology);
                } else if (plant.getActualInvestedCapital() * (1 - agent.getDebtRatioOfInvestments()) > agent
                        .getDownpaymentFractionOfCash() * agent.getCash()) {
                    // logger.warn(agent +
                    // " will not invest in {} technology as he does not have enough money for downpayment",
                    // technology);
                } else {

                    Map<Substance, Double> myFuelPrices = new HashMap<Substance, Double>();
                    for (Substance fuel : technology.getFuels()) {
                        myFuelPrices.put(fuel, expectedFuelPrices.get(fuel));
                    }
                    Set<SubstanceShareInFuelMix> fuelMix = calculateFuelMix(plant, myFuelPrices,
                            expectedCO2Price.get(market));
                    plant.setFuelMix(fuelMix);

                    double expectedMarginalCost = determineExpectedMarginalCost(plant, expectedFuelPrices,
                            expectedCO2Price.get(market));
                    double runningHours = 0d;
                    double expectedGrossProfit = 0d;

                    // logger.warn("Agent {}  found that the installed capacity in the market {} in future to be "
                    // + marketInformation.capacitySum +
                    // "and expectde maximum demand to be " +
                    // marketInformation.maxExpectedLoad, agent, market);
                    long numberOfSegments = reps.segmentRepository.count();

                    // TODO somehow the prices of long-term contracts could also
                    // be used here to determine the expected profit. Maybe not
                    // though...
                    for (SegmentLoad segmentLoad : market.getLoadDurationCurve()) {
                        double expectedElectricityPrice = marketInformation.expectedElectricityPricesPerSegment
                                .get(segmentLoad.getSegment());
                        double hours = segmentLoad.getSegment().getLengthInHours();
                        if (expectedMarginalCost <= expectedElectricityPrice) {
                            runningHours += hours;
                            expectedGrossProfit += (expectedElectricityPrice - expectedMarginalCost)
                                    * hours
                                    * plant.getAvailableCapacity(futureTimePoint, segmentLoad.getSegment(),
                                            numberOfSegments);
                        }
                    }

                    // logger.warn(agent +
                    // "expects technology {} to have {} running", technology,
                    // runningHours);
                    // expect to meet minimum running hours?
                    if (runningHours < plant.getTechnology().getMinimumRunningHours()) {
                        // logger.warn(agent
                        // +
                        // " will not invest in {} technology as he expect to have {} running, which is lower then required",
                        // technology, runningHours);
                    } else {

                        // logger.warn(technology +
                        // " the project is considered profitable ");

                        double fixedOMCost = calculateFixedOperatingCost(plant);// /
                                                                                // plant.getActualNominalCapacity();

                        double operatingProfit = expectedGrossProfit - fixedOMCost;

                        // TODO Alter discount rate on the basis of the amount
                        // in long-term contracts?
                        // TODO Alter discount rate on the basis of other stuff,
                        // such as amount of money, market share, portfolio
                        // size.

                        // Calculation of weighted average cost of capital,
                        // based on the companies debt-ratio
                        double wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                + agent.getDebtRatioOfInvestments() * agent.getLoanInterestRate();

                        // Creation of out cash-flow during power plant building
                        // phase (note that the cash-flow is negative!)
                        TreeMap<Integer, Double> discountedProjectCapitalOutflow = calculateSimplePowerPlantInvestmentCashFlow(
                                technology.getDepreciationTime(), (int) plant.getActualLeadtime(),
                                plant.getActualInvestedCapital(), 0);
                        // Creation of in cashflow during operation
                        TreeMap<Integer, Double> discountedProjectCashInflow = calculateSimplePowerPlantInvestmentCashFlow(
                                technology.getDepreciationTime(), (int) plant.getActualLeadtime(), 0, operatingProfit);

                        double discountedCapitalCosts = npv(discountedProjectCapitalOutflow, wacc);// are
                                                                                                   // defined
                                                                                                   // negative!!
                        // plant.getActualNominalCapacity();

                        // logger.warn("Agent {}  found that the discounted capital for technology {} to be "
                        // + discountedCapitalCosts, agent,
                        // technology);

                        double discountedOpProfit = npv(discountedProjectCashInflow, wacc);

                        // logger.warn("Agent {}  found the expected prices to be {}",
                        // agent,
                        // marketInformation.expectedElectricityPricesPerSegment);
                        // logger.warn("Agent {}  found that the projected discounted inflows for technology {} to be "
                        // + discountedOpProfit,
                        // agent, technology);

                        technology.setProjectValue(discountedOpProfit + discountedCapitalCosts);

                        // logger.warn(technology + " has a project value of " +
                        // technology.getProjectValue());

                        // logger.warn(
                        // "Agent {}  found the project value for technology {} to be "
                        // + Math.round(projectValue /
                        // plant.getActualNominalCapacity()) +
                        // " EUR/kW (running hours: "
                        // + runningHours + "", agent, technology);

                        // double projectTotalValue = projectValuePerMW *
                        // plant.getActualNominalCapacity();

                        // double projectReturnOnInvestment = discountedOpProfit
                        // / (-discountedCapitalCosts);

                        //
                        // calculate cumulative MCDA criteria values for
                        // calculation of the
                        // propensity
                        //

                        if (technology.getProjectValue() > 0) {

                            // logger.warn(agent + " is the investor ");
                            // logger.warn(technology +
                            // " is the profitable technology is " +
                            // technology.getName());
                            // logger.warn(technology +
                            // " has a NPV value of divided bt nominal capacity "
                            // + (technology.getProjectValue() /
                            // plant.getActualNominalCapacity()));
                            // logger.warn(market +
                            // " is the market where the investment option is ");

                            npvTotal += technology.getProjectValue() / plant.getActualNominalCapacity();
                            efficiencyTotal += plant.getActualEfficiency();
                            lifetimeTotal += plant.getActualLifetime();
                            investmentCostTotal += plant.getActualInvestedCapital();
                            minimalRunningHoursTotal += technology.getMinimumRunningHours();
                            footprintTotal += plant.calculateEmissionIntensity();

                        }

                        // logger.warn(technology + " the efficiency total is "
                        // + efficiencyTotal);
                        // logger.warn(technology +
                        // " The total lifetime value is " + lifetimeTotal);
                        // logger.warn(technology +
                        // " the total investment cost is " +
                        // investmentCostTotal);
                        // logger.warn(technology +
                        // " The total value of minimum running hours is " +
                        // minimalRunningHoursTotal);
                        // logger.warn(technology + " the footprint total is " +
                        // footprintTotal);

                        if (agent.isInvestorIncludeSubjectiveFactor() == false) {

                            logger.warn(agent + " has no subjective criteria included, which is not correct ");

                            /*
                             * Divide by capacity, in order not to favour large
                             * power plants (which have the single largest NPV
                             */

                            if (technology.getProjectValue() > 0
                                    && technology.getProjectValue() / plant.getActualNominalCapacity() > highestValue) {
                                highestValue = technology.getProjectValue() / plant.getActualNominalCapacity();
                                bestTechnology = plant.getTechnology();
                            }

                        } else {

                        }

                    }

                }
            }

            // Now the NPV's are estimated and the propensities are calculated
            // the propensities are normalised and the probabilities are
            // calculated.

            // ----------MCDA-------------

            // calculate propensity or utility for all with a
            // NPV > 0 and
            // determine lowest and highest propensity for
            // normalisation

            if (agent.isInvestorIncludeSubjectiveFactor() == true) {

                // double totalPropensity = 0d;

                for (PowerGeneratingTechnology technology : reps.genericRepository
                        .findAll(PowerGeneratingTechnology.class)) {

                    PowerPlant plant = new PowerPlant();
                    // plant.specifyNotPersist(getCurrentTick(), agent,
                    // getNodeForZone(market.getZone()), technology);

                    if (technology.getProjectValue() <= 0) {

                    } else {

                        logger.warn(technology + " the total npv value is " + npvTotal);
                        logger.warn(technology + " the total footprint is " + npvTotal);
                        logger.warn(technology + " the total efficiency " + npvTotal);

                        technology.setTechnologyPropensity(agent.getWeightfactorProfit() * technology.getProjectValue()
                                / plant.getActualNominalCapacity() / npvTotal - agent.getWeightfactorEmission()
                                * plant.calculateEmissionIntensity() / footprintTotal
                                + agent.getWeightfactorEfficiency() * plant.getActualEfficiency() / efficiencyTotal
                                - agent.getWeigthfactorInvestmentCost() * plant.getActualInvestedCapital()
                                / investmentCostTotal - agent.getWeightfactorMinimalRunningHours()
                                * technology.getMinimumRunningHours() / minimalRunningHoursTotal
                                + agent.getWeightfactorLifeTime() * plant.getActualLifetime() / lifetimeTotal);

                        logger.warn(technology + " the propensity is " + technology.getTechnologyPropensity());

                        if (technology.getTechnologyPropensity() < highestpropensity
                                && technology.getTechnologyPropensity() > lowestpropensity) {

                        } else if (technology.getTechnologyPropensity() < lowestpropensity) {
                            lowestpropensity = technology.getTechnologyPropensity();

                        } else {

                            highestpropensity = technology.getTechnologyPropensity();
                        }

                    }

                    // totalPropensity += technology.getTechnologyPropensity();
                }

                // logger.warn(agent + " the total propensity is " +
                // totalPropensity);
                // logger.warn(agent + " the lowest propensity is " +
                // lowestpropensity);
                // logger.warn(agent + " the highest propensity is " +
                // highestpropensity);

                double totalNormalisedPropensity = 0;

                // normalisation parameter in case of negative propensities or
                // equal
                // to zero, this makes the MCDA more robust

                if (highestpropensity < 0) {
                    highestpropensity = highestpropensity / agent.getNormalisationParameter();
                } else if (highestpropensity == 0) {
                    highestpropensity = highestpropensity + 1 * agent.getNormalisationParameter();
                } else {
                    highestpropensity = highestpropensity * agent.getNormalisationParameter();
                }

                if (lowestpropensity < 0) {
                    highestpropensity = lowestpropensity * agent.getNormalisationParameter();
                } else if (lowestpropensity == 0) {
                    lowestpropensity = lowestpropensity - 1 * agent.getNormalisationParameter();
                } else {
                    lowestpropensity = lowestpropensity / agent.getNormalisationParameter();
                }

                // logger.warn(agent +
                // " the lowest propensity after normalization is " +
                // lowestpropensity);
                // logger.warn(agent +
                // " the lowest propensity after normalization is " +
                // highestpropensity);

                int numberProfitableProjects = 0;

                for (PowerGeneratingTechnology technology : reps.genericRepository
                        .findAll(PowerGeneratingTechnology.class)) {

                    // PowerPlant plant = new PowerPlant();

                    if (technology.getProjectValue() <= 0) {

                    } else {

                        numberProfitableProjects += 1;

                        technology
                                .setTechnologyNormalisedPropensity((technology.getTechnologyPropensity() - lowestpropensity)
                                        * 1 / (highestpropensity - lowestpropensity));

                    }

                    totalNormalisedPropensity += technology.getTechnologyNormalisedPropensity();
                }

                // logger.warn(agent + " the total normalised propensity is " +
                // totalNormalisedPropensity);

                // normalise propensities

                // calculate probability and add information to lists for
                // discrete
                // distribution

                // double totalProbability = 0;

                // List<String> technologyNamesList = new ArrayList<String>();
                // ArrayList<Double> technologyProbabilitiesList = new
                // ArrayList<Double>();

                PowerGeneratingTechnology[] technologyNamesArray = new PowerGeneratingTechnology[numberProfitableProjects];
                double[] technologyProbabilitiesArray = new double[numberProfitableProjects];

                int i = 0;

                for (PowerGeneratingTechnology technology : reps.genericRepository
                        .findAll(PowerGeneratingTechnology.class)) {

                    if (technology.getProjectValue() <= 0) {

                    } else {

                        technology.setTechnologyProbability(technology.getTechnologyNormalisedPropensity()
                                / totalNormalisedPropensity);

                        // totalPropensity;
                        // technologyNamesList.add(technology.getName());

                        technologyNamesArray[i] = technology;
                        technologyProbabilitiesArray[i] = technology.getTechnologyProbability();
                        i++;
                    }

                    // verification cumulative probability = 1
                    // totalProbability +=
                    // technology.getTechnologyProbability();

                    // logger.warn(" Technology" + technology.getName() +
                    // " has the probabilities of "
                    // + technology.getTechnologyProbability());

                }

                // --- option one just select the investment with the highest
                // probability ---

                // for (PowerGeneratingTechnology technology :
                // reps.genericRepository.findAll(PowerGeneratingTechnology.class))
                // {
                // PowerPlant plant = new PowerPlant();
                // if (technologyProbability > 0 && technologyProbability >
                // highestValue) {
                // highestValue = technologyProbability;
                // // bestTechnology = plant.getTechnology();
                // }
                // }

                // --- option two setting probability mass function and select
                // the investment option on the basis of a random number.

                if (agent.isInvestorIncludeSubjectiveFactor() == false) {

                    int k;

                    EmpiricalWalker em = new EmpiricalWalker(technologyProbabilitiesArray, Empirical.NO_INTERPOLATION,
                            EmpiricalWalker.makeDefaultGenerator());
                    k = em.nextInt();
                }

            } else {

                bestTechnology = null;

            }

            // Another option to include a discrete distribution
            // DiscreteProbability(List<String> technologyNames, List<Double>
            // technologyProbabilities);
            // sample(DiscreteProbability(technologyNames,
            // technologyProbabilities));

            if (bestTechnology != null) {
                // logger.warn("Agent {} invested in technology {} at tick " +
                // getCurrentTick(), agent, bestTechnology);

                PowerPlant plant = new PowerPlant();
                plant.specifyAndPersist(getCurrentTick(), agent, getNodeForZone(market.getZone()), bestTechnology);
                PowerPlantManufacturer manufacturer = reps.genericRepository.findFirst(PowerPlantManufacturer.class);
                BigBank bigbank = reps.genericRepository.findFirst(BigBank.class);

                double investmentCostPayedByEquity = plant.getActualInvestedCapital()
                        * (1 - agent.getDebtRatioOfInvestments());
                double investmentCostPayedByDebt = plant.getActualInvestedCapital() * agent.getDebtRatioOfInvestments();
                double downPayment = investmentCostPayedByEquity;
                createSpreadOutDownPayments(agent, manufacturer, downPayment, plant);

                double amount = determineLoanAnnuities(investmentCostPayedByDebt, plant.getTechnology()
                        .getDepreciationTime(), agent.getLoanInterestRate());
                // logger.warn("Loan amount is: " + amount);
                Loan loan = reps.loanRepository.createLoan(agent, bigbank, amount, plant.getTechnology()
                        .getDepreciationTime(), getCurrentTick(), plant);
                // Create the loan
                plant.createOrUpdateLoan(loan);

            } else {
                // logger.warn("{} found no suitable technology anymore to invest in at tick "
                // + getCurrentTick(), agent);
                // agent will not participate in the next round of investment if
                // he does not invest now
                setNotWillingToInvest(agent);
            }
        }
    }

    // Creates n downpayments of equal size in each of the n building years of a
    // power plant
    @Transactional
    private void createSpreadOutDownPayments(EnergyProducer agent, PowerPlantManufacturer manufacturer,
            double totalDownPayment, PowerPlant plant) {
        int buildingTime = (int) plant.getActualLeadtime();
        for (int i = 0; i < buildingTime; i++) {
            reps.nonTransactionalCreateRepository.createCashFlow(agent, manufacturer, totalDownPayment / buildingTime,
                    CashFlow.DOWNPAYMENT, getCurrentTick() + i, plant);
        }
    }

    @Transactional
    private void setNotWillingToInvest(EnergyProducer agent) {
        agent.setWillingToInvest(false);
    }

    /**
     * Predicts fuel prices for {@link futureTimePoint} using a geometric trend
     * regression forecast. Only predicts fuels that are traded on a commodity
     * market.
     * 
     * @param agent
     * @param futureTimePoint
     * @return Map<Substance, Double> of predicted prices.
     */
    public Map<Substance, Double> predictFuelPrices(EnergyProducer agent, long futureTimePoint) {
        // Fuel Prices
        Map<Substance, Double> expectedFuelPrices = new HashMap<Substance, Double>();
        for (Substance substance : reps.substanceRepository.findAllSubstancesTradedOnCommodityMarkets()) {
            // Find Clearing Points for the last 5 years (counting current year
            // as one of the last 5 years).
            Iterable<ClearingPoint> cps = reps.clearingPointRepository
                    .findAllClearingPointsForSubstanceTradedOnCommodityMarkesAndTimeRange(substance, getCurrentTick()
                            - (agent.getNumberOfYearsBacklookingForForecasting() - 1), getCurrentTick());
            // logger.warn("{}, {}",
            // getCurrentTick()-(agent.getNumberOfYearsBacklookingForForecasting()-1),
            // getCurrentTick());
            // Create regression object
            GeometricTrendRegression gtr = new GeometricTrendRegression();
            for (ClearingPoint clearingPoint : cps) {
                // logger.warn("CP {}: {} , in" + clearingPoint.getTime(),
                // substance.getName(), clearingPoint.getPrice());
                gtr.addData(clearingPoint.getTime(), clearingPoint.getPrice());
            }
            expectedFuelPrices.put(substance, gtr.predict(futureTimePoint));
            // logger.warn("Forecast {}: {}, in Step " + futureTimePoint,
            // substance, expectedFuelPrices.get(substance));
        }
        return expectedFuelPrices;
    }

    // Create a powerplant investment and operation cash-flow in the form of a
    // map. If only investment, or operation costs should be considered set
    // totalInvestment or operatingProfit to 0
    private TreeMap<Integer, Double> calculateSimplePowerPlantInvestmentCashFlow(int depriacationTime,
            int buildingTime, double totalInvestment, double operatingProfit) {
        TreeMap<Integer, Double> investmentCashFlow = new TreeMap<Integer, Double>();
        double equalTotalDownPaymentInstallement = totalInvestment / buildingTime;
        for (int i = 0; i < buildingTime; i++) {
            investmentCashFlow.put(new Integer(i), -equalTotalDownPaymentInstallement);
        }
        for (int i = buildingTime; i < depriacationTime + buildingTime; i++) {
            investmentCashFlow.put(new Integer(i), operatingProfit);
        }

        return investmentCashFlow;
    }

    private double npv(TreeMap<Integer, Double> netCashFlow, double wacc) {
        double npv = 0;
        for (Integer iterator : netCashFlow.keySet()) {
            npv += netCashFlow.get(iterator).doubleValue() / Math.pow(1 + wacc, iterator.intValue());
        }
        return npv;
    }

    public double determineExpectedMarginalCost(PowerPlant plant, Map<Substance, Double> expectedFuelPrices,
            double expectedCO2Price) {
        double mc = determineExpectedMarginalFuelCost(plant, expectedFuelPrices);
        double co2Intensity = plant.calculateEmissionIntensity();
        mc += co2Intensity * expectedCO2Price;
        return mc;
    }

    public double determineExpectedMarginalFuelCost(PowerPlant powerPlant, Map<Substance, Double> expectedFuelPrices) {
        double fc = 0d;
        for (SubstanceShareInFuelMix mix : powerPlant.getFuelMix()) {
            double amount = mix.getShare();
            double fuelPrice = expectedFuelPrices.get(mix.getSubstance());
            fc += amount * fuelPrice;
        }
        return fc;
    }

    private PowerGridNode getNodeForZone(Zone zone) {
        for (PowerGridNode node : reps.genericRepository.findAll(PowerGridNode.class)) {
            if (node.getZone().equals(zone)) {
                return node;
            }
        }
        return null;
    }

    private class MarketInformation {

        Map<Segment, Double> expectedElectricityPricesPerSegment;
        double maxExpectedLoad = 0d;
        Map<PowerPlant, Double> meritOrder;
        double capacitySum;

        MarketInformation(ElectricitySpotMarket market, Map<ElectricitySpotMarket, Double> expectedDemand,
                Map<Substance, Double> fuelPrices, double co2price, long time) {
            // determine expected power prices
            expectedElectricityPricesPerSegment = new HashMap<Segment, Double>();
            Map<PowerPlant, Double> marginalCostMap = new HashMap<PowerPlant, Double>();
            capacitySum = 0d;

            // get merit order for this market
            for (PowerPlant plant : reps.powerPlantRepository.findExpectedOperationalPowerPlantsInMarket(market, time)) {

                double plantMarginalCost = determineExpectedMarginalCost(plant, fuelPrices, co2price);
                marginalCostMap.put(plant, plantMarginalCost);
                capacitySum += plant.getActualNominalCapacity();
            }

            // get difference between technology target and expected operational
            // capacity
            for (PowerGeneratingTechnologyTarget pggt : reps.powerGenerationTechnologyTargetRepository
                    .findAllByMarket(market)) {
                double expectedTechnologyCapacity = reps.powerPlantRepository
                        .calculateCapacityOfExpectedOperationalPowerPlantsInMarketAndTechnology(market,
                                pggt.getPowerGeneratingTechnology(), time);
                double targetDifference = pggt.getTrend().getValue(time) - expectedTechnologyCapacity;
                if (targetDifference > 0) {
                    PowerPlant plant = new PowerPlant();
                    plant.specifyNotPersist(getCurrentTick(), new EnergyProducer(),
                            reps.powerGridNodeRepository.findFirstPowerGridNodeByElectricitySpotMarket(market),
                            pggt.getPowerGeneratingTechnology());
                    plant.setActualNominalCapacity(targetDifference);
                    double plantMarginalCost = determineExpectedMarginalCost(plant, fuelPrices, co2price);
                    marginalCostMap.put(plant, plantMarginalCost);
                    capacitySum += targetDifference;
                }
            }

            MapValueComparator comp = new MapValueComparator(marginalCostMap);
            meritOrder = new TreeMap<PowerPlant, Double>(comp);
            meritOrder.putAll(marginalCostMap);

            long numberOfSegments = reps.segmentRepository.count();

            double demandFactor = expectedDemand.get(market).doubleValue();

            // find expected prices per segment given merit order
            for (SegmentLoad segmentLoad : market.getLoadDurationCurve()) {

                double expectedSegmentLoad = segmentLoad.getBaseLoad() * demandFactor;

                if (expectedSegmentLoad > maxExpectedLoad) {
                    maxExpectedLoad = expectedSegmentLoad;
                }

                double segmentSupply = 0d;
                double segmentPrice = 0d;

                for (Entry<PowerPlant, Double> plantCost : meritOrder.entrySet()) {
                    PowerPlant plant = plantCost.getKey();
                    double plantCapacity = 0d;
                    // Determine available capacity in the future in this
                    // segment
                    plantCapacity = plant
                            .getExpectedAvailableCapacity(time, segmentLoad.getSegment(), numberOfSegments);

                    // logger.warn("Capacity of plant " + plant.toString() +
                    // " is " +
                    // plantCapacity/plant.getActualNominalCapacity());
                    if (segmentSupply < expectedSegmentLoad) {
                        segmentSupply += plantCapacity;
                        segmentPrice = plantCost.getValue();
                    }

                }

                // logger.warn("Segment " +
                // segmentLoad.getSegment().getSegmentID() + " supply equals " +
                // segmentSupply + " and segment demand equals " +
                // expectedSegmentLoad);

                if (segmentSupply >= expectedSegmentLoad) {
                    expectedElectricityPricesPerSegment.put(segmentLoad.getSegment(), segmentPrice);
                } else {
                    expectedElectricityPricesPerSegment.put(segmentLoad.getSegment(), market.getValueOfLostLoad());
                }

            }
        }
    }

}