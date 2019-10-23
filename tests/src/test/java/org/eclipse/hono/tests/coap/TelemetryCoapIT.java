/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Integration tests for uploading telemetry data to the CoAP adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TelemetryCoapIT extends CoapTestBase {

    private static final String POST_URI = "/" + TelemetryConstants.TELEMETRY_ENDPOINT;
    private static final String PUT_URI_TEMPLATE = POST_URI + "/%s/%s";

    /**
     * Time out each test after 20 seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.millis(TEST_TIMEOUT_MILLIS);

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.applicationClientFactory.createTelemetryConsumer(tenantId, messageConsumer, remoteClose -> {});
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
    public void testUploadUsingQoS1(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final Tenant tenant = new Tenant();

        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET)
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);

        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapsRequest(Code.POST, Type.CON, getPostResource(), 0)),
                count -> {
            final Promise<OptionSet> result = Promise.promise();
            final Request request = createCoapsRequest(Code.POST, Type.CON, getPostResource(), count);
            client.advanced(getHandler(result), request);
            return result.future();
        });
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
    public void testUploadLargePayloadFails(final TestContext ctx) throws ConnectorException, IOException {

        final Async setup = ctx.async();
        final Tenant tenant = new Tenant();

        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET)
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
        final Request request = createCoapsRequest(Code.POST, Type.CON, getPostResource(), IntegrationTestSupport.getPayload(4096));
        final CoapResponse response = client.advanced(request);
        assertThat(response.getCode()).isEqualTo(ResponseCode.REQUEST_ENTITY_TOO_LARGE);
    }

}
