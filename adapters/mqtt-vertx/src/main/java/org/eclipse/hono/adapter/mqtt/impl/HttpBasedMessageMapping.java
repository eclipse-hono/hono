/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter.mqtt.impl;

import java.util.Objects;

import org.eclipse.hono.adapter.mqtt.MqttConstants;
import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.config.MapperEndpoint;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.impl.MqttPublishMessageImpl;

/**
 * A Vert.x based message mapping component. This component requests mapping from another server over HTTP(S) if this
 * is configured properly. The headers are overwritten with the result of the mapper (which includes the resourceId).
 * E.g.: when the deviceId is in the payload of the message, the deviceId can be deducted in the custom mapper and
 * the payload can be changed accordingly to the payload originally received by the gateway.
 */
public final class HttpBasedMessageMapping implements MessageMapping {

    private static final Logger log = LoggerFactory.getLogger(HttpBasedMessageMapping.class);

    private final WebClient webClient;
    private final MqttProtocolAdapterProperties mqttProtocolAdapterProperties;

    /**
     * Constructs the messageMapping client used to call external/custom messageMapping.
     *
     * @param webClient Vert.x webclient to use in the messageMapping.
     * @param mqttProtocolAdapterProperties The configuration properties of the mqtt protocol adapter used to look up
     *                                     mapper configurations.
     */
    public HttpBasedMessageMapping(final WebClient webClient, final MqttProtocolAdapterProperties mqttProtocolAdapterProperties) {
        Objects.requireNonNull(webClient);
        Objects.requireNonNull(mqttProtocolAdapterProperties);
        this.webClient = webClient;
        this.mqttProtocolAdapterProperties = mqttProtocolAdapterProperties;
    }

    /**
     * Fetches the mapper if configured and calls the external mapping service.
     *
     * @param ctx The mqtt context.
     * @param targetAddress The resourceIdentifier with the current targetAddress.
     * @param message Received message.
     * @param registrationInfo information retrieved from the device registry.
     * @return Mapped message
     */
    @Override
    public Future<MappedMessage> mapMessage(final MqttContext ctx, final ResourceIdentifier targetAddress,
                                            final MqttPublishMessage message, final JsonObject registrationInfo) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(targetAddress);
        Objects.requireNonNull(message);
        Objects.requireNonNull(registrationInfo);
        final Promise<MappedMessage> result = Promise.promise();
        if (!registrationInfo.containsKey(RegistrationConstants.FIELD_MAPPER)) {
            result.complete(new MappedMessage(ctx, targetAddress, message));
            return result.future();
        }

        final Object mapperObject = registrationInfo.getValue(RegistrationConstants.FIELD_MAPPER);
        if (!(mapperObject instanceof String)) {
            log.debug("Mapper configuration is a string. Not requesting mapping");
            result.complete(new MappedMessage(ctx, targetAddress, message));
            return result.future();
        }
        final String mapper = (String) mapperObject;
        if (mapper.isBlank()) {
            result.complete(new MappedMessage(ctx, targetAddress, message));
            return result.future();
        }

        final MapperEndpoint mapperEndpoint = mqttProtocolAdapterProperties.getMapperEndpoint(mapper);
        if (mapperEndpoint == null) {
            result.complete(new MappedMessage(ctx, targetAddress, message));
            return result.future();
        }

        final MultiMap headers = new VertxHttpHeaders();
        registrationInfo.iterator().forEachRemaining(stringObjectEntry -> {
            final Object value = stringObjectEntry.getValue();
            if (value instanceof String) {
                headers.add(stringObjectEntry.getKey(), (String) value);
            } else {
                headers.add(stringObjectEntry.getKey(), Json.encode(value));
            }
        });

        return mapMessageRequest(ctx, targetAddress, message, mapperEndpoint, headers);
    }

    Future<MappedMessage> mapMessageRequest(final MqttContext ctx, final ResourceIdentifier targetAddress,
                                            final MqttPublishMessage message, final MapperEndpoint mapperEndpoint,
                                            final MultiMap headers) {
        final Promise<MappedMessage> result = Promise.promise();
        webClient.post(mapperEndpoint.getPort(), mapperEndpoint.getHost(), mapperEndpoint.getUri())
                .putHeaders(headers)
                .ssl(mapperEndpoint.ssl)
                .sendBuffer(message.payload(), httpResponseAsyncResult -> {
                    if (httpResponseAsyncResult.succeeded()) {
                        final HttpResponse<Buffer> httpResponse = httpResponseAsyncResult.result();
                        if (httpResponse.statusCode() == 200) {
                            final MqttPublishMessageImpl mqttPublishMessage = new MqttPublishMessageImpl(ctx.message().messageId(), ctx.message().qosLevel(),
                                    ctx.message().isDup(), ctx.message().isRetain(), ctx.message().topicName(),
                                    httpResponse.bodyAsBuffer().getByteBuf());
                            final MultiMap responseHeaders = httpResponse.headers();
                            String deviceId = targetAddress.getResourceId();
                            if (responseHeaders.contains(MessageHelper.APP_PROPERTY_DEVICE_ID)) {
                                deviceId = responseHeaders.get(MessageHelper.APP_PROPERTY_DEVICE_ID);
                                log.debug("Received new deviceId from mapper: {}", deviceId);
                                responseHeaders.remove(MessageHelper.APP_PROPERTY_DEVICE_ID);
                            }

                            ctx.put(MqttConstants.MAPPER_DATA, responseHeaders);

                            result.complete(
                                    new MappedMessage(ctx, ResourceIdentifier.from(targetAddress,
                                            targetAddress.getTenantId(), deviceId), mqttPublishMessage)
                            );
                            return;
                        }
                    }
                    log.warn("Mapping failed for device {}", targetAddress.getResourceId());
                    result.complete(new MappedMessage(ctx, targetAddress, message));
                });
        return result.future();
    }
}
