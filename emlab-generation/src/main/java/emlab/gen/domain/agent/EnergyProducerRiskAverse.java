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
 * 
 * 
 */

@NodeEntity
public class EnergyProducerRiskAverse extends EnergyProducer implements Agent {

    // @RelatedTo(type = "PRODUCER_PRODUCERCREDITRISK", elementClass =
    // EnergyProducerCreditRisk.class, direction = Direction.OUTGOING)
    // EnergyProducerCreditRisk energyProducerCreditRisk;

    // agent is risk averse / taker for particular technologies
    @SimulationParameter(label = "Risk premium technology", from = -0.05, to = 0.05)
    private double riskPremiumNuclear;
    private double riskPremiumCoal;
    private double riskPremiumGas;
    private double riskPremiumRenewable;

    // agent tends to invest in conventional technologies
    private boolean conservativenessRiskProfile;

    // agent tends to diversify technologies
    private boolean progressivenessRiskProfile;

    // agent tends to follow popular technologies
    private boolean followersProfile;

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

    public boolean isProgressivenessRiskProfile() {
        return progressivenessRiskProfile;
    }

    public void setProgressivenessRiskProfile(boolean progressivenessRiskProfile) {
        this.progressivenessRiskProfile = progressivenessRiskProfile;
    }

    public boolean isFollowersProfile() {
        return followersProfile;
    }

    public void setFollowersProfile(boolean followersProfile) {
        this.followersProfile = followersProfile;
    }

}
