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
 * @rubenverweij Ruben: this energy producer includes credit-risk considerations
 *               depending on the financial structure.
 */

@NodeEntity
public class EnergyProducerCreditRisk extends EnergyProducer implements Agent {

    // @RelatedTo(type = "PRODUCER_PRODUCERCREDITRISK", elementClass =
    // EnergyProducerCreditRisk.class, direction = Direction.OUTGOING)
    // EnergyProducerCreditRisk energyProducerCreditRisk;

    @SimulationParameter(label = "Loan Interest free Rate", from = 0, to = 1)
    private double loanInterestFreeRate;

    @SimulationParameter(label = "Asset value path deviation", from = 0, to = 1)
    private double assetValueDeviation;

    @SimulationParameter(label = "Time to maturity BS debt-pricing model", from = 1, to = 15)
    private double timeToMaturity;

    // this debt bias is modelled to test the black-scholes debt pricing model
    // in a market with more debt than 100 percent of the total asset value.
    // This implies that companies are required to borrow money against higher
    // debt-rates.

    @SimulationParameter(label = "Additional debt", from = 0, to = 1e8)
    private double debtBias;

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

    public double getAssetValueDeviation() {
        return assetValueDeviation;
    }

    public void setAssetValueDeviation(double assetValueDeviation) {
        this.assetValueDeviation = assetValueDeviation;
    }

    public double getLoanInterestFreeRate() {
        return loanInterestFreeRate;
    }

    public void setLoanInterestFreeRate(double loanInterestFreeRate) {
        this.loanInterestFreeRate = loanInterestFreeRate;
    }

}
