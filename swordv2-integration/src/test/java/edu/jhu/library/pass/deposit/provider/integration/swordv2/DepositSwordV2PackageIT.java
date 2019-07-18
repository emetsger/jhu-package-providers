/*
 * Copyright 2019 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.jhu.library.pass.deposit.provider.integration.swordv2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataconservancy.pass.deposit.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.pass.deposit.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.pass.deposit.integration.shared.AbstractSubmissionFixture;
import org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph;
import org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph.GraphBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Repository;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import submissions.SubmissionResourceUtil;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.net.URI.create;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static submissions.SubmissionResourceUtil.lookupStream;

/**
 * Insures that a package generated for J10P and DASH can be successfully submitted via SWORD
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositSwordV2PackageIT extends AbstractSubmissionFixture {

    private static final String REPOSITORIES_JSON = System.getProperty("pass.deposit.repository.configuration", "/repositories.json");

    private static SubmissionGraph graph;

    private static DepositSubmission depositSubmission;

    private FilesystemModelBuilder builder;

    @BeforeClass
    public static void setUpGraph() throws Exception {
        PassJsonFedoraAdapter adapter = new PassJsonFedoraAdapter();
        GraphBuilder graphBuilder = GraphBuilder.newGraph(lookupStream(create("fake:submission1")),
                adapter);

        // Replace all the Repository entities in the graph with the repositories defined in the Deposit Services
        // repositories.json with a transport of SWORDv2
        assertTrue(graphBuilder.stream(Repository.class).count() > 0);
        graphBuilder.stream(Repository.class).map(PassEntity::getId).forEach(graphBuilder::removeEntity);
        assertEquals(0, graphBuilder.stream(Repository.class).count());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode dsConfig = mapper.readTree(DepositSwordV2PackageIT.class.getResourceAsStream(REPOSITORIES_JSON));
        dsConfig.fieldNames().forEachRemaining(field ->
            graphBuilder.addEntity(Repository.class)
                .set("name", field)
                .set("repositoryKey", field)
                .linkFrom(graphBuilder.submission(), "repositories")
                .build());

        graph = graphBuilder.build();

        assertEquals(2, graph.stream(Repository.class).count());
        assertEquals(2, graph.submission().getRepositories().size());

        graph.submission().getRepositories().forEach(uri -> {
            assertTrue(graph.submission().getRepositories().contains(uri));
        });

        FilesystemModelBuilder builder = new FilesystemModelBuilder(true);

        depositSubmission = builder.build(graph.asJson(), Collections.emptyMap());
    }

    @Before
    public void setUp() throws Exception {
        System.err.println(graph.submission().getId());
        System.err.println(depositSubmission.getId());
        triggerSubmission(URI.create(depositSubmission.getId()));
    }

    @Test
    public void foo() {

    }
}
