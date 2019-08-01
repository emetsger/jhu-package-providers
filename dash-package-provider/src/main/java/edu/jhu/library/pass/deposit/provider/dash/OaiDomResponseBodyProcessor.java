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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static edu.jhu.library.pass.deposit.provider.dash.DashUtil.asStream;
import static java.util.Collections.singletonList;

@Component
public class OaiDomResponseBodyProcessor implements OaiResponseBodyProcessor {

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

    private RepositoryCopyLocationAnalyzer analyzer;

    private DocumentBuilderFactory dbf;

    @Value("${pass.fedora.baseurl}")
    private String passBaseUrl;

    @Value("${pass.deposit.provider.dash.copyBaseUrl}")
    private String repoCopyBaseUrl;

    @Autowired
    public OaiDomResponseBodyProcessor(RepositoryCopyLocationAnalyzer analyzer, DocumentBuilderFactory dbf) {
        this.analyzer = analyzer;
        this.dbf = dbf;
    }

    public OaiDomResponseBodyProcessor(RepositoryCopyLocationAnalyzer analyzer,
                                       DocumentBuilderFactory dbf,
                                       String passBaseUrl,
                                       String repoCopyBaseUrl) {
        this.analyzer = analyzer;
        this.dbf = dbf;
        this.passBaseUrl = passBaseUrl;
        this.repoCopyBaseUrl = repoCopyBaseUrl;
    }

    @Override
    public String listIdentifiersResponse(OaiRequestMeta req, InputStream responseBody, List<String> records) {
        Document dom = null;
        try {
            dom = dbf.newDocumentBuilder().parse(responseBody);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (shouldIgnore(dom, req, singletonList(ERROR_CONDITION_NO_RECORDS_MATCH))) {
            return null;
        }

        asStream(dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_IDENTIFIER))
                .forEach(node -> records.add(node.getTextContent()));

        NodeList tokenNode = dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_RESUMPTION_TOKEN);
        if (tokenNode != null && tokenNode.getLength() == 1) {
            return tokenNode.item(0).getTextContent();
        }

        return null;
    }

    @Override
    public URL getRecordResponse(OaiRequestMeta req, InputStream responseBody, URI submissionUri) {
        Document dom = null;
        try {
            dom = dbf.newDocumentBuilder().parse(responseBody);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Node request = dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_REQUEST).item(0);
        OaiMetadata meta = OaiMetadata.forPrefix(request.getAttributes().getNamedItem(OAI_METADATA_PREFIX).getTextContent());

        if (shouldIgnore(dom, req, Collections.emptyList())) {
            return null;
        }

        switch (meta) {
            case DIM:
                Node mdNode = dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_METADATA).item(0);
                Document mdDoc = null;
                try {
                    mdDoc = dbf.newDocumentBuilder().newDocument();
                    mdDoc.appendChild(mdDoc.importNode(mdNode, true));
                } catch (ParserConfigurationException e) {
                    throw new RuntimeException(e);
                }
                return analyzer.analyze(mdDoc, submissionUri,
                        ((mdSchema, element, qualifier, textContent) -> {
                            return textContent.startsWith(passBaseUrl);

                        }),
                        ((mdSchema, element, qualifier, textContent) -> {
                            return textContent.startsWith(repoCopyBaseUrl);
                        }));
            default:
                throw new RuntimeException("Unable to parse OAI metadata: " + meta);
        }
    }

    public String getPassBaseUrl() {
        return passBaseUrl;
    }

    public void setPassBaseUrl(String passBaseUrl) {
        this.passBaseUrl = passBaseUrl;
    }

    public String getRepoCopyBaseUrl() {
        return repoCopyBaseUrl;
    }

    public void setRepoCopyBaseUrl(String repoCopyBaseUrl) {
        this.repoCopyBaseUrl = repoCopyBaseUrl;
    }

    private static boolean shouldIgnore(Document dom, OaiRequestMeta req, Collection<String> toIgnore) {
        return shouldIgnore(dom, req, new AtomicReference<>(), toIgnore);
    }

    private static boolean shouldIgnore(Document dom, OaiRequestMeta req, AtomicReference<Node> node, Collection<String> toIgnore) {
        NodeList errors = null;
        if ((errors = dom.getElementsByTagNameNS(OAI_PMH_NS, OAI_ERROR)) != null && errors.getLength() > 0) {
            if (errors.getLength() == 1
                    && errors.item(0).getAttributes().getNamedItem(OAI_ERROR_CODE) != null
                    && toIgnore.contains(errors.item(0).getAttributes().getNamedItem(OAI_ERROR_CODE).getTextContent())) {
                return true;
            }

            String errorMessage = String.format("OAI-PMH request (%s %s) failed with the following error(s):\n",
                        req.method(), req.url()) +
                    asStream(errors).map(error -> String.format("  Code: %s, Message: %s",
                            error.getAttributes().getNamedItem(OAI_ERROR_CODE), error.getNodeValue()))
                            .collect(Collectors.joining("\n"));
            throw new RuntimeException(errorMessage);
        }

        NodeList response = dom.getElementsByTagNameNS(OAI_PMH_NS, req.verb());

        if (response == null) {
            throw new RuntimeException("Missing expected response element for OAI-PMH verb '" + req.verb() + "'");
        }

        if (response.getLength() != 1) {
            throw new RuntimeException("Unexpected number of response elements for OAI-PMH verb '" + req.verb() +
                    "': expected exactly one <" + req.verb() + "> element, but found " + response.getLength() + " elements");
        }

        node.set(response.item(0));

        return false;
    }
}
