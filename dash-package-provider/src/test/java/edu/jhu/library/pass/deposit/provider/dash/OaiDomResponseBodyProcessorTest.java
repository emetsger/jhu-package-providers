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
package edu.jhu.library.pass.deposit.provider.dash;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class OaiDomResponseBodyProcessorTest {

    private static final String LIST_IDENTIFIERS_RESPONSE_OK = "oai-listidentifiers-response-ok.xml";

    private static final String LIST_IDENTIFIERS_RESUMPTION = "oai-listidentifiers-response-withresumption-ok.xml";

    private static final String NO_RECORDS_MATCH = "oai-listidentifiers-response-norecordsmatch.xml";

    private static final String BAD_VERB = "oai-listidentifiers-response-badverb.xml";

    private static final String MULTIPLE_ERRORS = "oai-listidentifiers-response-multipleerrors.xml";

    private static final String GET_RECORD_RESPONSE_OK = "oai-getrecord-response-ok.xml";

    private OaiResponseBodyProcessor.RepositoryCopyLocationAnalyzer analyzer;

    private DocumentBuilderFactory dbf;

    private String passBaseUrl;

    private String repoCopyBaseUrl;

    private URI submissionUri;

    private OaiResponseBodyProcessor underTest;

    @Before
    public void setUp() throws Exception {
        analyzer = mock(OaiResponseBodyProcessor.RepositoryCopyLocationAnalyzer.class);
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        passBaseUrl = "https://pass.harvard.edu";
        submissionUri = URI.create(passBaseUrl + "/submissions/submission1234");
        repoCopyBaseUrl = "http://nrs.harvard.edu";
        underTest = new OaiDomResponseBodyProcessor(analyzer, dbf, passBaseUrl, repoCopyBaseUrl);
    }

    /**
     * The supplied List should be populated with the identifiers.  This response has no resumption token.
     */
    @Test
    public void processListIdentifiersResponse() {
        List<String> records = new ArrayList<>();
        InputStream response = this.getClass().getResourceAsStream(LIST_IDENTIFIERS_RESPONSE_OK);

        String resumptionToken = underTest.listIdentifiersResponse(response, records);

        assertEquals(48, records.size());
        assertTrue(records.contains("oai:dash.harvard.edu:1/40998304"));
        assertNull(resumptionToken);
    }

    /**
     * Processing a response that contains a resumption token should return the expected value
     */
    @Test
    public void processListIdentifiersResponseWithResumptionToken() {
        List<String> records = new ArrayList<>();
        InputStream response = this.getClass().getResourceAsStream(LIST_IDENTIFIERS_RESUMPTION);

        String resumptionToken = underTest.listIdentifiersResponse(response, records);

        assertEquals(1, records.size());
        assertTrue(records.contains("oai:dash.harvard.edu:1/40998304"));
        assertEquals("dim/2018-07-23T00:00:00Z///100", resumptionToken);
    }

    /**
     * Processing a response that contains an error other than 'noRecordsMatch' is exceptional
     */
    @Test
    public void processListIdentifiersResponseWithNoRecords() {
        List<String> records = new ArrayList<>();
        InputStream response = this.getClass().getResourceAsStream(BAD_VERB);

        String resumptionToken = null;
        try {
            resumptionToken = underTest.listIdentifiersResponse(response, records);
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("badVerb"));
        }

        assertEquals(0, records.size());
        assertNull(resumptionToken);
    }

    /**
     * Processing a response that contains multiple errors is exceptional, even if one of the errors is 'noRecordsMatch'
     */
    @Test
    public void processListIdentifiersResponseWithMultipleErrors() {
        List<String> records = new ArrayList<>();
        InputStream response = this.getClass().getResourceAsStream(MULTIPLE_ERRORS);

        String resumptionToken = null;
        try {
            resumptionToken = underTest.listIdentifiersResponse(response, records);
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("noRecordsMatch"));
            assertTrue(e.getMessage().contains("badVerb"));
            assertTrue(e.getMessage().contains("moo"));
        }

        assertEquals(0, records.size());
        assertNull(resumptionToken);
    }

    /**
     * A single 'noRecordsMatch' error is not exceptional
     */
    @Test
    public void processListIdentifersResponseWithError() {
        List<String> records = new ArrayList<>();
        InputStream response = this.getClass().getResourceAsStream(NO_RECORDS_MATCH);

        String resumptionToken = underTest.listIdentifiersResponse(response, records);

        assertEquals(0, records.size());
        assertNull(resumptionToken);
    }

    /**
     * The analyzer should be invoked on the metadata from the GetRecord response.
     */
    @Test
    public void processGetRecordResponse() {
        InputStream response = this.getClass().getResourceAsStream(GET_RECORD_RESPONSE_OK);
        URL repoCopyUrl = underTest.getRecordResponse(response, submissionUri);
        assertNull(repoCopyUrl);

        ArgumentCaptor<Document> dimMetadata = ArgumentCaptor.forClass(Document.class);
        verify(analyzer).analyze(dimMetadata.capture(), eq(submissionUri), any(), any());

        Document doc = dimMetadata.getValue();
        Element root = doc.getDocumentElement();
        assertEquals("metadata", root.getTagName());
        assertEquals("http://www.openarchives.org/OAI/2.0/", root.getNamespaceURI());
        Node dim = root.getElementsByTagNameNS("http://www.dspace.org/xmlns/dspace/dim", "dim").item(0);
        assertEquals("http://www.dspace.org/xmlns/dspace/dim", dim.getNamespaceURI());
    }

}