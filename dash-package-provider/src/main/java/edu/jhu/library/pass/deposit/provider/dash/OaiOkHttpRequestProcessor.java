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
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static edu.jhu.library.pass.deposit.provider.dash.OaiUrlBuilder.DIM_METADATA_PREFIX;
import static edu.jhu.library.pass.deposit.provider.dash.OaiUrlBuilder.GET_RECORD;
import static edu.jhu.library.pass.deposit.provider.dash.OaiUrlBuilder.LIST_IDENTIFIERS;
import static java.lang.String.format;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
class OaiOkHttpRequestProcessor implements OaiRequestProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(OaiOkHttpRequestProcessor.class);

    private OkHttpClient oaiClient;

    private OaiUrlBuilder urlBuilder;

    private OaiResponseBodyProcessor responseProcessor;

    @Value("${pass.deposit.oaiclient.dash.request-throttle-ms}")
    private long requestThrottleMs;

    private Throttler throttler = (reqMeta) -> {
        try {
            LOG.debug("Sleeping {} ms between requests.  Next request: {}", requestThrottleMs, reqMeta);
            LOG.debug("Connection pool size: {} in use: {} idle: {}",
                    oaiClient.connectionPool().connectionCount(),
                    oaiClient.connectionPool().connectionCount() - oaiClient.connectionPool().idleConnectionCount(),
                    oaiClient.connectionPool().idleConnectionCount());
            Thread.sleep(requestThrottleMs);
        } catch (InterruptedException e) {
            // ignore
        }
    };

    @Autowired
    public OaiOkHttpRequestProcessor(@Qualifier("oaiClientFactory") OkHttpClient oaiClient,
                                     OaiUrlBuilder urlBuilder,
                                     OaiResponseBodyProcessor responseProcessor) {
        this.oaiClient = oaiClient;
        this.urlBuilder = urlBuilder;
        this.responseProcessor = responseProcessor;
    }

    public Stream<String> listIdentifiers(Instant from) {
        URL listRecords = null;
        String resumptionToken = null;
        List<String> recordIdentifiers = new ArrayList<>();

        do {
            listRecords = urlBuilder.listIdentifiers(DIM_METADATA_PREFIX, from, resumptionToken);

            // Headers like Accept, From, User-Agent, and authentication are added by the class that configures the
            // OkHttpClient as interceptors
            Request req = new Request.Builder()
                    .url(listRecords)
                    .build();
            OaiRequestMetaImpl reqMeta = new OaiRequestMetaImpl(
                    req, LIST_IDENTIFIERS, from, resumptionToken, DIM_METADATA_PREFIX);

            throttler.throttle(reqMeta);

            LOG.debug("Performing OAI ListRecords Request: {}", reqMeta);

            try (Response res = oaiClient.newCall(req).execute()) {
                if (res.code() != 200) {
                    throw new RuntimeException(
                            format("Error retrieving %s (code: %s): %s", listRecords, res.code(), res.message()));
                }

                resumptionToken = responseProcessor.listIdentifiersResponse(
                        reqMeta, res.body().byteStream(), recordIdentifiers);
            } catch (IOException e) {
                throw new RuntimeException("OAI-PMH client unable to process request: " + reqMeta, e);
            }
        } while (resumptionToken != null && resumptionToken.trim().length() > 0);

        return recordIdentifiers
                .stream()
                .peek(recordId -> {
                    LOG.debug("ListRecords result: {}", recordId);
                });
    }

    public Optional<URL> analyzeRecords(URI submissionUri, Stream<String> oaiRecordIds) {
        return oaiRecordIds
                .map(oaiRecordId -> {
                    URL getRecord = urlBuilder.getRecord(oaiRecordId, DIM_METADATA_PREFIX);

                    Request req = new Request.Builder()
                            .url(getRecord)
                            .build();
                    OaiRequestMetaImpl reqMeta = new OaiRequestMetaImpl(
                            req, GET_RECORD, null, null, DIM_METADATA_PREFIX);

                    throttler.throttle(reqMeta);

                    LOG.debug("Performing OAI GetRecord Request: {}", reqMeta);

                    try (Response res = oaiClient.newCall(req).execute()) {

                        if (res.code() != 200) {
                            throw new RuntimeException(
                                    format("Error retrieving %s (code: %s): %s", getRecord, res.code(), res.message()));
                        }

                        //  Parse request body for PASS Submission URI
                        //  Parse request body for Harvard DSpace Item URL
                        //  (dc.identifier uri beginning 'http://nrs.harvard.edu')

                        try {
                            return responseProcessor.getRecordResponse(
                                    reqMeta,
                                    res.body().byteStream(),
                                    submissionUri);
                        } catch (Exception e) {
                            LOG.warn(String.format("Error processing response for request %s (%s)",
                                    reqMeta.url(), reqMeta), e);
                        }

                        return null;
                    } catch (IOException e) {
                        throw new RuntimeException("OAI-PMH client unable to process request: " + reqMeta, e);
                    }

                })
                .filter(Objects::nonNull)
                .findAny();
    }

    @FunctionalInterface
    interface Throttler {
        void throttle(OaiResponseBodyProcessor.OaiRequestMeta requestMeta);
    }

    static String encode(String token) {
        if (token == null || token.trim().length() == 0) {
            return token;
        }

        char[] illegal = new char[]{
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

        StringBuilder sb = new StringBuilder(token);

        int replacementOffset = 0;

        for (int i = 0, offset = 0; i < token.length(); i++, offset = (i + replacementOffset)) {
            char candidate = token.charAt(i);

            for (int j = 0; j < illegal.length; j++) {
                if (candidate == illegal[j]) {
                    sb.replace(offset, offset + 1, encoding[j]);
                    replacementOffset += encoding[j].length() - 1;
                    break;
                }
            }
        }

        return sb.toString();
    }

    static class OaiRequestMetaImpl implements OaiResponseBodyProcessor.OaiRequestMeta {
        private String method;
        private String url;
        private String verb;
        private Instant from;
        private String resumptionToken;
        private String metadataPrefix;

        private OaiRequestMetaImpl(Request req, String verb) {
            this.verb = verb;
            this.method = req.method();
            this.url = req.url().toString();
        }

        public OaiRequestMetaImpl(Request req, String verb, Instant from, String resumptionToken, String metadataPrefix) {
            this(req, verb);
            this.from = from;
            this.resumptionToken = resumptionToken;
            this.metadataPrefix = metadataPrefix;
        }

        @Override
        public String method() {
            return method;
        }

        @Override
        public String url() {
            return url;
        }

        @Override
        public String verb() {
            return verb;
        }

        @Override
        public Instant from() {
            return null;
        }

        @Override
        public String resumptionToken() {
            return null;
        }

        @Override
        public String metadataPrefix() {
            return null;
        }

        @Override
        public String toString() {
            return new StringJoiner("\n  ", OaiRequestMetaImpl.class.getSimpleName() + "[", "]")
                    .add("method='" + method + "'")
                    .add("url='" + url + "'")
                    .add("verb='" + verb + "'")
                    .add("from=" + from)
                    .add("resumptionToken='" + resumptionToken + "'")
                    .add("metadataPrefix='" + metadataPrefix + "'")
                    .toString();
        }
    }

}
