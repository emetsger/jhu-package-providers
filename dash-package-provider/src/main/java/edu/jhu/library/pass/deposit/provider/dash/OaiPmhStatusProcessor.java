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

import com.damnhandy.uri.template.UriTemplate;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.status.DefaultDepositStatusProcessor;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusResolver;
import org.dataconservancy.pass.model.Deposit;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

public class OaiPmhStatusProcessor extends DefaultDepositStatusProcessor {

    private static final String GET_RECORD = "GetRecord";

    private static final String GET_RECORD_TEMPLATE = "";

    private static final String LIST_IDENTIFIERS = "ListIdentifiers";

    private static final String LIST_IDENTIFIERS_TEMPLATE = "{{oaiBaseUrl}}{{?verb}}{{?from}}{{?metadataPrefix}}{{?resumptionToken}}";

    static final String DIM_METADATA_PREFIX = "dim";

    static final String HARVARD_NRS_URL_REGEX = "http://nrs.harvard.edu";

    static final Pattern HARVARD_REPOCOPY_PATTERN = Pattern.compile("^" + HARVARD_NRS_URL_REGEX);

    private OkHttpClient oaiClient;

    private String oaiBaseUrl;

    private String metadataPrefix;

    private Matcher repoCopyUriMatcher;

    public OaiPmhStatusProcessor(DepositStatusResolver<URI, URI> statusResolver) {
        super(statusResolver);
    }

    @Override
    public Deposit.DepositStatus process(Deposit deposit, RepositoryConfig repositoryConfig) {
        Deposit.DepositStatus status = super.process(deposit, repositoryConfig);
        if (Deposit.DepositStatus.ACCEPTED != status) {
            return status;
        }

        // list identifiers from OAI-PMH endpoint since the Submission date (ListIdentifiers)
        //   (check error)

        URL listRecords = null;
        String resumptionToken = null;
        List<URL> recordIdentifiers = new ArrayList<>();

        do {
            listRecords = parameterizeGetRecordsRequest(resumptionToken);

            // Headers like Accept, From, User-Agent, and authentication are added by the class that configures the
            // OkHttpClient as interceptors
            try (Response res = oaiClient.newCall(new Request.Builder()
                    .url(listRecords)
                    .build()).execute()) {
                if (res.code() != 200) {
                    throw new RuntimeException(
                            format("Error retrieving %s (code: %s): %s", listRecords, res.code(), res.message()));
                }

                resumptionToken = processResult(res.body().byteStream(), recordIdentifiers);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } while (resumptionToken != null && resumptionToken.trim().length() > 0);

        // For each identifier, issue a GetRecord request
        //   (check error)

        

        // Parse request body for PASS Submission URI

        // Parse request body for Harvard DSpace Item URL (dc.identifier uri beginning 'http://nrs.harvard.edu')

        // Resolve the RepositoryCopy for the Submission, and update its RepositoryCopy status and uri




    }

    private URL parameterizeGetRecordsRequest(String resumptionToken) {
        URL listRecords;
        try {
            UriTemplate t = UriTemplate.fromTemplate(LIST_IDENTIFIERS_TEMPLATE)
                    .set("oaiBaseUrl", oaiBaseUrl)
                    .set("verb", LIST_IDENTIFIERS)
                    .set("metadataPrefix", DIM_METADATA_PREFIX)
                    .set("from", "");

            if (resumptionToken != null && resumptionToken.trim().length() > 0) {
                t.set("resumptionToken", resumptionToken);
            }

            listRecords = new URL(t.expand());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return listRecords;
    }

    /**
     * Processes a batch of results from OAI-PMH: if a valid response, populate records.  If response is empty (an
     * OAI-PMH "no matching records" error) do nothing.  Otherwise throw a RuntimeException with the error message from
     * the response.  Return a resumption token if present, otherwise {@code null}.
     *
     * @param result the OAI-PMH response body
     * @param records the List of URLs to populate using the records from the response body
     * @return String resumptionToken, may be {@code null}
     */
    String processResult(InputStream result, List<URL> records) {

    }

}
