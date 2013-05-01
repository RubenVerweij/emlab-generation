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
package emlab.gen.domain.agent;

import org.springframework.data.neo4j.annotation.NodeEntity;

import agentspring.agent.Agent;
import agentspring.simulation.SimulationParameter;

/**
 * @rubenverweij Ruben: this energy producer includes
 * 
 *               1 risk averse behaviour 2 technology preferences 3 risk averse
 *               behaviour
 * 
 */

@NodeEntity
public class EnergyProducerAdditions extends EnergyProducer implements Agent {

    private String investorIncludeCreditRisk;
    private String investorIncludeSubjectiveFactor;
    private String specificRiskAverse;
    private String diversificationProfile;
    private String conservativenessRiskProfile;

    // these parameters are beloning to the credit-risk consideration

    @SimulationParameter(label = "Loan Interest free Rate", from = 0, to = 1)
    private double loanInterestFreeRate;

    @SimulationParameter(label = "Asset value path deviation", from = 0, to = 1)
    private double assetValueDeviation;

    @SimulationParameter(label = "Time to maturity BS debt-pricing model", from = 1, to = 15)
    private double timeToMaturity;

    @SimulationParameter(label = "Additional debt", from = 0, to = 1e8)
    private double debtBias;

    // these parameters are beloning to the technology preferences

    @SimulationParameter(label = "Weightfactor for profit", from = 0, to = 5)
    private double weightfactorProfit;

    @SimulationParameter(label = "Weightfactor for emission", from = 0, to = 5)
    private double weightfactorEmission;

    @SimulationParameter(label = "Weightfactor for efficiency", from = 0, to = 5)
    private double weightfactorEfficiency;

    @SimulationParameter(label = "Weightfactor for lifetime", from = 0, to = 5)
    private double weightfactorLifeTime;

    @SimulationParameter(label = "Weightfactor for fuelprice", from = 0, to = 5)
    private double weightfactorFuelPrice;

    @SimulationParameter(label = "Weightfactor for investmentcost", from = 0, to = 5)
    private double weigthfactorInvestmentCost;

    @SimulationParameter(label = "Weightfactor for runninghours", from = 0, to = 5)
    private double weightfactorMinimalRunningHours;

    @SimulationParameter(label = "Normalisation parameter for MCDA", from = 1, to = 1.15)
    private double normalisationParameter;

    // these variables are beloning to the risk-averse behaviour

    @SimulationParameter(label = "Risk premium technology", from = -0.15, to = 0.15)
    private double riskPremiumNuclear;
    private double riskPremiumCoal;
    private double riskPremiumGas;
    private double riskPremiumRenewable;

    private boolean followersProfile;

    @SimulationParameter(label = "Market Giant Capacity ", from = 5000, to = 150000)
    private double marketGiantCapacity;

    public String getInvestorIncludeCreditRisk() {
        return investorIncludeCreditRisk;
    }

    public void setInvestorIncludeCreditRisk(String investorIncludeCreditRisk) {
        this.investorIncludeCreditRisk = investorIncludeCreditRisk;
    }

    public String getInvestorIncludeSubjectiveFactor() {
        return investorIncludeSubjectiveFactor;
    }

    public void setInvestorIncludeSubjectiveFactor(String investorIncludeSubjectiveFactor) {
        this.investorIncludeSubjectiveFactor = investorIncludeSubjectiveFactor;
    }

    public String getSpecificRiskAverse() {
        return specificRiskAverse;
    }

    public void setSpecificRiskAverse(String specificRiskAverse) {
        this.specificRiskAverse = specificRiskAverse;
    }

    public String getDiversificationProfile() {
        return diversificationProfile;
    }

    public void setDiversificationProfile(String diversificationProfile) {
        this.diversificationProfile = diversificationProfile;
    }

    public String getConservativenessRiskProfile() {
        return conservativenessRiskProfile;
    }

    public void setConservativenessRiskProfile(String conservativenessRiskProfile) {
        this.conservativenessRiskProfile = conservativenessRiskProfile;
    }

