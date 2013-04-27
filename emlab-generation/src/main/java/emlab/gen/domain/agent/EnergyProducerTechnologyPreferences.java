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
 * @rubenverweij Ruben: this energy producer has a biased investment process due
 *               to specific technology preferences based upon various criteria;
 */

@NodeEntity
public class EnergyProducerTechnologyPreferences extends EnergyProducer implements Agent {

    private boolean investorIncludeSubjectiveFactor;

    @SimulationParameter(label = "Weightfactor for profit", from = 0, to = 5)
    private int weightfactorProfit;
    @SimulationParameter(label = "Weightfactor for emission", from = 0, to = 5)
    private int weightfactorEmission;
    @SimulationParameter(label = "Weightfactor for efficiency", from = 0, to = 5)
    private int weightfactorEfficiency;
    @SimulationParameter(label = "Weightfactor for lifetime", from = 0, to = 5)
    private int weightfactorLifeTime;
    @SimulationParameter(label = "Weightfactor for fuelprice", from = 0, to = 5)
    private int weightfactorFuelPrice;
    @SimulationParameter(label = "Weightfactor for investmentcost", from = 0, to = 5)
    private int weigthfactorInvestmentCost;
    @SimulationParameter(label = "Weightfactor for runninghours", from = 0, to = 5)
    private int weightfactorMinimalRunningHours;

    // parameter is needed for normalising the propensity values in the MCDA

    @SimulationParameter(label = "Normalisation parameter for MCDA", from = 1, to = 1.15)
    private double normalisationParameter;

    public boolean isInvestorIncludeSubjectiveFactor() {
        return investorIncludeSubjectiveFactor;
    }

    public void setInvestorIncludeSubjectiveFactor(boolean investorIncludeSubjectiveFactor) {
        this.investorIncludeSubjectiveFactor = investorIncludeSubjectiveFactor;
    }

    public double getNormalisationParameter() {
        return normalisationParameter;
    }

    public void setNormalisationParameter(double normalisationParameter) {
        this.normalisationParameter = normalisationParameter;
    }

    public int getWeigthfactorInvestmentCost() {
        return weigthfactorInvestmentCost;
    }

    public void setWeigthfactorInvestmentCost(int weigthfactorInvestmentCost) {
        this.weigthfactorInvestmentCost = weigthfactorInvestmentCost;
    }

    public int getWeightfactorMinimalRunningHours() {
        return weightfactorMinimalRunningHours;
    }

    public void setWeightfactorMinimalRunningHours(int weightfactorMinimalRunningHours) {
        this.weightfactorMinimalRunningHours = weightfactorMinimalRunningHours;
    }

    public int getWeightfactorLifeTime() {
        return weightfactorLifeTime;
    }

    public void setWeightfactorLifeTime(int weightfactorLifeTime) {
        this.weightfactorLifeTime = weightfactorLifeTime;
    }

    public int getWeightfactorFuelPrice() {
        return weightfactorFuelPrice;
    }

    public void setWeightfactorFuelPrice(int weightfactorFuelPrice) {
        this.weightfactorFuelPrice = weightfactorFuelPrice;
    }

    public int getWeightfactorProfit() {
        return weightfactorProfit;
    }

    public int getWeightfactorEfficiency() {
        return weightfactorEfficiency;
    }

    public void setWeightfactorEfficiency(int weightfactorEfficiency) {
        this.weightfactorEfficiency = weightfactorEfficiency;
    }

    public void setWeightfactorProfit(int weightfactorProfit) {
        this.weightfactorProfit = weightfactorProfit;
    }

    public int getWeightfactorEmission() {
        return weightfactorEmission;
    }

    public void setWeightfactorEmission(int weightfactorEmission) {
        this.weightfactorEmission = weightfactorEmission;
    }

}
