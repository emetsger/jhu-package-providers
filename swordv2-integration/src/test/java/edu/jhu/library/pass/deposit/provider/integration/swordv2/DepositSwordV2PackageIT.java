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
import org.dataconservancy.deposit.util.async.Condition;
import org.dataconservancy.pass.client.PassClientFactory;
import org.dataconservancy.pass.deposit.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.pass.deposit.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.pass.deposit.integration.shared.AbstractSubmissionFixture;
import org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph;
import org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph.GraphBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;
import submissions.SubmissionResourceUtil;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.net.URI.create;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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

    @Before
    @Override
    public void setUpOkHttp() throws Exception {
        this.fcrepoPass = System.getProperty("pass.fedora.password", System.getenv("PASS_FEDORA_PASSWORD"));
        this.fcrepoUser = System.getProperty("pass.fedora.user", System.getenv("PASS_FEDORA_USER"));
        this.fcrepoBaseUrl = System.getProperty("pass.fedora.baseurl", System.getenv("PASS_FEDORA_BASEURL"));
        this.contextUri = System.getProperty("pass.jsonld.context", System.getenv("PASS_JSONLD_CONTEXT"));
        this.okHttp = fcrepoClient(this.fcrepoUser, this.fcrepoPass);
    }

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
    public void setUpPassClient() {
        this.passClient = PassClientFactory.getPassClient();
    }

    @Test
    public void foo() {
        triggerSubmission(URI.create(depositSubmission.getId()));

        // Wait for two RepositoryCopy resources:
        //   one to J10P, one to DASH

        // Should wait for the J10P RepositoryCopy to be complete with an Item URL and external IDs

        Condition<RepositoryCopy> j10p = new Condition<>(() -> {
            URI repo = passClient.findByAttribute(Repository.class, "repositoryKey", "jscholarship");
            System.err.println("Found Repository: " + repo);
            URI repoCopy = passClient.findByAttribute(RepositoryCopy.class, "repository", repo);
            System.err.println("Found RepositoryCopy: " + repoCopy);
            return passClient.readResource(repoCopy, RepositoryCopy.class);
        },
        (repoCopy -> RepositoryCopy.CopyStatus.ACCEPTED == repoCopy.getCopyStatus()),
        "Find J10P repo copy");

        // The DASH RepositoryCopy should be IN_PROGRESS, with a null Item URL and external IDs
        Condition<RepositoryCopy> dash = new Condition<>(() -> {
            URI repo = passClient.findByAttribute(Repository.class, "repositoryKey", "dash");
            System.err.println("Found Repository: " + repo);
            URI repoCopy = passClient.findByAttribute(RepositoryCopy.class, "repository", repo);
            System.err.println("Found RepositoryCopy: " + repoCopy);
            return passClient.readResource(repoCopy, RepositoryCopy.class);
        },
        (repoCopy -> RepositoryCopy.CopyStatus.IN_PROGRESS == repoCopy.getCopyStatus()),
        "Find DASH repo copy");

        System.err.println("Waiting for J10P repoCopy...");
        j10p.await();
        System.err.println("Found J10P repoCopy " + String.join(", ", j10p.getResult().getId().toString(), j10p.getResult().getCopyStatus().toString(), j10p.getResult().getAccessUrl().toString()));

        System.err.println("Waiting for DASH repoCopy...");
        dash.await();
        System.err.println("Found DASH repoCopy " + String.join(", ", dash.getResult().getId().toString(), dash.getResult().getCopyStatus().toString()));
        assertNull(dash.getResult().getAccessUrl());
    }
}
