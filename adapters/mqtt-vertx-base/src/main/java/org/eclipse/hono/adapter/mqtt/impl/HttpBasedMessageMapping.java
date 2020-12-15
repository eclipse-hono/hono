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

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.mqtt.MappedMessage;
import org.eclipse.hono.adapter.mqtt.MessageMapping;
import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.MapperEndpoint;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * A message mapper that calls out to a service implementation using HTTP.
 * <p>
 * This component requests mapping from another server over HTTP(S) if this
 * is configured properly. The headers are overwritten with the result of the mapper (which includes the resourceId).
 * E.g.: when the deviceId is in the payload of the message, the deviceId can be deducted in the custom mapper and
 * the payload can be changed accordingly to the payload originally received by the gateway.
 */
public final class HttpBasedMessageMapping implements MessageMapping<MqttContext> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpBasedMessageMapping.class);

    private final WebClient webClient;
    private final MqttProtocolAdapterProperties mqttProtocolAdapterProperties;

    /**
     * Constructs the messageMapping client used to call external/custom messageMapping.
     *
     * @param webClient Vert.x webclient to use in the messageMapping.
     * @param mqttProtocolAdapterProperties The configuration properties of the mqtt protocol adapter used to look up
     *                                     mapper configurations.
     */
    public HttpBasedMessageMapping(
            final WebClient webClient,
            final MqttProtocolAdapterProperties mqttProtocolAdapterProperties) {

        this.webClient = Objects.requireNonNull(webClient);
        this.mqttProtocolAdapterProperties = Objects.requireNonNull(mqttProtocolAdapterProperties);
    }

    @Override
    public Future<MappedMessage> mapMessage(
            final MqttContext ctx,
            final ResourceIdentifier targetAddress,
            final RegistrationAssertion registrationInfo) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(registrationInfo);

        final Promise<MappedMessage> result = Promise.promise();
        final String mapper = registrationInfo.getMapper();

        if (Strings.isNullOrEmpty(mapper)) {
            LOG.debug("no payload mapping configured for {}", ctx.authenticatedDevice());
            result.complete(new MappedMessage(targetAddress, ctx.message().payload()));
        } else {
            final MapperEndpoint mapperEndpoint = mqttProtocolAdapterProperties.getMapperEndpoint(mapper);
            if (mapperEndpoint == null) {
                LOG.debug("no mapping endpoint [name: {}] found for {}", mapper, ctx.authenticatedDevice());
                result.complete(new MappedMessage(targetAddress, ctx.message().payload()));
            } else {
                mapMessageRequest(ctx, targetAddress, registrationInfo, mapperEndpoint, result);
            }
        }

        return result.future();
    }

    private void mapMessageRequest(
            final MqttContext ctx,
            final ResourceIdentifier targetAddress,
            final RegistrationAssertion registrationInfo,
            final MapperEndpoint mapperEndpoint,
            final Handler<AsyncResult<MappedMessage>> resultHandler) {

        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        JsonObject.mapFrom(registrationInfo).forEach(property -> {
            final Object value = property.getValue();
            if (value instanceof String) {
                // prevent strings from being enclosed in quotes
                headers.add(property.getKey(), (String) value);
            } else {
                headers.add(property.getKey(), Json.encode(value));
            }
        });
        headers.add(MessageHelper.APP_PROPERTY_ORIG_ADDRESS, ctx.message().topicName());
        headers.add(HttpHeaders.CONTENT_TYPE.toString(), ctx.contentType());

        final Promise<MappedMessage> result = Promise.promise();

        webClient.post(mapperEndpoint.getPort(), mapperEndpoint.getHost(), mapperEndpoint.getUri())
            .putHeaders(headers)
            .ssl(mapperEndpoint.isTlsEnabled())
            .sendBuffer(ctx.message().payload(), httpResponseAsyncResult -> {
                if (httpResponseAsyncResult.failed()) {
                    LOG.debug("mapping of message published by {} failed", ctx.authenticatedDevice(), httpResponseAsyncResult.cause());
                    result.fail(new ServiceInvocationException(HttpURLConnection.HTTP_INTERNAL_ERROR, httpResponseAsyncResult.cause()));
                } else {
                    final HttpResponse<Buffer> httpResponse = httpResponseAsyncResult.result();
                    if (httpResponse.statusCode() == HttpURLConnection.HTTP_OK) {
                        final Map<String, String> additionalProperties = new HashMap<>();
                        httpResponse.headers().forEach(entry -> additionalProperties.put(entry.getKey(), entry.getValue()));

                        final String mappedDeviceId = Optional.ofNullable(additionalProperties.remove(MessageHelper.APP_PROPERTY_DEVICE_ID))
                                .map(id -> {
                                    LOG.debug("original {} has been mapped to [device-id: {}]", ctx.authenticatedDevice(), id);
                                    return id;
                                })
                                .orElseGet(() -> targetAddress.getResourceId());

                        result.complete(new MappedMessage(
                                ResourceIdentifier.from(targetAddress.getEndpoint(), targetAddress.getTenantId(), mappedDeviceId),
                                httpResponse.bodyAsBuffer(),
                                additionalProperties));
                    } else {
                        if (httpResponse.statusCode() > 200 && httpResponse.statusCode() < 300) {
                            result.complete(new MappedMessage(targetAddress, ctx.message().payload()));
                        } else {
                            result.fail(new ServiceInvocationException(httpResponse.statusCode(),
                                    String.format("Payload mapping failed [DeviceId=%s, StatusCode=%s]",
                                                targetAddress.getResourceId(), httpResponse.statusCode())));
                        }
                    }
                }
                resultHandler.handle(result.future());
            });
    }
}
