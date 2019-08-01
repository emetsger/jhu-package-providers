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

import org.w3c.dom.Document;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.List;

/**
 * Processes OAI-PMH API responses.  The implementation determines the metadata prefix, and must be
 * matched with the appropriate {@link RepositoryCopyLocationAnalyzer}.
 */
interface OaiResponseBodyProcessor {

    /**
     * Processes a batch of ListIdentifier results from OAI-PMH: if a valid response, populate records.  If response is
     * empty (an OAI-PMH "no matching records" error) do nothing.  Otherwise throw a RuntimeException with the error
     * message from the response.  Return a resumption token if present, otherwise {@code null}.
     *
     * @param req OAI-PMH properties of the OAI-PMH request that produced the response body
     * @param responseBody  the OAI-PMH response body
     * @param records the List of OAI-PMH record identifiers to populate using the records from the response body
     * @return String resumptionToken, may be {@code null}
     */
    String listIdentifiersResponse(OaiRequestMeta req, InputStream responseBody, List<String> records);

    /**
     * Processes a single GetRecord result from OAI-PMH.  If a valid response, populate the returned Map with extracted
     * information.  If response is empty (an OAI-PMH "no matching records" error) do nothing.  Otherwise throw a
     * RuntimeException with the error message from the response.
     *
     * @param req OAI-PMH properties of the OAI-PMH request that produced the response body
     * @param responseBody the OAI-PMH response body
     * @return the URL associated with the PASS Submission (e.g. a DSpace Item URL), or {@code null} if none is found
     */
    URL getRecordResponse(OaiRequestMeta req, InputStream responseBody, URI submissionUri);

    /**
     * Processes metadata of an OAI-PMH record, and determines if it contains a URL that pointing to a RepositoryCopy
     * of the Submission.
     */
    @FunctionalInterface
    interface RepositoryCopyLocationAnalyzer {

        /**
         * Parses the supplied metadata for a PASS RepositoryCopy URL.  If the analyzer determines that the metadata
         * represents or describes the PASS Submission, it will return a URL that provides the location of the
         * Submission as described in the metadata.
         *
         * @param metadata a DOM containing the &gt;metadata/&lt; document parsed from an OAI getRecord response
         * @param submissionUri the PASS Submission URI, which may or may not be referenced in the metadata
         * @return the URL associated with the PASS Submission URI which represents a PASS RepositoryCopy
         */
        URL analyze(Document metadata, URI submissionUri, FieldMatcher submissionMatcher, FieldMatcher repoCopyMatcher);
    }

    @FunctionalInterface
    interface FieldMatcher {

        boolean matches(String mdSchema, String element, String qualifier, String textContent);

    }

    /**
     * Encapsulates salient properties of the OAI-PMH request that corresponds to a response body.
     */
    interface OaiRequestMeta {

        /**
         * The HTTP method of the request
         *
         * @return the method
         */
        String method();

        /**
         * The full URL (including query parameters) of the request
         *
         * @return the URL
         */
        String url();

        /**
         * The OAI-PMH request verb
         *
         * @return the request verb
         */
        String verb();

        /**
         * The {@link Instant} used as the value for the OAI-PMH request parameter 'from'
         *
         * @return the {@code Instant} used as the 'from' parameter value
         */
        Instant from();

        /**
         * The OAI-PMH request parameter 'resumptionToken'
         *
         * @return the 'resumptionToken' parameter value
         */
        String resumptionToken();

        /**
         * The OAI-PMH request parameter 'metadataPrefix'
         *
         * @return the 'metadataPrefix' parameter value
         */
        String metadataPrefix();

    }

}
