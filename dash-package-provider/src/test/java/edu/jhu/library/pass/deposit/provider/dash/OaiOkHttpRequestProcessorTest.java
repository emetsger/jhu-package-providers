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

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class OaiOkHttpRequestProcessorTest {

    @Rule
    public MockWebServer webServer = new MockWebServer();

    private OaiUrlBuilder urlBuilder;

    private OaiResponseBodyProcessor processor;

    private OaiOkHttpRequestProcessor underTest;

    @Before
    public void setUp() throws Exception {
        urlBuilder = mock(OaiUrlBuilder.class);
        processor = mock(OaiResponseBodyProcessor.class);

        underTest = new OaiOkHttpRequestProcessor(new OkHttpClient(), urlBuilder, processor);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listIdentifiersNoResumption() throws Exception {
        Instant now = Instant.now();

        URL url = webServer.url("/").url();
        MockResponse res = new MockResponse();
        Buffer buf = new Buffer();
        buf.write("moo".getBytes());
        res.setBody(buf);
        webServer.enqueue(res);

        when(urlBuilder.listIdentifiers(OaiUrlBuilder.DIM_METADATA_PREFIX, now, null))
                .thenReturn(url);
        when(processor.listIdentifiersResponse(any(), any())).thenAnswer(inv -> {
            ((List) inv.getArgument(1)).add("moo");
            ((List) inv.getArgument(1)).add("cow");
            return null;
        });

        Stream<String> ids = underTest.listIdentifiers(now);

        assertNotNull(ids);
        List<String> idList = ids.collect(Collectors.toList());
        assertEquals(2, idList.size());
        assertEquals("moo", idList.get(0));
        assertEquals("cow", idList.get(1));

        verify(urlBuilder).listIdentifiers(OaiUrlBuilder.DIM_METADATA_PREFIX, now, null);
        verify(processor).listIdentifiersResponse(any(), any());
        assertNotNull(webServer.takeRequest());
        assertEquals(1, webServer.getRequestCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listIdentifiersWithResumption() throws Exception {
        Instant now = Instant.now();

        URL url = webServer.url("/").url();

        MockResponse res1 = new MockResponse();
        Buffer buf1 = new Buffer();
        buf1.write("moo".getBytes());
        res1.setBody(buf1);

        MockResponse res2 = new MockResponse();
        Buffer buf2 = new Buffer();
        buf2.write("cow".getBytes());
        res2.setBody(buf2);

        webServer.enqueue(res1);
        webServer.enqueue(res2);

        when(urlBuilder.listIdentifiers(eq(OaiUrlBuilder.DIM_METADATA_PREFIX), eq(now), any()))
                .thenReturn(url);

        when(processor.listIdentifiersResponse(any(), any())).thenAnswer(inv -> {
            ((List) inv.getArgument(1)).add("moo");
            ((List) inv.getArgument(1)).add("cow");
            return "resToken";
        }).thenAnswer(inv -> {
            ((List) inv.getArgument(1)).add("foo");
            ((List) inv.getArgument(1)).add("bar");
            return null;
        });

        Stream<String> ids = underTest.listIdentifiers(now);

        assertNotNull(ids);
        List<String> idList = ids.collect(Collectors.toList());
        assertEquals(4, idList.size());
        assertEquals("moo", idList.get(0));
        assertEquals("bar", idList.get(3));

        verify(urlBuilder).listIdentifiers(OaiUrlBuilder.DIM_METADATA_PREFIX, now, null);
        verify(urlBuilder).listIdentifiers(OaiUrlBuilder.DIM_METADATA_PREFIX, now, "resToken");

        verify(processor, times(2)).listIdentifiersResponse(any(), any());
        assertEquals(2, webServer.getRequestCount());
    }

    @Test
    public void encodeResumptionTokenDate() {
        String token = "10/20/1999";
        String expected = "10%2F20%2F1999";

        assertEquals(expected, OaiOkHttpRequestProcessor.encode(token));
    }

    @Test
    public void encodeResumptionTokenSpace() {
        String token = " 10/20/1999 ";
        String expected = "%2010%2F20%2F1999%20";

        assertEquals(expected, OaiOkHttpRequestProcessor.encode(token));
    }

    @Test
    public void encodeResumptionToken() {
        String token = "dim/2018-07-23T00:00:00Z///100";
        String expected = "dim%2F2018-07-23T00%3A00%3A00Z%2F%2F%2F100";

        assertEquals(expected, OaiOkHttpRequestProcessor.encode(token));
    }
}