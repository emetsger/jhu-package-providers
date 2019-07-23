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

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;

interface OaiResponseBodyProcessor {

    /**
     * Processes a batch of ListIdentifier results from OAI-PMH: if a valid response, populate records.  If response is
     * empty (an OAI-PMH "no matching records" error) do nothing.  Otherwise throw a RuntimeException with the error
     * message from the response.  Return a resumption token if present, otherwise {@code null}.
     *
     * @param response  the OAI-PMH response body
     * @param records the List of OAI-PMH record identifiers to populate using the records from the response body
     * @return String resumptionToken, may be {@code null}
     */
    String listIdentifiersResponse(InputStream response, List<String> records);

    /**
     * Processes a single GetRecord result from OAI-PMH.  If a valid response, populate the returned Map with extracted
     * information.  If response is empty (an OAI-PMH "no matching records" error) do nothing.  Otherwise throw a
     * RuntimeException with the error message from the response.
     *
     * @param response the OAI-PMH response body
     * @return the URL associated with the PASS Submission (e.g. a DSpace Item URL), or {@code null} if none is found
     */
    URL getRecordResponse(InputStream response, URI submissionUri);

    interface MetadataProcessor {
        String process(InputStream metadata, OaiMetadata scheme, URI submissionUri);
    }

}
