/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.http;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.function.Consumer;

import io.vertx.core.json.JsonObject;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Integration tests for uploading telemetry data to the HTTP adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TelemetryHttpIT extends HttpTestBase {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final String URI = String.format("/%s", TelemetryConstants.TELEMETRY_ENDPOINT);

    @Override
    protected String getEndpointUri() {
        return URI;
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.honoClient.createTelemetryConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    /**
     * Verifies that a number of telemetry messages uploaded to Hono's HTTP adapter
     * using QoS 1 can be successfully consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadUsingQoS1(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "binary/octet-stream")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, deviceId, password))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_QOS_LEVEL, "1");

        helper.registry.addDeviceForTenant(tenant, deviceId, password).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        testUploadMessages(ctx, tenantId, count -> {
            return httpClient.create(
                    getEndpointUri(),
                    Buffer.buffer("hello " + count),
                    requestHeaders,
                    statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED);
        });
    }

    /**
     * Upload some telemetry messages that signal that the sender stays connected for some time.
     * The consumer sends a command as a response.
     * <p>
     * Verify that the http response header contains a command.
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingBasicAuth2(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "binary/octet-stream")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, deviceId, password))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TIL_DISCONNECT, "1");

        helper.registry.addDeviceForTenant(tenant, deviceId, password).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        testUploadMessages(ctx, tenantId,
                msg -> {
                    assertAllMessageProperties(ctx, msg);
                    final Integer ttd = MessageHelper.getTimeUntilDisconnect(msg);
                    LOGGER.trace("piggy backed telemetry message received: {}, ttd = {}", msg, ttd);
                    ctx.assertNotNull(ttd);
                    final Optional<TimeUntilDisconnectNotification> notificationOpt = TimeUntilDisconnectNotification.fromMessage(msg);
                    ctx.assertTrue(notificationOpt.isPresent());
                    final TimeUntilDisconnectNotification notification = notificationOpt.get();
                    ctx.assertEquals(tenantId, notification.getTenantId());
                    ctx.assertEquals(deviceId, notification.getDeviceId());
                    // now ready to send a command
                    createCommandClientAndSendCommand(notification);
                },
                count -> {
                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            requestHeaders,
                            statusCode -> statusCode == HttpURLConnection.HTTP_OK);
                });
    }

    private void createCommandClientAndSendCommand(final TimeUntilDisconnectNotification notification) {
        helper.honoClient.getOrCreateCommandClient(notification.getTenantId(), notification.getDeviceId()).map(commandClient -> {
            final JsonObject jsonCmd = new JsonObject().put("brightness", (int) (Math.random() * 100));
            // let the commandClient timeout when the notification expires
            commandClient.setRequestTimeout(notification.getMillisecondsUntilExpiry());

            // send the command upstream to the device
            sendCommandToAdapter(commandClient, Buffer.buffer(jsonCmd.encodePrettily()), notification);
            return commandClient;
        }).otherwise(t -> {
            LOGGER.info("Could not create command client : {}", t.getMessage());
            return null;
        });
    }

    private void sendCommandToAdapter(final CommandClient commandClient, final Buffer commandBuffer,
                                      final TimeUntilDisconnectNotification notification) {
        LOGGER.trace("Trying to send command {}", commandBuffer.toString());
        commandClient.sendCommand("setBrightness", commandBuffer).map(result -> {
            LOGGER.trace("Successfully sent command [{}] and received response: [{}]",
                    commandBuffer.toString(), Optional.ofNullable(result).orElse(Buffer.buffer()).toString());
            commandClient.close(v -> {
            });
            return result;
        }).otherwise(t -> {
            LOGGER.debug("Could not send command or did not receive a response : {}", t.getMessage());
            commandClient.close(v -> {
            });
            return (Buffer) null;
        });
    }
}