    public double getLoanInterestFreeRate() {
        return loanInterestFreeRate;
    }

    public void setLoanInterestFreeRate(double loanInterestFreeRate) {
        this.loanInterestFreeRate = loanInterestFreeRate;
    }

    public double getAssetValueDeviation() {
        return assetValueDeviation;
    }

    public void setAssetValueDeviation(double assetValueDeviation) {
        this.assetValueDeviation = assetValueDeviation;
    }

    public double getTimeToMaturity() {
        return timeToMaturity;
    }

    public void setTimeToMaturity(double timeToMaturity) {
        this.timeToMaturity = timeToMaturity;
    }

    public double getDebtBias() {
        return debtBias;
    }

    public void setDebtBias(double debtBias) {
        this.debtBias = debtBias;
    }

    public double getWeightfactorProfit() {
        return weightfactorProfit;
    }

    public void setWeightfactorProfit(double weightfactorProfit) {
        this.weightfactorProfit = weightfactorProfit;
    }

    public double getWeightfactorEmission() {
        return weightfactorEmission;
    }

    public void setWeightfactorEmission(double weightfactorEmission) {
        this.weightfactorEmission = weightfactorEmission;
    }

    public double getWeightfactorEfficiency() {
        return weightfactorEfficiency;
    }

    public void setWeightfactorEfficiency(double weightfactorEfficiency) {
        this.weightfactorEfficiency = weightfactorEfficiency;
    }

    public double getWeightfactorLifeTime() {
        return weightfactorLifeTime;
    }

    public void setWeightfactorLifeTime(double weightfactorLifeTime) {
        this.weightfactorLifeTime = weightfactorLifeTime;
    }

    public double getWeightfactorFuelPrice() {
        return weightfactorFuelPrice;
    }

    public void setWeightfactorFuelPrice(double weightfactorFuelPrice) {
        this.weightfactorFuelPrice = weightfactorFuelPrice;
    }

    public double getWeigthfactorInvestmentCost() {
        return weigthfactorInvestmentCost;
    }

    public void setWeigthfactorInvestmentCost(double weigthfactorInvestmentCost) {
        this.weigthfactorInvestmentCost = weigthfactorInvestmentCost;
    }

    public double getWeightfactorMinimalRunningHours() {
        return weightfactorMinimalRunningHours;
    }

    public void setWeightfactorMinimalRunningHours(double weightfactorMinimalRunningHours) {
        this.weightfactorMinimalRunningHours = weightfactorMinimalRunningHours;
    }

    public double getNormalisationParameter() {
        return normalisationParameter;
    }

    public void setNormalisationParameter(double normalisationParameter) {
        this.normalisationParameter = normalisationParameter;
    }

    public double getRiskPremiumNuclear() {
        return riskPremiumNuclear;
    }

    public void setRiskPremiumNuclear(double riskPremiumNuclear) {
        this.riskPremiumNuclear = riskPremiumNuclear;
    }

    public double getRiskPremiumCoal() {
        return riskPremiumCoal;
    }

    public void setRiskPremiumCoal(double riskPremiumCoal) {
        this.riskPremiumCoal = riskPremiumCoal;
    }

    public double getRiskPremiumGas() {
        return riskPremiumGas;
    }

    public void setRiskPremiumGas(double riskPremiumGas) {
        this.riskPremiumGas = riskPremiumGas;
    }

    public double getRiskPremiumRenewable() {
        return riskPremiumRenewable;
    }

    public void setRiskPremiumRenewable(double riskPremiumRenewable) {
        this.riskPremiumRenewable = riskPremiumRenewable;
    }

    public boolean isFollowersProfile() {
        return followersProfile;
    }

    public void setFollowersProfile(boolean followersProfile) {
        this.followersProfile = followersProfile;
    }

    public double getMarketGiantCapacity() {
        return marketGiantCapacity;
    }

    public void setMarketGiantCapacity(double marketGiantCapacity) {
        this.marketGiantCapacity = marketGiantCapacity;
    }

}
