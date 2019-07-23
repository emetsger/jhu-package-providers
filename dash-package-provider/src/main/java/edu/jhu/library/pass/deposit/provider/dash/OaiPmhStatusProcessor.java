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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.status.DefaultDepositStatusProcessor;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusResolver;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

@Component
public class OaiPmhStatusProcessor extends DefaultDepositStatusProcessor {

    private OaiUrlBuilder urlBuilder;

    private OkHttpClient oaiClient;

    private OaiResponseBodyProcessor process;

    private PassClient passClient;

    private CriticalRepositoryInteraction cri;

    @Autowired
    public OaiPmhStatusProcessor(DepositStatusResolver<URI, URI> statusResolver,
                                 OaiUrlBuilder urlBuilder,
                                 OkHttpClient oaiClient,
                                 OaiResponseBodyProcessor process,
                                 PassClient passClient,
                                 CriticalRepositoryInteraction cri) {
        super(statusResolver);
        this.urlBuilder = urlBuilder;
        this.oaiClient = oaiClient;
        this.process = process;
        this.passClient = passClient;
        this.cri = cri;
    }

    @Override
    public Deposit.DepositStatus process(Deposit deposit, RepositoryConfig repositoryConfig) {
        Deposit.DepositStatus status = super.process(deposit, repositoryConfig);
        if (Deposit.DepositStatus.ACCEPTED != status) {
            return status;
        }

        Submission submission = passClient.readResource(deposit.getSubmission(), Submission.class);

        // list identifiers from OAI-PMH endpoint since the Submission date
        //   (check error)

        URL listRecords = null;
        String resumptionToken = null;
        List<String> recordIdentifiers = new ArrayList<>();
        Instant from = submission.getSubmittedDate().toDate().toInstant();

        do {
            listRecords = urlBuilder.listIdentifiers(resumptionToken, from);

            // Headers like Accept, From, User-Agent, and authentication are added by the class that configures the
            // OkHttpClient as interceptors
            try (Response res = oaiClient.newCall(new Request.Builder()
                    .url(listRecords)
                    .build()).execute()) {
                if (res.code() != 200) {
                    throw new RuntimeException(
                            format("Error retrieving %s (code: %s): %s", listRecords, res.code(), res.message()));
                }

                resumptionToken = encode(process.listIdentifiersResponse(res.body().byteStream(), recordIdentifiers));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } while (resumptionToken != null && resumptionToken.trim().length() > 0);

        // For each record identifier:
        for (String oaiRecordIdentifier : recordIdentifiers) {

            //  issue a GetRecord request
            //      (check error)

            URL getRecord = urlBuilder.getRecord(oaiRecordIdentifier);
            URL itemUrl;

            try (Response res = oaiClient.newCall(new Request.Builder()
                    .url(getRecord)
                    .build()).execute()) {

                if (res.code() != 200) {
                    throw new RuntimeException(
                            format("Error retrieving %s (code: %s): %s", getRecord, res.code(), res.message()));
                }

                //  Parse request body for PASS Submission URI
                //  Parse request body for Harvard DSpace Item URL (dc.identifier uri beginning 'http://nrs.harvard.edu')
                itemUrl = process.getRecordResponse(res.body().byteStream(), submission.getId());

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (itemUrl == null) {
                // no pass submission found or no DSpace Item URL found, move on
                continue;
            }

            //  Resolve the RepositoryCopy for the Submission, and update its RepositoryCopy access url and external ids
            //  the caller of this processor will update the RepositoryCopy.copyStatus

            cri.performCritical(deposit.getRepositoryCopy(), RepositoryCopy.class,
                    (criRepoCopy) -> criRepoCopy.getCopyStatus() != RepositoryCopy.CopyStatus.COMPLETE,
                    (criRepoCopy) -> criRepoCopy.getAccessUrl() != null
                            && criRepoCopy.getExternalIds() != null
                            && criRepoCopy.getExternalIds().size() > 0,
                    (criRepoCopy) -> {
                        criRepoCopy.setAccessUrl(URI.create(itemUrl.toString()));
                        criRepoCopy.setExternalIds(Collections.singletonList(itemUrl.toString()));
                        return criRepoCopy;
                    });

            break;
        }

        return status;
    }

    static String encode(String resumptionToken) {
        if (resumptionToken == null || resumptionToken.trim().length() == 0) {
            return resumptionToken;
        }

        char[] illegal = new char[] {
                '/',
                '?',
                '#',
                '=',
                '&',
                ':',
                ';',
                ' ',
                '%',
                '+',
                '@',
                '$',
                ',',
                '"',
                '>',
                '<'};

        String[] encoding = {
                "%2F",
                "%3F",
                "%23",
                "%3D",
                "%26",
                "%3A",
                "%3B",
                "%20",
                "%25",
                "%2B",
                "%40",
                "%24",
                "%2C",
                "%22",
                "%3E",
                "%3C"};

        StringBuilder sb = new StringBuilder(resumptionToken);

        int replacementOffset = 0;

        for (int i = 0, offset = 0; i < resumptionToken.length(); i++, offset = (i + replacementOffset)) {
            char candidate = resumptionToken.charAt(i);

            for (int j = 0; j < illegal.length; j++) {
                if (candidate == illegal[j]) {
                    sb.replace(offset, offset+1, encoding[j]);
                    replacementOffset += encoding[j].length() - 1;
                    break;
                }
            }
        }

        return sb.toString();
    }

}
