/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.standalone;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.definition.model.Endpoint;
import io.gravitee.definition.model.ssl.TrustStore;
import io.gravitee.definition.model.endpoint.HttpEndpoint;
import io.gravitee.definition.model.ssl.pem.PEMTrustStore;
import io.gravitee.gateway.handlers.api.definition.Api;
import io.gravitee.gateway.standalone.junit.annotation.ApiConfiguration;
import io.gravitee.gateway.standalone.junit.annotation.ApiDescriptor;
import io.gravitee.gateway.standalone.junit.rules.ApiDeployer;
import io.gravitee.gateway.standalone.junit.rules.ApiPublisher;
import io.gravitee.gateway.standalone.servlet.TeamServlet;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 * @author GraviteeSource Team
 */
@ApiDescriptor("/io/gravitee/gateway/standalone/ssl-pem-support.json")
@ApiConfiguration(
        servlet = TeamServlet.class,
        contextPath = "/team"
)
@Ignore
public class SSLPEMTrustStoreTest extends AbstractGatewayTest {

    // P12 has been generated from SSLPKCS12TrustStoreTest

    // openssl pkcs12 -in keystore.p12 -nodes -passin pass:password | openssl pkcs8 -topk8 -inform PEM -outform PEM -out server-key.pem -nocrypt
    // openssl rsa -inform PEM -outform PEM -in server-key.pem -out key-pkcs1.pem
    // openssl pkcs12 -in keystore.p12 -nokeys -passin pass:password -out server-cert.pem

    private WireMockRule wireMockRule = new WireMockRule(wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort()
            .keystorePath(SSLPEMTrustStoreTest.class.getResource("/io/gravitee/gateway/standalone/keystore01.jks").getPath())
            .keystorePassword("password"));

    @Rule
    public final TestRule chain = RuleChain
            .outerRule(new ApiPublisher())
            .outerRule(wireMockRule)
            .around(new ApiDeployer(this));

    @Test
    public void simple_request_ssl() throws Exception {
        stubFor(get(urlEqualTo("/team/my_team"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SC_OK)
                        .withBody("{\"key\": \"value\"}")));

        // First call is calling an endpoint where trustAll is defined to true, no need for truststore => 200
        Request request = Request.Get("http://localhost:8082/test/my_team");
        Response response = request.execute();
        HttpResponse returnResponse = response.returnResponse();
        assertEquals(HttpStatus.SC_OK, returnResponse.getStatusLine().getStatusCode());

        // Second call is calling an endpoint where trustAll is defined to false, without truststore => 502
        request = Request.Get("http://localhost:8082/test/my_team");
        response = request.execute();
        returnResponse = response.returnResponse();
        assertEquals(HttpStatus.SC_BAD_GATEWAY, returnResponse.getStatusLine().getStatusCode());

        // Third call is calling an endpoint where trustAll is defined to false, with truststore => 200
        request = Request.Get("http://localhost:8082/test/my_team");
        response = request.execute();
        returnResponse = response.returnResponse();
        assertEquals(HttpStatus.SC_OK, returnResponse.getStatusLine().getStatusCode());

        // Check that the stub has been successfully invoked by the gateway
        verify(2, getRequestedFor(urlEqualTo("/team/my_team")));
    }

    @Override
    public void before(Api api) {
        super.before(api);

        try {
            for (Endpoint endpoint : api.getProxy().getGroups().iterator().next().getEndpoints()) {
                URL target = new URL(endpoint.getTarget());
                URL newTarget = new URL(target.getProtocol(), target.getHost(), wireMockRule.httpsPort(), target.getFile());
                endpoint.setTarget(newTarget.toString());

                HttpEndpoint httpEndpoint = (HttpEndpoint) endpoint;
                if (httpEndpoint.getHttpClientSslOptions() != null && httpEndpoint.getHttpClientSslOptions().getTrustStore() != null) {
                    PEMTrustStore trustStore = (PEMTrustStore) httpEndpoint.getHttpClientSslOptions().getTrustStore();
                    trustStore.setPath(SSLPEMTrustStoreTest.class.getResource("/io/gravitee/gateway/standalone/server-cert.pem").getPath());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
