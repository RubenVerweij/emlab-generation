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
 *               to specific technology preferences based upon;
 * 
 *               1) Technology preferences based upon company attitude 2)
 *               Technology preferences based upon market share
 * 
 */

@NodeEntity
public class EnergyProducerTechnologyPreferences extends EnergyProducer implements Agent {

    /**
     * Booleans good idea?
     */

    @SimulationParameter(label = "Stereotype based upon company attitude")
    private boolean greenAttitude;
    private boolean coalReticent;
    private boolean profitOnly;

    @SimulationParameter(label = "Stereotype based upon asset base")
    private double marketShare;

    public double getMarketShare() {
        return marketShare;
    }

    public void setMarketShare(double marketShare) {
        this.marketShare = marketShare;
    }

    public boolean isGreenAttitude() {
        return greenAttitude;
    }

    public void setGreenAttitude(boolean greenAttitude) {
        this.greenAttitude = greenAttitude;
    }

    public boolean isCoalReticent() {
        return coalReticent;
    }

    public void setCoalReticent(boolean coalReticent) {
        this.coalReticent = coalReticent;
    }

    public boolean isProfitOnly() {
        return profitOnly;
    }

    public void setProfitOnly(boolean profitOnly) {
        this.profitOnly = profitOnly;
    }

}