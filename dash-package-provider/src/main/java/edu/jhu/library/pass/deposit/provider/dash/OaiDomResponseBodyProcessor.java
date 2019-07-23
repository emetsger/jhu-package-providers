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

import org.apache.tika.io.IOUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Spliterators.AbstractSpliterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.jhu.library.pass.deposit.provider.dash.OaiUrlBuilder.GET_RECORD;
import static edu.jhu.library.pass.deposit.provider.dash.OaiUrlBuilder.LIST_IDENTIFIERS;
import static java.util.Collections.singletonList;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static java.util.stream.StreamSupport.stream;

@Component
public class OaiDomResponseBodyProcessor implements OaiResponseBodyProcessor {

    static final String HARVARD_NRS_URL_REGEX = "http://nrs.harvard.edu";

    static final Pattern HARVARD_REPOCOPY_PATTERN = Pattern.compile("^" + HARVARD_NRS_URL_REGEX);

    private static final String ERROR_CONDITIONS = "https://www.openarchives.org/OAI/openarchivesprotocol.html#ErrorConditions";

    private static final String ERROR_CONDITION_NO_RECORDS_MATCH = "noRecordsMatch";

    private static final String ERROR_CONDITION_ID_DOES_NOT_EXIST = "idDoesNotExist";

    private static final String OAI_ERROR = "error";

    private static final String OAI_ERROR_CODE = "code";

    private static final String OAI_RESUMPTION_TOKEN = "resumptionToken";

    private static final String OAI_IDENTIFIER = "identifier";

    private static final String OAI_METADATA = "metadata";

    private static final String OAI_REQUEST = "request";

    private static final String OAI_METADATA_PREFIX = "metadataPrefix";

    private static final String OAI_PMH_NS = "http://www.openarchives.org/OAI/2.0/";

    private Matcher repoCopyUriMatcher;

    private DocumentBuilderFactory dbf;

    @Override
    public String listIdentifiersResponse(InputStream response, List<String> records) {
        Document dom = null;
        try {
            dom = dbf.newDocumentBuilder().parse(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        AtomicReference<Node> result = new AtomicReference<>();
        if (shouldIgnore(dom, LIST_IDENTIFIERS, result, singletonList(ERROR_CONDITION_NO_RECORDS_MATCH))) {
            return null;
        }

        asStream(dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_IDENTIFIER))
                .forEach(node -> records.add(node.getTextContent()));

        return dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_RESUMPTION_TOKEN).item(0).getTextContent();
    }

    @Override
    public URL getRecordResponse(InputStream response, URI submissionUri) {
        Document dom = null;
        try {
            dom = dbf.newDocumentBuilder().parse(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Node request = dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_REQUEST).item(0);
        OaiMetadata meta = OaiMetadata.forPrefix(request.getAttributes().getNamedItem(OAI_METADATA_PREFIX).getTextContent());

        AtomicReference<Node> result = new AtomicReference<>();
        if (shouldIgnore(dom, GET_RECORD, result, Collections.emptyList())) {
            return null;
        }

        InputStream metadataDom = IOUtils.toInputStream(
                        dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_METADATA).item(0).getTextContent());

        switch (meta) {
            case DIM:

                break;

            default:
                throw new RuntimeException("Unable to parse OAI metadata: " + meta);
        }


    }

    private static Stream<Node> asStream(NodeList nodeList) {
        int characteristics = SIZED | ORDERED;
        Stream<Node> nodeStream = stream(new AbstractSpliterator<Node>(nodeList.getLength(), characteristics) {
            int index = 0;

            @Override
            public boolean tryAdvance(Consumer<? super Node> action) {
                if (nodeList.getLength() == index) {
                    return false;
                }

                action.accept(nodeList.item(index++));

                return true;
            }
        }, false);

        return nodeStream;
    }

    private static boolean shouldIgnore(Document dom, String verb, AtomicReference<Node> node, Collection<String> toIgnore) {
        NodeList errors = null;
        if ((errors = dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_ERROR)) != null) {
            if (errors.getLength() == 1
                    && errors.item(0).getAttributes().getNamedItem(OAI_ERROR_CODE) != null
                    && toIgnore.contains(errors.item(0).getAttributes().getNamedItem(OAI_ERROR_CODE).getTextContent())) {
                return true;
            }

            String errorMessage = "OAI-PMH request failed with the following error(s):\n" +
                    asStream(errors).map(error -> String.format("\n  Code: %s, Message: %s\n",
                            error.getAttributes().getNamedItem(OAI_ERROR_CODE), error.getTextContent()))
                            .collect(Collectors.joining(", "));
            throw new RuntimeException(errorMessage);
        }

        NodeList response = dom.getElementsByTagNameNS(OAI_PMH_NS, verb);

        if (response == null) {
            throw new RuntimeException("Missing expected response element for OAI-PMH verb '" + verb + "'");
        }

        if (response.getLength() != 1) {
            throw new RuntimeException("Unexpected number of response elements for OAI-PMH verb '" + verb +
                    "': expected exactly one <" + verb + ">, but found " + response.getLength());
        }

        node.set(response.item(0));

        return false;
    }
}
