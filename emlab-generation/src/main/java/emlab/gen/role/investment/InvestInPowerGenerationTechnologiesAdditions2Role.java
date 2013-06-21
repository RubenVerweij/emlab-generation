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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import emlab.gen.domain.agent.BigBank;
import emlab.gen.domain.agent.EnergyProducer;
import emlab.gen.domain.agent.EnergyProducerAdditions;
import emlab.gen.domain.agent.PowerPlantManufacturer;
import emlab.gen.domain.agent.StrategicReserveOperator;
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
import emlab.gen.repository.StrategicReserveOperatorRepository;
import emlab.gen.util.GeometricTrendRegression;
import emlab.gen.util.MapValueComparator;

/**
 * {@link EnergyProducer}s decide to invest in new {@link PowerPlant}
 * 
 * @author R. Verweij This algorithm optionally includes technology preferences,
 *         credit-risk considerations and risk-averse behaviour
 */
@Configurable
@NodeEntity
public class InvestInPowerGenerationTechnologiesAdditions2Role<T extends EnergyProducerAdditions> extends
        GenericInvestmentRole<T> implements Role<T>, NodeBacked {

    @Transient
    @Autowired
    Reps reps;

    @Transient
    @Autowired
    Neo4jTemplate template;

    @Transient
    @Autowired
    StrategicReserveOperatorRepository strategicReserveOperatorRepository;

    // market expectations
    @Transient
    Map<ElectricitySpotMarket, MarketInformation> marketInfoMap = new HashMap<ElectricitySpotMarket, MarketInformation>();

    @Override
    @Transactional
    public void act(T agent) {

        long futureTimePoint = getCurrentTick() + agent.getInvestmentFutureTimeHorizon();
        // logger.warn(agent + " is looking at timepoint " + futureTimePoint);

        // ==== Expectations ===

        Map<Substance, Double> expectedFuelPrices = predictFuelPrices(agent, futureTimePoint);

        // CO2
        Map<ElectricitySpotMarket, Double> expectedCO2Price = determineExpectedCO2PriceInclTax(futureTimePoint,
                agent.getNumberOfYearsBacklookingForForecasting());

        // logger.warn(expectedCO2Price.toString());

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

        double debtTotal = 0;
        double assetPlantTotal = 0;
        double assetTotal = 0d;
        double loanInterestRiskRate = 0;

        if (agent.getInvestorIncludeCreditRisk().equals("true")) {

            for (PowerPlant plant : reps.powerPlantRepository.findPowerPlantsByOwner(agent)) {

                if (plant.getLoan().getNumberOfPaymentsDone() < plant.getLoan().getTotalNumberOfPayments()) {

                    long paymentsLeft = plant.getLoan().getTotalNumberOfPayments()
                            - plant.getLoan().getNumberOfPaymentsDone();
                    double amountPayment = plant.getLoan().getAmountPerPayment();
                    debtTotal += (paymentsLeft * amountPayment);

                } else {

                }

                if (plant.getLoan().getNumberOfPaymentsDone() < plant.getTechnology().getDepreciationTime()) {

                    double plantInvestedCapital = plant.getActualInvestedCapital();
                    double depreciationTermAmount = plantInvestedCapital / plant.getTechnology().getDepreciationTime();

                    assetPlantTotal += plantInvestedCapital - depreciationTermAmount;

                } else {

                }

                if (debtTotal == 0) {

                    debtTotal = 1;

                } else {

                }

                // logger.warn(agent + " debt value is " + debtTotal);
                // logger.warn(agent + " the value of the plants is " +
                // assetPlantTotal);

            }

            if (agent.getDebtBias() == 0) {

            } else {

                debtTotal = debtTotal + agent.getDebtBias();
            }

            // Calculation of weighted average cost of capital,
            // based on the companies debt-ratio

            // Equity value according to call option solution of
            // Black-Scholes here debt-rate of the investor is
            // determined based upon the financial structure of the
            // investor. Low asset value with respect to debt means
            // a higher debt rate offer

            assetTotal = assetPlantTotal + agent.getCash();

            // logger.warn(agent + " has a debt value of " + debtTotal +
            // " and a plant value of " + assetPlantTotal
            // + " and an (plant + cash) value of " + assetTotal +
            // " at timepoint "
            // + futureTimePoint);

            double d1 = (Math.log(assetTotal / debtTotal) + (agent.getLoanInterestFreeRate() + Math.pow(
                    agent.getAssetValueDeviation(), 2) / 2)
                    * agent.getTimeToMaturity())
                    / (agent.getAssetValueDeviation() * Math.sqrt(agent.getTimeToMaturity()));

            // logger.warn(agent + " has a d1 of " + d1 + futureTimePoint);

            double d2 = d1 - (agent.getAssetValueDeviation() * Math.sqrt(agent.getTimeToMaturity()));

            // Outcome to standard normal variable n1(d1) and
            // n2(d2) using Taylor approximation

            double n1 = cumulativeNormalDistributionFunction(d1);
            double n2 = cumulativeNormalDistributionFunction(d2);

            // logger.warn(agent + " has a n2 of " + n2 + futureTimePoint);
            // logger.warn(agent + " has a n1 of " + n1 + futureTimePoint);

            double equityValueBS = (assetTotal * n1)
                    - (debtTotal * Math.exp(-agent.getLoanInterestFreeRate() * agent.getTimeToMaturity())) * n2;

            double pricedDebtTotal = assetTotal - equityValueBS;

            // Calculation of credit-risk interest rate
            // agent.setLoanInterestRiskRate((-1 / agent.getTimeToMaturity() *
            // Math.log(pricedDebtTotal / debtTotal)));

            loanInterestRiskRate = -1 / agent.getTimeToMaturity() * Math.log(pricedDebtTotal / debtTotal);

        } else {

        }

        // Investment decision
        // for (ElectricitySpotMarket market :
        // reps.genericRepository.findAllAtRandom(ElectricitySpotMarket.class))
        // {
        ElectricitySpotMarket market = agent.getInvestorMarket();
        MarketInformation marketInformation = new MarketInformation(market, expectedDemand, expectedFuelPrices,
                expectedCO2Price.get(market).doubleValue(), futureTimePoint);
        /*
         * if (marketInfoMap.containsKey(market) &&
         * marketInfoMap.get(market).time == futureTimePoint) {
         * marketInformation = marketInfoMap.get(market); } else {
         * marketInformation = new MarketInformation(market, expectedFuelPrices,
         * expectedCO2Price, futureTimePoint); marketInfoMap.put(market,
         * marketInformation); }
         */

        // logger.warn(agent + " is expecting a CO2 price of " +
        // expectedCO2Price.get(market) + " Euro/MWh at timepoint "
        // + futureTimePoint + " in Market " + market);

        // logger.warn("Agent {}  found the expected prices to be {}", agent,
        // marketInformation.expectedElectricityPricesPerSegment);

        // logger.warn("Agent {}  found that the installed capacity in the market {} in future to be "
        // + marketInformation.capacitySum +
        // "and expectde maximum demand to be "
        // + marketInformation.maxExpectedLoad, agent, market);

        double highestValue = Double.MIN_VALUE;
        PowerGeneratingTechnology bestTechnology = null;
        double projectValue = 0d;

        // Variables for MCDA
        // Cumulative variable criteria for MCDA
        double npvTotal = 0d;
        double footprintTotal = 0d;
        double efficiencyTotal = 0d;
        double lifetimeTotal = 0d;
        double investmentCostTotal = 0d;
        double minimalRunningHoursTotal = 0d;

        List<PowerGeneratingTechnology> technologyNameArray = new ArrayList<PowerGeneratingTechnology>();
        List<Double> npvArray = new ArrayList<Double>();
        List<Double> footprintArray = new ArrayList<Double>();
        List<Double> efficiencyArray = new ArrayList<Double>();
        List<Integer> lifetimeArray = new ArrayList<Integer>();
        List<Double> investmentCostArray = new ArrayList<Double>();
        List<Double> minimumRunningHoursArray = new ArrayList<Double>();

        // MCDA variable
        List<Double> technologyPropensityArray = new ArrayList<Double>();
        List<Double> technologyNormalisedPropensityArray = new ArrayList<Double>();
        List<Double> technologyProbabilityArray = new ArrayList<Double>();

        // Portfolio diversification
        List<Double> technologyCapacity = new ArrayList<Double>();
        List<Double> technologyCapacityTotal = new ArrayList<Double>();
        List<PowerGeneratingTechnology> technologyNames = new ArrayList<PowerGeneratingTechnology>();

        List<Double> technologyMarketShare = new ArrayList<Double>();
        List<Double> technologyNormalisedMarketShare = new ArrayList<Double>();

        // Checks
        String checkInvestorIsNotRiskAverse = null;
        String checkInvestorIsNotTechnologyPreferences = null;
        String checkInvestorIsNotCreditRisk = null;

        double totalCapacity = 0d;

        totalCapacity = reps.powerPlantRepository.calculateCapacityOfOperationalPowerPlantsByOwner(agent,
                getCurrentTick());

        // ensure that 0 is returned
        // if (totalCapacity <= 0) {
        // totalCapacity = 0d;
        // }

        String riskdiversificationProfile = null;

        if (totalCapacity >= agent.getMarketGiantCapacity()) {
            riskdiversificationProfile = "true";
        } else {
            riskdiversificationProfile = "false";
        }

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
                    .calculateCapacityOfExpectedOperationalPowerPlantsInMarketByOwner(market, futureTimePoint, agent);
            double expectedOwnedCapacityInMarketOfThisTechnology = reps.powerPlantRepository
                    .calculateCapacityOfExpectedOperationalPowerPlantsInMarketByOwnerAndTechnology(market, technology,
                            futureTimePoint, agent);
            double capacityOfTechnologyInPipeline = reps.powerPlantRepository
                    .calculateCapacityOfPowerPlantsByTechnologyInPipeline(technology, getCurrentTick());
            double operationalCapacityOfTechnology = reps.powerPlantRepository
                    .calculateCapacityOfOperationalPowerPlantsByTechnology(technology, getCurrentTick());
            double capacityInPipelineInMarket = reps.powerPlantRepository
                    .calculateCapacityOfPowerPlantsByMarketInPipeline(market, getCurrentTick());

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
            } else if (capacityInPipelineInMarket > 0.2 * marketInformation.maxExpectedLoad) {
                // logger.warn("Not investing because more than 20% of demand in pipeline.");

            } else if ((capacityOfTechnologyInPipeline > 2.0 * operationalCapacityOfTechnology)
                    && capacityOfTechnologyInPipeline > 9000) { // TODO:
                // reflects that you cannot expand a technology out of zero.
                // logger.warn(agent +
                // " will not invest in {} technology because there's too much capacity in the pipeline",
                // technology);
            } else if (agent.getInvestorIncludeCreditRisk().equals("false")
                    && plant.getActualInvestedCapital() * (1 - agent.getDebtRatioOfInvestments()) > agent
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
                    // logger.warn(agent+
                    // " will not invest in {} technology as he expect to have {} running, which is lower then required",
                    // technology, runningHours);
                } else {

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
                    double wacc = 0d;

                    if (agent.getInvestorIncludeCreditRisk().equals("true")) {

                        if (agent.getSpecificRiskAverse().equals("true")) {

                            if (technology.getName().equals("Nuclear")) {

                                wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                        + agent.getRiskPremiumNuclear() + agent.getDebtRatioOfInvestments()
                                        * loanInterestRiskRate;

                            } else if (technology.getName().equals("CoalPSC") || technology.getName().equals("IGCC")
                                    || technology.getName().equals("Lignite")
                                    || technology.getName().equals("CoalPscCSS")
                                    || technology.getName().equals("IgccCCS")) {

                                wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                        + agent.getRiskPremiumCoal() + agent.getDebtRatioOfInvestments()
                                        * loanInterestRiskRate;

                            } else if (technology.getName().equals("GasConventional")) {
                                wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                        + agent.getRiskPremiumGas() + agent.getDebtRatioOfInvestments()
                                        * loanInterestRiskRate;

                            } else {
                                wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                        + agent.getRiskPremiumRenewable() + agent.getDebtRatioOfInvestments()
                                        * loanInterestRiskRate;
                            }

                        } else {

                            checkInvestorIsNotRiskAverse = "true";

                            wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                    + agent.getDebtRatioOfInvestments() * loanInterestRiskRate;
                        }

                        // logger.warn(agent +
                        // " includes credit-risks and gets a debt-rate offer of "
                        // + loanInterestRiskRate + " % at timepoint " +
                        // futureTimePoint);

                    } else {

                        checkInvestorIsNotCreditRisk = "true";

                        if (agent.getSpecificRiskAverse().equals("true")) {

                            if (technology.getName().equals("Nuclear")) {

                                wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                        + agent.getRiskPremiumNuclear() + agent.getDebtRatioOfInvestments()
                                        * agent.getLoanInterestRate();

                            } else if (technology.getName().equals("CoalPSC") || technology.getName().equals("IGCC")
                                    || technology.getName().equals("Lignite")
                                    || technology.getName().equals("CoalPscCSS")
                                    || technology.getName().equals("IgccCCS")) {

                                wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                        + agent.getRiskPremiumCoal() + agent.getDebtRatioOfInvestments()
                                        * agent.getLoanInterestRate();

                            } else if (technology.getName().equals("GasConventional")) {
                                wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                        + agent.getRiskPremiumGas() + agent.getDebtRatioOfInvestments()
                                        * agent.getLoanInterestRate();

                            } else {
                                wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                        + agent.getRiskPremiumRenewable() + agent.getDebtRatioOfInvestments()
                                        * agent.getLoanInterestRate();
                            }

                        } else {

                            checkInvestorIsNotRiskAverse = "true";

                            wacc = (1 - agent.getDebtRatioOfInvestments()) * agent.getEquityInterestRate()
                                    + agent.getDebtRatioOfInvestments() * agent.getLoanInterestRate();
                        }

                        // logger.warn(" does not include credit risk ");
                    }

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

                    // logger.warn("Agent {}  found that the projected discounted inflows for technology {} to be "
                    // + discountedOpProfit,
                    // agent, technology);

                    projectValue = discountedOpProfit + discountedCapitalCosts;

                    // logger.warn(
                    // "Agent {}  found the project value for technology {} to be "
                    // + Math.round(projectValue /
                    // plant.getActualNominalCapacity()) +
                    // " EUR/kW (running hours: "
                    // + runningHours + "", agent, technology);

                    if (projectValue > 0) {

                        // MCDA information

                        npvTotal += projectValue / plant.getActualNominalCapacity();
                        efficiencyTotal += plant.getActualEfficiency();
                        lifetimeTotal += plant.getActualLifetime();
                        investmentCostTotal += plant.getActualInvestedCapital();
                        minimalRunningHoursTotal += technology.getMinimumRunningHours();
                        footprintTotal += plant.calculateEmissionIntensity();

                        technologyNameArray.add(technology);
                        npvArray.add((projectValue / plant.getActualNominalCapacity()));
                        footprintArray.add(plant.calculateEmissionIntensity());
                        efficiencyArray.add(plant.getActualEfficiency());
                        lifetimeArray.add(technology.getExpectedLifetime());
                        investmentCostArray.add(plant.getActualInvestedCapital());
                        minimumRunningHoursArray.add(technology.getMinimumRunningHours());
                        technologyPropensityArray.add(0.00);
                        technologyNormalisedPropensityArray.add(0.00);
                        technologyProbabilityArray.add(0.00);

                        technologyNames.add(technology);
                        technologyCapacity.add(calculateTechnologyMarketShare(agent, technology, getCurrentTick()));
                        technologyCapacityTotal.add(totalCapacity);
                        technologyMarketShare.add(0.00);
                        technologyNormalisedMarketShare.add(0.00);

                    }

                    // double projectTotalValue = projectValuePerMW *
                    // plant.getActualNominalCapacity();

                    // double projectReturnOnInvestment = discountedOpProfit
                    // / (-discountedCapitalCosts);

                    /*
                     * Divide by capacity, in order not to favour large power
                     * plants (which have the single largest NPV
                     */

                    if (agent.getInvestorIncludeSubjectiveFactor().equals("false")
                            && riskdiversificationProfile == "false") {

                        checkInvestorIsNotTechnologyPreferences = "true";

                        if (projectValue > 0 && projectValue / plant.getActualNominalCapacity() > highestValue) {
                            highestValue = projectValue / plant.getActualNominalCapacity();
                            bestTechnology = plant.getTechnology();
                        }

                    }
                }

            }
        }

        if (agent.getInvestorIncludeSubjectiveFactor().equals("true")) {

            // propensities
            double highestpropensity = Double.NEGATIVE_INFINITY;
            double lowestpropensity = Double.POSITIVE_INFINITY;

            if (technologyNameArray.size() >= 2) {

                for (int i = 0; i < technologyPropensityArray.size(); i++) {
                    technologyPropensityArray.set(i, (npvArray.get(i) * agent.getWeightfactorProfit() / npvTotal));

                    if (footprintTotal > 0) {

                        technologyPropensityArray.set(i, technologyPropensityArray.get(i)
                                - (footprintArray.get(i) * agent.getWeightfactorEmission() / footprintTotal));

                        if (efficiencyTotal > 0) {

                            technologyPropensityArray.set(i, technologyPropensityArray.get(i)
                                    + (efficiencyArray.get(i) * agent.getWeightfactorEfficiency() / efficiencyTotal));

                            if (investmentCostTotal > 0) {

                                technologyPropensityArray
                                        .set(i,
                                                technologyPropensityArray.get(i)
                                                        - (investmentCostArray.get(i)
                                                                * agent.getWeigthfactorInvestmentCost() / investmentCostTotal));

                                if (lifetimeTotal > 0) {

                                    technologyPropensityArray
                                            .set(i,
                                                    technologyPropensityArray.get(i)
                                                            - (lifetimeArray.get(i) * agent.getWeightfactorLifeTime() / lifetimeTotal));

                                } else {

                                }

                            } else {

                            }

                        } else {

                        }

                    } else {

                    }

                    if (highestpropensity < technologyPropensityArray.get(i)) {

                        highestpropensity = technologyPropensityArray.get(i);

                    } else {

                    }

                    if (lowestpropensity > technologyPropensityArray.get(i)) {

                        lowestpropensity = technologyPropensityArray.get(i);

                    } else {

                    }

                }

            } else {

            }

            if (highestpropensity < 0) {
                highestpropensity = highestpropensity / agent.getNormalisationParameter();
            } else if (highestpropensity == 0) {
                highestpropensity = highestpropensity + 1 * agent.getNormalisationParameter();
            } else {
                highestpropensity = highestpropensity * agent.getNormalisationParameter();
            }

            if (lowestpropensity < 0) {
                lowestpropensity = lowestpropensity * agent.getNormalisationParameter();
            } else if (lowestpropensity == 0) {
                lowestpropensity = lowestpropensity - 1 * agent.getNormalisationParameter();
            } else {
                lowestpropensity = lowestpropensity / agent.getNormalisationParameter();
            }

            double totalNormalisedPropensity = 0d;

            if (technologyNameArray.size() >= 2) {

                for (int i = 0; i < technologyPropensityArray.size(); i++) {

                    technologyNormalisedPropensityArray.set(i, (technologyPropensityArray.get(i) - lowestpropensity)
                            * 1 / (highestpropensity - lowestpropensity));

                    totalNormalisedPropensity += (technologyPropensityArray.get(i) - lowestpropensity) * 1
                            / (highestpropensity - lowestpropensity);

                }

            } else {

            }

            if (technologyNameArray.size() >= 2) {

                for (int i = 0; i < technologyProbabilityArray.size(); i++) {

                    technologyProbabilityArray.set(i, technologyNormalisedPropensityArray.get(i)
                            / totalNormalisedPropensity);

                }
            } else {

                for (int i = 0; i < technologyProbabilityArray.size(); i++) {
                    technologyProbabilityArray.set(i, 1.00);
                }

            }

            double bestValue = 0d;

            for (int i = 0; i < technologyProbabilityArray.size(); i++) {

                if (technologyProbabilityArray.get(i) > bestValue) {
                    bestValue = technologyProbabilityArray.get(i);
                    bestTechnology = technologyNameArray.get(i);
                }
            }

        } else {

            if (riskdiversificationProfile.equals("true")) {

                for (int i = 0; i < technologyNames.size(); i++) {

                    technologyMarketShare.set(i, technologyCapacity.get(i) / technologyCapacityTotal.get(i));
                }

                // logger.warn(" Market-share " + technologyMarketShare);

                double bestValue = Double.POSITIVE_INFINITY;

                for (int i = 0; i < technologyMarketShare.size(); i++) {
                    if (technologyMarketShare.get(i) < bestValue) {
                        bestValue = technologyMarketShare.get(i);
                        bestTechnology = technologyNames.get(i);
                    } else {

                    }
                }
            } else {

            }

            checkInvestorIsNotTechnologyPreferences = "true";

        }

        /*
         * if (bestTechnology != null &&
         * agent.getInvestorIncludeSubjectiveFactor().equals("true")) {
         * 
         * logger.warn(agent + " has the following probabilities; " +
         * technologyProbabilityArray + " for the following technologies " +
         * technologyNameArray + " the best technology is " + bestTechnology); }
         * 
         * 
         * if (bestTechnology != null &&
         * agent.getInvestorIncludeCreditRisk().equals("true")) {
         * 
         * logger.warn(agent + " invests in " + bestTechnology +
         * " for a interest rate of " + +loanInterestRiskRate 100 + " % " +
         * " The debt total is; " + debtTotal + " and asset total is " +
         * assetTotal); }
         * 
         * if (bestTechnology != null &&
         * agent.getSpecificRiskAverse().equals("true")) {
         * 
         * logger.warn(agent + " diversificationprofile? " +
         * riskdiversificationProfile +
         * " and has the following technology marketshares " +
         * technologyCapacity + " the best technology is " + bestTechnology); }
         */

        // logger.warn(agent + " includes credit-risk " +
        // agent.getInvestorIncludeCreditRisk()
        // + " and technology preferences " +
        // agent.getInvestorIncludeSubjectiveFactor()
        // + " and includes risk-averse behaviour " +
        // agent.getSpecificRiskAverse()
        // + " the agent is diversifying his portfolio? " +
        // riskdiversificationProfile
        // + " the probabilities in investing are " + technologyProbabilityArray
        // + " for the following technologies " + technologyNameArray +
        // " the NPV's are " + npvArray
        // + " the individual capacities for these technologies are " +
        // technologyCapacity
        // + " the total capacity is " + technologyCapacityTotal +
        // " the best technology is " + bestTechnology
        // + " the loan is granted for a interest rate of " +
        // +loanInterestRiskRate + " % "
        // + " checks risk-averse " + checkInvestorIsNotRiskAverse +
        // " checks technology preferences "
        // + checkInvestorIsNotTechnologyPreferences + " checks risk-averse " +
        // checkInvestorIsNotCreditRisk);

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

            double amount = 0d;

            if (agent.getInvestorIncludeCreditRisk().equals("true")) {

                amount = determineLoanAnnuities(investmentCostPayedByDebt, plant.getTechnology().getDepreciationTime(),
                        agent.getLoanInterestRate());

            } else {

                amount = determineLoanAnnuities(investmentCostPayedByDebt, plant.getTechnology().getDepreciationTime(),
                        agent.getLoanInterestRate());
            }

            agent.setLoanInterestRiskRate(loanInterestRiskRate);

            // logger.warn("Loan amount is: " + amount);
            Loan loan = reps.loanRepository.createLoan(agent, bigbank, amount, plant.getTechnology()
                    .getDepreciationTime(), getCurrentTick(), plant);
            // Create the loan
            plant.createOrUpdateLoan(loan);

            double investments = plant.getActualNominalCapacity();

            // logger.warn(agent + " invested " + investments + " against rate "
            // + 100 * agent.getLoanInterestRiskRate());

        } else {
            // logger.warn("{} found no suitable technology anymore to invest in at tick "
            // + getCurrentTick(), agent);
            // agent will not participate in the next round of investment if
            // he does not invest now
            setNotWillingToInvest(agent);
        }

    }

    // }

    // Creates n downpayments of equal size in each of the n building years of a
    // power plant
    @Transactional
    private void createSpreadOutDownPayments(EnergyProducer agent, PowerPlantManufacturer manufacturer,
            double totalDownPayment, PowerPlant plant) {
        int buildingTime = (int) plant.getActualLeadtime();
        reps.nonTransactionalCreateRepository.createCashFlow(agent, manufacturer, totalDownPayment / buildingTime,
                CashFlow.DOWNPAYMENT, getCurrentTick(), plant);
        Loan downpayment = reps.loanRepository.createLoan(agent, manufacturer, totalDownPayment / buildingTime,
                buildingTime - 1, getCurrentTick(), plant);
        plant.createOrUpdateDownPayment(downpayment);
    }

    @Transactional
    private void setNotWillingToInvest(EnergyProducer agent) {
        agent.setWillingToInvest(false);
    }

    static double cumulativeNormalDistributionFunction(double x) {
        int neg = (x < 0d) ? 1 : 0;
        if (neg == 1)
            x *= -1d;

        double k = (1d / (1d + 0.2316419 * x));
        double y = ((((1.330274429 * k - 1.821255978) * k + 1.781477937) * k - 0.356563782) * k + 0.319381530) * k;
        y = 1.0 - 0.398942280401 * Math.exp(-0.5 * x * x) * y;

        return (1d - neg) * y + neg * (1d - y);
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

    public double calculateTechnologyMarketShare(EnergyProducer producer, PowerGeneratingTechnology technology,
            long time) {

        String i = technology.getName();
        double technologyCapacity = 0d;

        for (PowerPlant plant : reps.powerPlantRepository.findOperationalPowerPlantsByOwner(producer, time)) {

            if (plant.getTechnology().getName().equals(i)) {

                technologyCapacity += plant.getActualNominalCapacity();

            } else {

            }

        }

        return technologyCapacity;
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
                double totalCapacityAvailable = 0d;

                for (Entry<PowerPlant, Double> plantCost : meritOrder.entrySet()) {
                    PowerPlant plant = plantCost.getKey();
                    double plantCapacity = 0d;
                    // Determine available capacity in the future in this
                    // segment
                    plantCapacity = plant
                            .getExpectedAvailableCapacity(time, segmentLoad.getSegment(), numberOfSegments);
                    totalCapacityAvailable += plantCapacity;
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

                // Find strategic reserve operator for the market.
                double reservePrice = 0;
                double reserveVolume = 0;
                for (StrategicReserveOperator operator : strategicReserveOperatorRepository.findAll()) {
                    ElectricitySpotMarket market1 = reps.marketRepository.findElectricitySpotMarketForZone(operator
                            .getZone());
                    if (market.getNodeId().intValue() == market1.getNodeId().intValue()) {
                        reservePrice = operator.getReservePriceSR();
                        reserveVolume = operator.getReserveVolume();
                    }
                }

                if (segmentSupply >= expectedSegmentLoad
                        && ((totalCapacityAvailable - expectedSegmentLoad) <= (reserveVolume))) {
                    expectedElectricityPricesPerSegment.put(segmentLoad.getSegment(), reservePrice);
                    // logger.warn("Price: "+
                    // expectedElectricityPricesPerSegment);
                } else if (segmentSupply >= expectedSegmentLoad
                        && ((totalCapacityAvailable - expectedSegmentLoad) > (reserveVolume))) {
                    expectedElectricityPricesPerSegment.put(segmentLoad.getSegment(), segmentPrice);
                    // logger.warn("Price: "+
                    // expectedElectricityPricesPerSegment);
                } else {
                    expectedElectricityPricesPerSegment.put(segmentLoad.getSegment(), market.getValueOfLostLoad());
                }

            }
        }
    }

}
