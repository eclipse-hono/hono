/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.sdk.gateway.mqtt2amqp;

import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.DownstreamMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.EventMessage;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttEndpoint;

/**
 * A {@link AbstractMqttToAmqpProtocolGateway} implementation for testing purposes. It handles only one device.
 */
class TestMqttToAmqpProtocolGateway extends AbstractMqttToAmqpProtocolGateway {

    public static final String DEVICE_USERNAME = "device-user";
    public static final String DEVICE_PASSWORD = "device-password";
    public static final String TENANT_ID = "the-tenant";
    public static final String DEVICE_ID = "the-device-id";
    public static final Device DEVICE = new Device(TENANT_ID, DEVICE_ID);

    public static final String GW_USERNAME = "gw@tenant2";
    public static final String GW_PASSWORD = "gw-secret";

    public static final JsonObject PAYLOAD = new JsonObject("{\"the-key\": \"the-value\"}");
    public static final String CONTENT_TYPE = "application/json";
    public static final String COMMAND_TOPIC = "the/command/topic";
    public static final String FILTER1 = "topic/FILTER1/#";
    public static final String FILTER2 = "topic/FILTER2/#";
    public static final String FILTER_INVALID = "unknown/#";
    public static final String KEY_COMMAND_PAYLOAD = "command-payload";
    public static final String KEY_SUBJECT = "subject";
    public static final String KEY_REPLY_TO = "reply-to";
    public static final String KEY_CORRELATION_ID = "correlation-id";
    public static final String KEY_MESSAGE_ID = "message-id";
    public static final String KEY_CONTENT_TYPE = "content-type";
    public static final String KEY_APPLICATION_PROPERTIES = "application-properties";
    public static final String KEY_APPLICATION_PROPERTY_TOPIC = "topic";

    private final AtomicBoolean startupComplete = new AtomicBoolean();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicBoolean connectionClosed = new AtomicBoolean();
    private final AmqpAdapterClientFactory amqpAdapterClientFactory;

    private CommandHandler commandHandler;

    TestMqttToAmqpProtocolGateway(final ClientConfigProperties clientConfigProperties,
            final MqttGatewayServerConfig mqttGatewayServerConfig, final Vertx vertx,
            final AmqpAdapterClientFactory amqpAdapterClientFactory) {
        super(clientConfigProperties, mqttGatewayServerConfig, 100);
        this.amqpAdapterClientFactory = amqpAdapterClientFactory;
        super.vertx = vertx;
    }

    /**
     * Checks if the startup completed.
     *
     * @return {@code true} if {@link AbstractMqttToAmqpProtocolGateway#afterStartup(Promise)} has been invoked.
     */
    public boolean isStartupComplete() {
        return startupComplete.get();
    }

    /**
     * Checks if the shutdown has been initiated.
     *
     * @return {@code true} if {@link AbstractMqttToAmqpProtocolGateway#beforeShutdown(Promise)} has been invoked.
     */
    public boolean isShutdownStarted() {
        return shutdownStarted.get();
    }

    /**
     * Checks if the connection to a device has been closed.
     *
     * @return {@code true} if {@link AbstractMqttToAmqpProtocolGateway#onDeviceConnectionClose(MqttEndpoint)} has been
     *         invoked.
     */
    public boolean isConnectionClosed() {
        return connectionClosed.get();
    }

    /**
     * Return the command handler for the test device.
     *
     * @return The command handler that has been created during the establishment of the device connection.
     */
    public CommandHandler getCommandHandler() {
        return commandHandler;
    }

    @Override
    AmqpAdapterClientFactory createTenantClientFactory(final String tenantId,
            final ClientConfigProperties clientConfig) {
        return amqpAdapterClientFactory;
    }

    @Override
    protected Future<Device> authenticateDevice(final String username, final String password,
            final String clientId) {
        if (DEVICE_USERNAME.equals(username) && DEVICE_PASSWORD.equals(password)) {
            return Future.succeededFuture(DEVICE);
        } else {
            return Future.failedFuture("auth failed");
        }
    }

    @Override
    protected boolean isTopicFilterValid(final String topicFilter, final String tenantId, final String deviceId,
            final String clientId) {
        return FILTER1.equals(topicFilter) || FILTER2.equals(topicFilter);
    }

    @Override
    protected Future<DownstreamMessage> onPublishedMessage(final MqttDownstreamContext ctx) {
        final EventMessage message = new EventMessage(ctx.message().payload());
        message.addApplicationProperty(KEY_APPLICATION_PROPERTY_TOPIC, ctx.topic());
        message.setContentType(CONTENT_TYPE);

        return Future.succeededFuture(message);
    }

    @Override
    protected Command onCommandReceived(final MqttCommandContext ctx) {
        final JsonObject payload = new JsonObject();
        payload.put(KEY_COMMAND_PAYLOAD, ctx.getPayload().toJson());
        payload.put(KEY_SUBJECT, ctx.getSubject());
        payload.put(KEY_REPLY_TO, ctx.getReplyTo());
        payload.put(KEY_CORRELATION_ID, ctx.getCorrelationId());
        payload.put(KEY_MESSAGE_ID, ctx.getMessageId());
        payload.put(KEY_CONTENT_TYPE, ctx.getContentType());

        if (ctx.getApplicationProperties() != null) {
            payload.put(KEY_APPLICATION_PROPERTIES, new JsonObject(ctx.getApplicationProperties().getValue()));

        }

        return new Command(COMMAND_TOPIC, FILTER1, payload.toBuffer());
    }

    @Override
    protected Future<Device> authenticateClientCertificate(final X509Certificate deviceCertificate) {
        return Future.succeededFuture(DEVICE);
    }

    @Override
    protected Future<Credentials> provideGatewayCredentials(final String tenantId) {
        return Future.succeededFuture(new Credentials(GW_USERNAME, GW_PASSWORD));
    }

    @Override
    protected void afterStartup(final Promise<Void> startPromise) {
        startupComplete.compareAndSet(false, true);
        super.afterStartup(startPromise);
    }

    @Override
    protected void beforeShutdown(final Promise<Void> stopPromise) {
        shutdownStarted.compareAndSet(false, true);
        super.beforeShutdown(stopPromise);
    }

    @Override
    CommandHandler createCommandHandler(final Device device, final Vertx vertx, final int commandAckTimeout) {
        commandHandler = super.createCommandHandler(device, vertx, commandAckTimeout);
        ;
        return commandHandler;
    }

    @Override
    protected void onDeviceConnectionClose(final MqttEndpoint endpoint) {
        connectionClosed.compareAndSet(false, true);
    }

}
