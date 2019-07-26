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

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class OaiClientFactory implements FactoryBean<OkHttpClient> {

    @Value("${pass.deposit.oaiclient.read-timeout-ms}")
    private long readTimeoutMs = 30000;

    @Value("${pass.deposit.oaiclient.connect-timeout-ms}")
    private long connectTimeoutMs = 30000;

    @Value("${pass.deposit.oaiclient.dash.oaiUser}")
    private String oaiUser;

    @Value("${pass.deposit.oaiclient.dash.oaiPassword}")
    private String oaiPassword;

    @Override
    public OkHttpClient getObject() throws Exception {
        // Headers like Accept, From, User-Agent, and authentication are added by the class that configures the
        // OkHttpClient as interceptors
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        builder.readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS);

        builder.addInterceptor(chain -> {
            Request req = chain.request().newBuilder()
                    .addHeader("Accept", "text/xml")
                    .addHeader("User-Agent", "pass/dash-oai")
                    .build();

            return chain.proceed(req);
        });

        if (oaiUser != null) {
            builder.authenticator((route, response) -> {
                if (response.request().header("Authorization") != null) {
                    return null;
                }

                String credential = Credentials.basic(oaiUser, oaiPassword);
                return response.request().newBuilder()
                        .header("Authorization", credential)
                        .build();
            });
        }

        return builder.build();
    }

    @Override
    public Class<?> getObjectType() {
        return OkHttpClient.class;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public String getOaiUser() {
        return oaiUser;
    }

    public void setOaiUser(String oaiUser) {
        this.oaiUser = oaiUser;
    }

    public String getOaiPassword() {
        return oaiPassword;
    }

    public void setOaiPassword(String oaiPassword) {
        this.oaiPassword = oaiPassword;
    }
}
