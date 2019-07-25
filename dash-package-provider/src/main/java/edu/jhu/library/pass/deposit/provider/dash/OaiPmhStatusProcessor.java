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
import org.dataconservancy.pass.deposit.messaging.status.DefaultDepositStatusProcessor;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusResolver;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;

@Component
public class OaiPmhStatusProcessor extends DefaultDepositStatusProcessor {

    private OaiRequestProcessor requestProcessor;

    private PassClient passClient;

    private CriticalRepositoryInteraction cri;

    @Autowired
    public OaiPmhStatusProcessor(DepositStatusResolver<URI, URI> statusResolver,
                                 OaiRequestProcessor requestProcessor,
                                 PassClient passClient,
                                 CriticalRepositoryInteraction cri) {
        super(statusResolver);
        this.passClient = passClient;
        this.cri = cri;
        this.requestProcessor = requestProcessor;
    }

    @Override
    public Deposit.DepositStatus process(Deposit deposit, RepositoryConfig repositoryConfig) {
        Deposit.DepositStatus status = super.process(deposit, repositoryConfig);

        // A status of ACCEPTED indicates that the Item has completed the DSpace workflow and has been accepted
        // into the DSpace archive.
        //
        // If the Item hasn't been accepted into the archive, then it won't have an Item URL, so there's no point in
        // continuing.
        if (Deposit.DepositStatus.ACCEPTED != status) {
            return status;
        }

        Submission submission = passClient.readResource(deposit.getSubmission(), Submission.class);
        Instant from = submission.getSubmittedDate().toDate().toInstant();

        // The default status returned by this method is DepositStatus.SUBMITTED.  If an OAI-PMH record matching
        // this Submission is found, and the record contains a DSpace Item URL, the status will be updated to
        // DepositStatus.ACCEPTED.  Until then, the DepositStatus remains as SUBMITTED.
        AtomicReference<Deposit.DepositStatus> result = new AtomicReference<>(Deposit.DepositStatus.SUBMITTED);

        // Retrieve all OAI-PMH records from the Submission.submittedDate to the present.  Analyze each record,
        // searching for a record that contains the PASS Submission URI and a DSpace Item URL.
        //
        // If a match is found, update the RepositoryCopy with the DSpace Item URL, and set the DepositStatus to
        // ACCEPTED.  If no match is found, leave the DepositStatus as SUBMITTED.
        requestProcessor.analyzeRecords(
                submission.getId(), requestProcessor.listIdentifiers(from))
                    .ifPresent(repoCopyUrl -> {
                                cri.performCritical(deposit.getRepositoryCopy(), RepositoryCopy.class,
                                        (criRepoCopy) -> criRepoCopy.getCopyStatus() != CopyStatus.COMPLETE,
                                        (criRepoCopy) -> criRepoCopy.getAccessUrl() != null
                                                && criRepoCopy.getExternalIds() != null
                                                && criRepoCopy.getExternalIds().size() > 0,
                                        (criRepoCopy) -> {
                                            // The super class will update the RepositoryCopy.copyStatus
                                            criRepoCopy.setAccessUrl(URI.create(repoCopyUrl.toString()));
                                            criRepoCopy.setExternalIds(singletonList(repoCopyUrl.toString()));
                                            return criRepoCopy;
                                        });
                                result.set(Deposit.DepositStatus.ACCEPTED);
                            }
                    );

        return result.get();
    }
}
