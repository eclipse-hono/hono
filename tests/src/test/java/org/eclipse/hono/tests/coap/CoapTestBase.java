/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.tests.coap;

import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedSinglePskStore;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.tests.CommandEndpointConfiguration.SubscriberRole;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

/**
 * Base class for CoAP adapter integration tests.
 *
 */
public abstract class CoapTestBase {

    /**
     * The default password of devices.
     */
    protected static final String SECRET = "secret";

        /**
     * A helper for accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected static IntegrationTestSupport helper;

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    /**
     * The vert.x instance.
     */
    protected final Vertx vertx = Vertx.vertx();

    /**
     * The IP address and port of the CoAP adapter's secure endpoint.
     */
    protected InetSocketAddress coapAdapterSecureAddress;
    /**
     * The random tenant identifier created for each test case.
     */
    protected String tenantId;
    /**
     * The random device identifier created for each test case.
     */
    protected String deviceId;

    /**
     * Maps a CoAP response code to the corresponding HTTP status code.
     *
     * @param responseCode The CoAP response code.
     * @return The status code.
     */
    protected static int toHttpStatusCode(final ResponseCode responseCode) {
        int result = 0;
        result += responseCode.codeClass * 100;
        result += responseCode.codeDetail;
        return result;
    }

    /**
     * Creates the endpoint configuration variants for Command &amp; Control scenarios.
     *
     * @return The configurations.
     */
    static Stream<CoapCommandEndpointConfiguration> commandAndControlVariants() {
        return Stream.of(
                new CoapCommandEndpointConfiguration(SubscriberRole.DEVICE),
                new CoapCommandEndpointConfiguration(SubscriberRole.UNAUTHENTICATED_DEVICE),
                new CoapCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES),
                new CoapCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE)
                );
    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo The test meta data.
     * @param ctx The vert.x test context.
     * @throws UnknownHostException if the CoAP adapter's host name cannot be resolved.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo, final VertxTestContext ctx) throws UnknownHostException {

        logger.info("running {}", testInfo.getDisplayName());
        helper = new IntegrationTestSupport(vertx);
        logger.info("using CoAP adapter [host: {}, coap port: {}, coaps port: {}]",
                IntegrationTestSupport.COAP_HOST,
                IntegrationTestSupport.COAP_PORT,
                IntegrationTestSupport.COAPS_PORT);
        coapAdapterSecureAddress = new InetSocketAddress(Inet4Address.getByName(IntegrationTestSupport.COAP_HOST), IntegrationTestSupport.COAPS_PORT);

        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        helper.init().onComplete(ctx.completing());
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     *
     * @param ctx The vert.x context.
     */
    @AfterEach
    public void deleteObjects(final VertxTestContext ctx) {
        helper.deleteObjects(ctx);
    }

    /**
     * Closes the AMQP 1.0 Messaging Network client.
     *
     * @param ctx The Vert.x test context.
     */
    @AfterEach
    public void disconnect(final VertxTestContext ctx) {
        helper.disconnect().onComplete(ctx.completing());
    }

    /**
     * Creates the client to use for uploading data to the insecure endpoint
     * of the CoAP adapter.
     *
     * @return The client.
     */
    protected CoapClient getCoapClient() {
        return new CoapClient();
    }

    /**
     * Creates the client to use for uploading data to the secure endpoint
     * of the CoAP adapter.
     *
     * @param deviceId The device to add a shared secret for.
     * @param tenant The tenant that the device belongs to.
     * @param sharedSecret The secret shared with the CoAP server.
     * @return The client.
     */
    protected CoapClient getCoapsClient(final String deviceId, final String tenant, final String sharedSecret) {
        return getCoapsClient(new AdvancedSinglePskStore(
                IntegrationTestSupport.getUsername(deviceId, tenant),
                sharedSecret.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Creates the client to use for uploading data to the secure endpoint
     * of the CoAP adapter.
     *
     * @param pskStoreToUse The store to retrieve shared secrets from.
     * @return The client.
     */
    protected CoapClient getCoapsClient(final AdvancedPskStore pskStoreToUse) {

        final DtlsConnectorConfig.Builder dtlsConfig = new DtlsConnectorConfig.Builder();
        // listen on wildcard to support non-localhost docker daemons
        dtlsConfig.setAddress(new InetSocketAddress(0));
        dtlsConfig.setAdvancedPskStore(pskStoreToUse);
        dtlsConfig.setMaxRetransmissions(1);
        final CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
        builder.setNetworkConfig(NetworkConfig.createStandardWithoutFile());
        builder.setConnector(new DTLSConnector(dtlsConfig.build()));
        return new CoapClient().setEndpoint(builder.build());
    }

    /**
     * Perform additional checks on a received message.
     * <p>
     * This default implementation does nothing. Subclasses should override this method to implement
     * reasonable checks.
     *
     * @param msg The message to perform checks on.
     * @throws RuntimeException if any of the checks fail.
     */
    protected void assertAdditionalMessageProperties(final DownstreamMessage<? extends MessageContext> msg) {
        // empty
    }

    /**
     * Gets a handler for CoAP responses.
     *
     * @param responseHandler The handler to invoke with the outcome of the request. The handler will be invoked with a
     *            succeeded result if the response contains a 2.04 (Changed) code. Otherwise it will be invoked with a
     *            result that is failed with a {@link CoapResultException}.
     * @return The handler.
     */
    protected final CoapHandler getHandler(final Handler<AsyncResult<OptionSet>> responseHandler) {
        return getHandler(responseHandler, ResponseCode.CHANGED);
    }

    /**
     * Gets a handler for CoAP responses.
     *
     * @param responseHandler The handler to invoke with the outcome of the request. The handler will be invoked with a
     *            succeeded result if the response contains the expected code. Otherwise it will be invoked with a
     *            result that is failed with a {@link CoapResultException}.
     * @param expectedStatusCode The status code that is expected in the response.
     * @return The handler.
     */
    protected final CoapHandler getHandler(final Handler<AsyncResult<OptionSet>> responseHandler, final ResponseCode expectedStatusCode) {
        return new CoapHandler() {

            @Override
            public void onLoad(final CoapResponse response) {
                if (response.getCode() == expectedStatusCode) {
                    logger.debug("=> received {}", Utils.prettyPrint(response));
                    responseHandler.handle(Future.succeededFuture(response.getOptions()));
                } else {
                    logger.warn("expected {} => received {}", expectedStatusCode, Utils.prettyPrint(response));
                    responseHandler.handle(Future.failedFuture(
                            new CoapResultException(toHttpStatusCode(response.getCode()), response.getResponseText())));
                }
            }

            @Override
            public void onError() {
                responseHandler
                        .handle(Future.failedFuture(new CoapResultException(HttpURLConnection.HTTP_UNAVAILABLE)));
            }
        };
    }

    /**
     * Creates a URI for a resource that uses the <em>coap</em> scheme.
     *
     * @param resource The resource path.
     * @return The URI.
     */
    protected final URI getCoapRequestUri(final String resource) {

        return getRequestUri("coap", resource, null);
    }

    /**
     * Creates a URI for a resource that uses the <em>coaps</em> scheme.
     *
     * @param resource The resource path.
     * @return The URI.
     */
    protected final URI getCoapsRequestUri(final String resource) {

        return getRequestUri("coaps", resource, null);
    }

    /**
     * Creates a URI for a resource and query parameters.
     *
     * @param scheme The URI scheme to use.
     * @param resource The resource path.
     * @param query The query parameters or {@code null}.
     * @return The URI.
     * @throws NullPointerException if scheme or resource are {@code null}.
     * @throws IllegalArgumentException if scheme is neither <em>coap</em> nor <em>coaps</em>.
     */
    protected URI getRequestUri(final String scheme, final String resource, final String query) {

        final int port;
        switch (scheme) {
        case "coap": 
            port = IntegrationTestSupport.COAP_PORT;
            break;
        case "coaps": 
            port = IntegrationTestSupport.COAPS_PORT;
            break;
        default:
            throw new IllegalArgumentException();
        }
        try {
            return new URI(scheme, null, IntegrationTestSupport.COAP_HOST, port, resource, query, null);
        } catch (final URISyntaxException e) {
            // cannot happen
            return null;
        }
    }
}
