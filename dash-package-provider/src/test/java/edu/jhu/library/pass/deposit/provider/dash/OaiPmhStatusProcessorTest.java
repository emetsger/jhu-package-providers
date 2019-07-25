/*
 *
 *  * Copyright 2019 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package edu.jhu.library.pass.deposit.provider.dash;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryDepositConfig;
import org.dataconservancy.pass.deposit.messaging.config.repository.StatusMapping;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusResolver;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.net.URI.create;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class OaiPmhStatusProcessorTest {

    // Mocks for OaiPmhStatusProcessor

    private OaiRequestProcessor requestProcessor;

    private PassClient passClient;

    private CriticalRepositoryInteraction cri;

    private Submission submission;

    private OaiPmhStatusProcessor underTest;


    // Mocks for the super class

    private DepositStatusResolver statusResolver;

    private Deposit deposit;

    private RepositoryConfig repoConfig;

    private StatusMapping statusMapping;

    private String depositStatusRef = "http://swordapi/swordState";

    private URI submissionUri = URI.create("http://pass/submissions/submission1234");

    private URI repoCopyUri = URI.create("http://pass/repositoryCopies/repoCopy1234");

    /**
     * A SWORD state that will map to the default Deposit.DepositStatus as returned by the {@link #statusMapping}
     */
    private String inWorkflowSwordState = "http://dspace.org/state/inworkflow";

    /**
     * A SWORD state that will map to Deposit.DepositStatus.ACCEPTED as returned by the {@link #statusMapping}
     */
    private String archivedState = "http://dspace.org/state/archived";

    @Before
    public void setUp() throws Exception {
        statusResolver = mock(DepositStatusResolver.class);
        deposit = new Deposit();
        deposit.setId(create(UUID.randomUUID().toString()));
        deposit.setDepositStatusRef(depositStatusRef);
        deposit.setSubmission(submissionUri);
        deposit.setRepositoryCopy(repoCopyUri);
        submission = new Submission();
        submission.setId(submissionUri);
        submission.setSubmittedDate(DateTime.now());
        repoConfig = mock(RepositoryConfig.class);
        statusMapping = new StatusMapping();

        // http://dspace.org/state/archived maps to DepositStatus.ACCEPTED
        statusMapping.addStatusEntry(archivedState, Deposit.DepositStatus.ACCEPTED.toString());
        // Any other status uri maps to DepositStatus.SUBMITTED
        statusMapping.setDefaultMapping(Deposit.DepositStatus.SUBMITTED.toString());

        RepositoryDepositConfig depositConfig = mock(RepositoryDepositConfig.class);
        when(repoConfig.getRepositoryDepositConfig()).thenReturn(depositConfig);
        when(depositConfig.getStatusMapping()).thenReturn(statusMapping);

        requestProcessor = mock(OaiRequestProcessor.class);
        passClient = mock(PassClient.class);
        cri = mock(CriticalRepositoryInteraction.class);

        when(passClient.readResource(submissionUri, Submission.class)).thenReturn(submission);

        underTest = new OaiPmhStatusProcessor(statusResolver, requestProcessor, passClient, cri);
    }

    /**
     * if the super class determines that the deposit status is not accepted, then the OaiPmhStatusProcessor should
     * abort.  OAI-PMH is only used once the PASS Submission has been accepted into the DSpace archive.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void notAccepted() {
        when(statusResolver.resolve(create(depositStatusRef), repoConfig)).thenReturn(create(inWorkflowSwordState));

        Deposit.DepositStatus result = underTest.process(deposit, repoConfig);

        verifyZeroInteractions(passClient, cri, requestProcessor);
        assertEquals(Deposit.DepositStatus.SUBMITTED, result);
    }

    /**
     * When an OAI-PMH record contains the PASS submission URI <em>and</em> a DSpace Item URL, the processor should
     * return a status of ACCEPTED.
     *
     * @throws MalformedURLException
     */
    @Test
    @SuppressWarnings("unchecked")
    public void singleIdentifierMatchingSubmission() throws MalformedURLException {
        URL repoCopyUrl = new URL("http://fedora/repositoryCopies/1234");
        Stream<String> recordStream = Stream.of("moo");

        // The super class should resolve the SWORD state 'http://dspace.org/state/archived' as ACCEPTED.  Any
        // other state would have the OAI-PMH status processor abort.
        when(statusResolver.resolve(create(depositStatusRef), repoConfig)).thenReturn(create(archivedState));

        // Contact the OAI-PMH endpoint and list all identifiers created or modified since the submission date,
        // return a single OAI-PMH record 'moo'
        when(requestProcessor.listIdentifiers(any())).thenReturn(recordStream);

        // Retrieve the OAI-PMH record 'moo' and analyze the metadata, searching for the PASS submission URI and the
        // DSpace Item URL.  This mock returns the DSpace Item URL, indicating a match.
        when(requestProcessor.analyzeRecords(submissionUri, recordStream)).thenReturn(Optional.of(repoCopyUrl));

        // Invoke the processor
        Deposit.DepositStatus result = underTest.process(deposit, repoConfig);

        // The Submission should be read by the PASS Client
        verify(passClient).readResource(submissionUri, Submission.class);

        // The request processor should have been invoked to list identifiers and retrieve the record
        verify(requestProcessor).listIdentifiers(any());
        verify(requestProcessor).analyzeRecords(submissionUri, recordStream);

        // Because the request processor returned the DSpace Item URL, the CRI updating the RepositoryCopy should be
        // invoked.
        verify(cri).performCritical(eq(deposit.getRepositoryCopy()), eq(RepositoryCopy.class), any(Predicate.class), any(Predicate.class), any());

        // Finally the DepositStatus should be ACCEPTED
        assertEquals(Deposit.DepositStatus.ACCEPTED, result);
    }

    /**
     * When the OAI-PMH record does not contain the PASS submission URI <em>or</em> it contains the the PASS
     * submission URI but there's no DSpace Item URL, the processor should return a status of DepositStatus.SUBMITTED.
     * <p>
     * This insures that the Deposit will remain in an <em>intermediate</em> status, and the OaiPmhStatusProcessor will
     * eventually be invoked again, when the OAI-PMH feed may have been updated to contain the required information.
     * </p>
     *
     * @throws MalformedURLException
     */
    @Test
    @SuppressWarnings("unchecked")
    public void noMatch() throws MalformedURLException {
        Stream<String> recordStream = Stream.of("moo");

        // The super class should resolve the SWORD state 'http://dspace.org/state/archived' as ACCEPTED.  Any
        // other state would have the OAI-PMH status processor abort.
        when(statusResolver.resolve(create(depositStatusRef), repoConfig)).thenReturn(create(archivedState));

        // Contact the OAI-PMH endpoint and list all identifiers created or modified since the submission date,
        // return a single OAI-PMH record 'moo'
        when(requestProcessor.listIdentifiers(any())).thenReturn(recordStream);

        // Retrieve the OAI-PMH record 'moo' and analyze the metadata, searching for the PASS submission URI and the
        // DSpace Item URL.  This mock returns an empty Optional, indicating no match
        when(requestProcessor.analyzeRecords(submissionUri, recordStream)).thenReturn(Optional.empty());

        // Invoke the processor
        Deposit.DepositStatus result = underTest.process(deposit, repoConfig);

        // The Submission should be read by the PASS Client
        verify(passClient).readResource(submissionUri, Submission.class);

        // The request processor should have been invoked to list identifiers and retrieve the record
        verify(requestProcessor).listIdentifiers(any());
        verify(requestProcessor).analyzeRecords(submissionUri, recordStream);

        // Because the request processor did not return the DSpace Item URL, the CRI updating the RepositoryCopy should
        // not be invoked.
        verifyZeroInteractions(cri);

        // Finally the DepositStatus should be SUBMITTED
        assertEquals(Deposit.DepositStatus.SUBMITTED, result);
    }
}