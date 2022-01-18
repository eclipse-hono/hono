/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Integration tests for uploading telemetry data to the CoAP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
public class TelemetryCoapIT extends CoapTestBase {

    private static final String POST_URI = "/" + TelemetryConstants.TELEMETRY_ENDPOINT;
    private static final String PUT_URI_TEMPLATE = POST_URI + "/%s/%s";

    @Override
    protected Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {

        return helper.applicationClient
                .createTelemetryConsumer(tenantId, (Handler) messageConsumer, remoteClose -> {});
    }

    @Override
    protected void assertAdditionalMessageProperties(final DownstreamMessage<? extends MessageContext> msg) {
        assertThat(msg.getCreationTime()).isNotNull();
    }

    @Override
    protected String getPutResource(final String tenant, final String deviceId) {
        return String.format(PUT_URI_TEMPLATE, tenant, deviceId);
    }

    @Override
    protected String getPostResource() {
        return POST_URI;
    }

    @Override
    protected Type getMessageType() {
        return Type.NON;
    }

    /**
     * Verifies that a number of telemetry messages uploaded to Hono's CoAP adapter
     * using QoS 1 can be successfully consumed via the AMQP Messaging Network.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadUsingQoS1(final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();

        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET)
        .onComplete(setup.succeedingThenComplete());
        ctx.verify(() -> assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue());

        final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);

        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapsRequest(Code.POST, Type.CON, getPostResource(), 0)),
                null,
                count -> {
                    final Promise<OptionSet> result = Promise.promise();
                    final Request request = createCoapsRequest(Code.POST, Type.CON, getPostResource(), count);
                    client.advanced(getHandler(result), request);
                    return result.future();
                },
                MESSAGES_TO_SEND,
                QoS.AT_LEAST_ONCE);
    }

    /**
     * Verifies that the upload of a telemetry message containing a payload that
     * exceeds the CoAP adapter's configured max payload size fails with a 4.13
     * response code.
     *
     * @param ctx The test context.
     * @throws IOException if the CoAP request cannot be sent to the adapter.
     * @throws ConnectorException  if the CoAP request cannot be sent to the adapter.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadFailsForLargePayload(final VertxTestContext ctx) throws ConnectorException, IOException {

        final Tenant tenant = new Tenant();

        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET)
        .compose(ok -> {
            final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
            final Request request = createCoapsRequest(Code.POST, Type.CON, getPostResource(), IntegrationTestSupport.getPayload(4096));
            final Promise<OptionSet> result = Promise.promise();
            client.advanced(getHandler(result, ResponseCode.REQUEST_ENTITY_TOO_LARGE), request);
            return result.future();
        })
        .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that a number of messages uploaded to Hono's CoAP adapter using TLS_ECDSA based authentication can be
     * successfully consumed via the messaging infrastructure.
     * <p>
     * The device's tenant is being determined using the requested host name contained in the client's SNI TLS
     * extension.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingClientCertificateWithAlias(final VertxTestContext ctx) throws InterruptedException {

        assumeTrue(IntegrationTestSupport.isTrustAnchorGroupsSupported(),
                "device registry does not support trust anchor groups");
        assumeTrue(IntegrationTestSupport.isTenantAliasSupported(),
                "device registry does not support tenant aliases");

        final var clientCertLoader = KeyLoader.fromFiles(vertx, PATH_DEVICE_KEY, PATH_DEVICE_CERT);
        final var clientCert = (X509Certificate) clientCertLoader.getCertificateChain()[0];

        final VertxTestContext setup = new VertxTestContext();

        helper.getCertificate(PATH_CA_CERT)
            .compose(caCert -> helper.registry.addTenant(
                    helper.getRandomTenantId(),
                    Tenants.createTenantForTrustAnchor(caCert).setTrustAnchorGroup("test-group"))
                .map(caCert))
            .compose(caCert -> {
                return helper.registry.addDeviceForTenant(
                        tenantId,
                        Tenants.createTenantForTrustAnchor(caCert)
                            .setTrustAnchorGroup("test-group")
                            .setAlias("test-alias"),
                        deviceId,
                        clientCert);
            })
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final CoapClient client = getCoapsClient(clientCertLoader);

        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapsRequest(
                        Code.POST,
                        getMessageType(),
                        "test-alias." + IntegrationTestSupport.COAP_HOST,
                        getPostResource(),
                        "hello 0".getBytes(StandardCharsets.UTF_8))),
                count -> {
                    final Promise<OptionSet> result = Promise.promise();
                    final String payload = "hello " + count;
                    final Request request = createCoapsRequest(
                            Code.POST,
                            getMessageType(),
                            "test-alias." + IntegrationTestSupport.COAP_HOST,
                            getPostResource(),
                            payload.getBytes(StandardCharsets.UTF_8));
                    client.advanced(getHandler(result), request);
                    return result.future();
                });
    }

}
