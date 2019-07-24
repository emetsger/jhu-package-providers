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
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
class OaiUrlBuilder {

    static final String GET_RECORD = "GetRecord";

    static final String LIST_IDENTIFIERS = "ListIdentifiers";

    static final String DIM_METADATA_PREFIX = "dim";

    private static final String GET_RECORD_TEMPLATE = "{+oaiBaseUrl}{?verb,metadataPrefix,identifier}";

    private static final String LIST_IDENTIFIERS_TEMPLATE = "{+oaiBaseUrl}{?verb,from,metadataPrefix,resumptionToken}";

    private final DateTimeFormatter utcDate = DateTimeFormatter.ISO_DATE.withZone(ZoneId.of("UTC"));

    private String oaiBaseUrl;

    OaiUrlBuilder(String oaiBaseUrl) {
        this.oaiBaseUrl = oaiBaseUrl;
    }

    URL listIdentifiers(String resumptionToken, Instant from) {
        try {
            UriTemplate t = UriTemplate.fromTemplate(LIST_IDENTIFIERS_TEMPLATE)
                    .set("oaiBaseUrl", oaiBaseUrl)
                    .set("verb", LIST_IDENTIFIERS)
                    .set("metadataPrefix", DIM_METADATA_PREFIX);

            if (from != null) {
                t.set("from", utcDate.format(from));
            }

            if (resumptionToken != null && resumptionToken.trim().length() > 0) {
                t.set("resumptionToken", resumptionToken);
            }

            return new URL(t.expand());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    URL getRecord(String recordId) {
        Objects.requireNonNull(recordId, "OAI-PMH recordId must not be null");
        try {
            UriTemplate t = UriTemplate.fromTemplate(GET_RECORD_TEMPLATE)
                    .set("oaiBaseUrl", oaiBaseUrl)
                    .set("verb", GET_RECORD)
                    .set("identifier", recordId)
                    .set("metadataPrefix", DIM_METADATA_PREFIX);

            return new URL(t.expand());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

}
