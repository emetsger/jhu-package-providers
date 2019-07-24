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

import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.*;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class OaiUrlBuilderTest {

    private static final String BASE_URL = "https://dash.harvard.edu/oai/request";

    private OaiUrlBuilder underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new OaiUrlBuilder(BASE_URL);
    }


    @Test
    public void listIdentifiers() {
        URL actual = underTest.listIdentifiers(null, null);

        assertTrue(actual.toString().startsWith(BASE_URL));
        assertFalse(actual.getQuery().contains("resumptionToken"));
        assertFalse(actual.getQuery().contains("from"));
        assertTrue(actual.getQuery().contains("verb=ListIdentifiers"));
        assertTrue(actual.getQuery().contains("metadataPrefix=dim"));
    }

    @Test
    public void listIdentifiersWithToken() {
        URL actual = underTest.listIdentifiers("moo", null);

        assertTrue(actual.toString().startsWith(BASE_URL));
        assertTrue(actual.getQuery().contains("resumptionToken=moo"));
    }

    @Test
    public void listIdentifiersWithFrom() {
        Instant now = Instant.now();
        String formattedNow = DateTimeFormatter.ISO_DATE.withZone(ZoneId.of("UTC")).format(now);
        URL actual = underTest.listIdentifiers(null, now);

        assertTrue(actual.toString().startsWith(BASE_URL));
        assertTrue(actual.getQuery().contains("from=" + formattedNow));
    }

    @Test
    public void getRecord() {
        URL actual = underTest.getRecord("recordId");
        assertTrue(actual.toString().startsWith(BASE_URL));
        assertTrue(actual.getQuery().contains("verb=GetRecord"));
        assertTrue(actual.getQuery().contains("metadataPrefix=dim"));
        assertTrue(actual.getQuery().contains("identifier=recordId"));
    }

    @Test(expected = NullPointerException.class)
    public void getRecordNullIdentifier() {
        underTest.getRecord(null);
    }
}