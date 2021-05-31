/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.tests.jms;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.jms.JMSException;
import javax.jms.Message;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.DeviceConnectionClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.DeviceConnectionConstants.DeviceConnectionAction;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


/**
 * A JMS based client for interacting with a Device Connection service implementation.
 *
 */
public class JmsBasedDeviceConnectionClient extends JmsBasedRequestResponseServiceClient<JsonObject, RequestResponseResult<JsonObject>> implements DeviceConnectionClient {

    private static final Logger LOG = LoggerFactory.getLogger(JmsBasedDeviceConnectionClient.class);

    /**
     * Creates a new client.
     *
     * @param connection The JMS connection to use.
     * @param clientConfig The client configuration properties.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public JmsBasedDeviceConnectionClient(
            final JmsBasedHonoConnection connection,
            final ClientConfigProperties clientConfig) {

        super(connection, clientConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final SpanContext context) {
        return sendRequest(
                tenantId,
                DeviceConnectionAction.GET_LAST_GATEWAY.getSubject(),
                Map.of(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId),
                null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {
        return sendRequest(
                tenantId,
                DeviceConnectionAction.SET_LAST_GATEWAY.getSubject(),
                Map.of(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId,
                        MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId),
                null)
                .mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> setCommandHandlingAdapterInstance(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final Duration lifespan,
            final SpanContext context) {

        final int lifespanSeconds = lifespan != null && lifespan.getSeconds() <= Integer.MAX_VALUE ? (int) lifespan.getSeconds() : -1;
        return sendRequest(
                tenantId,
                DeviceConnectionAction.SET_CMD_HANDLING_ADAPTER_INSTANCE.getSubject(),
                Map.of(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId,
                        MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId,
                        MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds),
                null)
                .onSuccess(payload -> LOG.debug("successfully set command-handling adapter instance"))
                .onFailure(t -> LOG.error("failed to set command-handling adapter instance", t))
                .mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getCommandHandlingAdapterInstances(
            final String tenantId,
            final String deviceId,
            final List<String> viaGateways,
            final SpanContext context) {

        final JsonObject payload = new JsonObject();
        Optional.ofNullable(viaGateways)
            .ifPresent(list -> payload.put(DeviceConnectionConstants.FIELD_GATEWAY_IDS, new JsonArray(list)));

        return sendRequest(
                tenantId,
                DeviceConnectionAction.GET_CMD_HANDLING_ADAPTER_INSTANCES.getSubject(),
                Map.of(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId),
                payload.toBuffer());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> removeCommandHandlingAdapterInstance(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final SpanContext context) {

        return sendRequest(
                tenantId,
                DeviceConnectionAction.REMOVE_CMD_HANDLING_ADAPTER_INSTANCE.getSubject(),
                Map.of(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId,
                        MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId),
                null)
                .mapEmpty();
    }

    /**
     * Sends a request for an operation.
     *
     * @param tenantId The tenant to send the request for.
     * @param operation The name of the operation to invoke or {@code null} if the message
     *                  should not have a subject.
     * @param applicationProperties Application properties to set on the request message or
     *                              {@code null} if no properties should be set.
     * @param payload Payload to include or {@code null} if the message should have no body.
     * @return A future indicating the outcome of the operation.
     */
    public Future<JsonObject> sendRequest(
            final String tenantId,
            final String operation,
            final Map<String, Object> applicationProperties,
            final Buffer payload) {

        final Future<JmsBasedRequestResponseClient<RequestResponseResult<JsonObject>>> client = JmsBasedRequestResponseClient.forEndpoint(
                connection, DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT, tenantId);
        final Future<Message> requestMessage = createRequestMessage(operation, applicationProperties, payload);

        return CompositeFuture.all(client, requestMessage)
                .compose(ok -> client.result().send(requestMessage.result(), this::getResult))
                .compose(devConResult -> {
                    final Promise<JsonObject> result = Promise.promise();
                    switch (devConResult.getStatus()) {
                        case HttpURLConnection.HTTP_OK:
                        case HttpURLConnection.HTTP_NO_CONTENT:
                            result.complete(devConResult.getPayload());
                            break;
                        default:
                            result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                                    "unsupported response status: " + devConResult.getStatus()));
                    }
                    return result.future();
                });
    }

    /**
     * Creates a request message for the given parameters.
     *
     * @param operation The name of the operation to invoke or {@code null} if the message
     *                  should not have a subject.
     * @param applicationProperties Application properties to set on the request message or
     *                              {@code null} if no properties should be set.
     * @param payload Payload to include or {@code null} if the message should have no body.
     * @return A succeeded future containing the created message or a failed future if there was an exception
     *         creating the message.
     */
    protected Future<Message> createRequestMessage(final String operation,
            final Map<String, Object> applicationProperties, final Buffer payload) {
        try {
            final Message request = createMessage(payload);
            addProperties(request, applicationProperties);
            if  (operation != null) {
                request.setJMSType(operation);
            }
            return Future.succeededFuture(request);
        } catch (final JMSException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, e));
        }
    }

    private Future<RequestResponseResult<JsonObject>> getResult(final Message message) {

        return getPayload(message)
                .map(payload -> {
                    if (payload == null || payload.length() == 0) {
                        return new RequestResponseResult<>(getStatus(message), null, null, null);
                    } else {
                        try {
                            final JsonObject json = payload.toJsonObject();
                            return new RequestResponseResult<>(getStatus(message), json, null, null);
                        } catch (final DecodeException e) {
                            LOG.warn("Device Connection service returned malformed payload", e);
                            throw new ServiceInvocationException(
                                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                                    "Device Connection service returned malformed payload");
                        }
                    }
                });
    }
}
