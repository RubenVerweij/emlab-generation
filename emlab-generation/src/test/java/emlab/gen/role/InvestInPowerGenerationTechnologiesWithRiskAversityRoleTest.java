/*******************************************************************************
 * Copyright 2012 the original author or authors.
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
package emlab.gen.role;

import org.apache.log4j.Logger;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import emlab.gen.domain.agent.EnergyProducer;
import emlab.gen.repository.MarketRepository;
import emlab.gen.repository.PowerGenerationTechnologyTargetRepository;
import emlab.gen.repository.PowerPlantRepository;
import emlab.gen.role.investment.GenericInvestmentRole;

/**
 * @author Ruben J-unit test for investment role with risk-averse behaviour
 * 
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({ "/emlab-gen-test-context.xml" })
@Transactional
public class InvestInPowerGenerationTechnologiesWithRiskAversityRoleTest {

    Logger logger = Logger.getLogger(InvestInPowerGenerationTechnologiesWithRiskAversityRoleTest.class);

    @Autowired
    PowerPlantRepository powerPlantRepository;
    @Autowired
    MarketRepository marketRepository;

    @Autowired
    Neo4jTemplate template;

    @Autowired
    GenericInvestmentRole<EnergyProducer> genericInvestmentRole;

    @Autowired
    PowerGenerationTechnologyTargetRepository powerGenerationTechnologyTargetRepository;

}