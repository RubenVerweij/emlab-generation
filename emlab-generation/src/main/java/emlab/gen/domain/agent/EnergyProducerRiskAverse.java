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
 * @rubenverweij Ruben: this energy producer includes risk averse behaviour
 * 
 *               1. specific risk averse behaviour 2. diversifying conservative
 *               behaviour
 */

@NodeEntity
public class EnergyProducerRiskAverse extends EnergyProducer implements Agent {

    // agent is risk averse / taker for particular technologies
    private boolean specificRiskAverse;

    @SimulationParameter(label = "Risk premium technology", from = -0.15, to = 0.15)
    private double riskPremiumNuclear;
    private double riskPremiumCoal;
    private double riskPremiumGas;
    private double riskPremiumRenewable;

    // agent tends to invest in conventional technologies
    private boolean conservativenessRiskProfile;

    // agent tends to follow popular technologies
    private boolean followersProfile;

    // Border where a investor is considered a giant
    @SimulationParameter(label = "Market Giant Capacity ", from = 5000, to = 150000)
    private double marketGiantCapacity;

    // parameter is needed for normalising the propensity values in the
    // portfolio diversification

    @SimulationParameter(label = "Normalisation parameter for technology shares", from = 1, to = 1.15)
    private double normalisationParameter;

    public double getNormalisationParameter() {
        return normalisationParameter;
    }

    public void setNormalisationParameter(double normalisationParameter) {
        this.normalisationParameter = normalisationParameter;
    }

    public double getMarketGiantCapacity() {
        return marketGiantCapacity;
    }

    public void setMarketGiantCapacity(double marketGiantCapacity) {
        this.marketGiantCapacity = marketGiantCapacity;
    }

    public boolean isSpecificRiskAverse() {
        return specificRiskAverse;
    }

    public void setSpecificRiskAverse(boolean specificRiskAverse) {
        this.specificRiskAverse = specificRiskAverse;
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

    public boolean isConservativenessRiskProfile() {
        return conservativenessRiskProfile;
    }

    public void setConservativenessRiskProfile(boolean conservativenessRiskProfile) {
        this.conservativenessRiskProfile = conservativenessRiskProfile;
    }

    public boolean isFollowersProfile() {
        return followersProfile;
    }

    public void setFollowersProfile(boolean followersProfile) {
        this.followersProfile = followersProfile;
    }

}
