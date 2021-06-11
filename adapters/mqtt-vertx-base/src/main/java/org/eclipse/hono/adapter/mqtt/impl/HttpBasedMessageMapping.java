/*******************************************************************************
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
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.config.MapperEndpoint;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * A message mapper that invokes a service implementation via HTTP(S).
 * <p>
 * The headers are overwritten with the result of the mapper (which includes the resourceId).
 * E.g.: when the deviceId is in the payload of the message, the deviceId can be deducted in the custom mapper and
 * the payload can be changed accordingly to the payload originally received by the gateway.
 */
public final class HttpBasedMessageMapping implements MessageMapping<MqttContext> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpBasedMessageMapping.class);

    private final WebClient webClient;
    private final MqttProtocolAdapterProperties mqttProtocolAdapterProperties;

    /**
     * Creates a new service for a web client and configuration properties.
     *
     * @param webClient The web client to use for invoking the mapper endpoint.
     * @param protocolAdapterConfig The configuration properties of the MQTT protocol
     *                              adapter used to look up mapper configurations.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public HttpBasedMessageMapping(
            final WebClient webClient,
            final MqttProtocolAdapterProperties protocolAdapterConfig) {

        this.webClient = Objects.requireNonNull(webClient);
        this.mqttProtocolAdapterProperties = Objects.requireNonNull(protocolAdapterConfig);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation tries to look up the URL of the service endpoint to invoke in the
     * adapter's <em>mapperEndpoints</em> using the value of the registration assertion's
     * <em>mapper</em> property as the key.
     * If a mapping endpoint configuration is found, an HTTP GET request is sent to the endpoint
     * containing the original message's
     * <ul>
     * <li>payload in the request body,</li>
     * <li>content type in the HTTP content-type header,</li>
     * <li>topic name in the {@value MessageHelper#APP_PROPERTY_ORIG_ADDRESS} header and</li>
     * <li>all properties from the registration assertion as headers.</li>
     * </ul>
     *
     * @return A future indicating the mapping result.
     *         The future will be succeeded with the original unaltered message if no mapping
     *         URL could be found. The future will be succeeded with the message contained in the
     *         response body if the returned status code is 200.
     *         Otherwise, the future will be failed with a {@link ServerErrorException}.
     */
    @Override
    public Future<MappedMessage> mapDownstreamMessage(
            final MqttContext ctx,
            final ResourceIdentifier targetAddress,
            final RegistrationAssertion registrationInfo) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(registrationInfo);

        final Promise<MappedMessage> result = Promise.promise();
        final String mapper = registrationInfo.getDownstreamMessageMapper();

        if (Strings.isNullOrEmpty(mapper)) {
            LOG.debug("no payload mapping configured for {}", ctx.authenticatedDevice());
            result.complete(new MappedMessage(targetAddress, ctx.message().payload()));
        } else {
            final MapperEndpoint mapperEndpoint = mqttProtocolAdapterProperties.getMapperEndpoint(mapper);
            if (mapperEndpoint == null) {
                LOG.debug("no mapping endpoint [name: {}] found for {}", mapper, ctx.authenticatedDevice());
                result.complete(new MappedMessage(targetAddress, ctx.message().payload()));
            } else {
                mapDownstreamMessageRequest(ctx, targetAddress, registrationInfo, mapperEndpoint, result);
            }
        }

        return result.future();
    }

    @Override
    public Future<Buffer> mapUpstreamMessage(final RegistrationAssertion registrationInfo, final Command command) {
        Objects.requireNonNull(registrationInfo);
        Objects.requireNonNull(command);

        final Promise<Buffer> result = Promise.promise();
        final String mapper = registrationInfo.getUpstreamMessageMapper();

        if (Strings.isNullOrEmpty(mapper)) {
            LOG.debug("no payload mapping configured for {}", registrationInfo.getDeviceId());
            result.complete(command.getPayload());
        } else {
            final MapperEndpoint mapperEndpoint = mqttProtocolAdapterProperties.getMapperEndpoint(mapper);
            if (mapperEndpoint == null) {
                LOG.debug("no mapping endpoint [name: {}] found for {}", mapper, registrationInfo.getDeviceId());
                result.complete(command.getPayload());
            } else {
                mapUpstreamMessageRequest(command, registrationInfo, mapperEndpoint, result);
            }
        }

        return result.future();
    }

    private void mapUpstreamMessageRequest(
        final Command command,
        final RegistrationAssertion registrationInfo,
        final MapperEndpoint mapperEndpoint,
        final Handler<AsyncResult<Buffer>> resultHandler) {

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
        if (command.getGatewayId() != null) {
            headers.add(MessageHelper.APP_PROPERTY_GATEWAY_ID, command.getGatewayId());
        }

        if (command.getDeviceId() != null) {
            headers.add(MessageHelper.APP_PROPERTY_DEVICE_ID, command.getDeviceId());
        }

        if (command.getContentType() != null) {
            headers.add(HttpHeaders.CONTENT_TYPE.toString(), command.getContentType());
        }

        final Promise<Buffer> result = Promise.promise();

        webClient.post(mapperEndpoint.getPort(), mapperEndpoint.getHost(), mapperEndpoint.getUri())
            .putHeaders(headers)
            .ssl(mapperEndpoint.isTlsEnabled())
            .sendBuffer(command.getPayload(), httpResponseAsyncResult -> {
                if (httpResponseAsyncResult.failed()) {
                    LOG.debug("failed to map message [origin: {}] using mapping service [host: {}, port: {}, URI: {}]",
                        command.getDeviceId(),
                        mapperEndpoint.getHost(), mapperEndpoint.getPort(), mapperEndpoint.getUri(),
                        httpResponseAsyncResult.cause());
                    result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, httpResponseAsyncResult.cause()));
                } else {
                    final HttpResponse<Buffer> httpResponse = httpResponseAsyncResult.result();
                    if (httpResponse.statusCode() == HttpURLConnection.HTTP_OK) {
                        result.complete(httpResponse.bodyAsBuffer());
                    } else {
                        LOG.debug("mapping service [host: {}, port: {}, URI: {}] returned unexpected status code: {}",
                            mapperEndpoint.getHost(), mapperEndpoint.getPort(), mapperEndpoint.getUri(),
                            httpResponse.statusCode());
                        result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                            "could not invoke configured mapping service"));
                    }
                }
                resultHandler.handle(result.future());
            });
    }

    private void mapDownstreamMessageRequest(
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
        if (ctx.contentType() != null) {
            headers.add(HttpHeaders.CONTENT_TYPE.toString(), ctx.contentType());
        }

        final Promise<MappedMessage> result = Promise.promise();

        webClient.post(mapperEndpoint.getPort(), mapperEndpoint.getHost(), mapperEndpoint.getUri())
            .putHeaders(headers)
            .ssl(mapperEndpoint.isTlsEnabled())
            .sendBuffer(ctx.message().payload(), httpResponseAsyncResult -> {
                if (httpResponseAsyncResult.failed()) {
                    LOG.debug("failed to map message [origin: {}] using mapping service [host: {}, port: {}, URI: {}]",
                            ctx.authenticatedDevice(),
                            mapperEndpoint.getHost(), mapperEndpoint.getPort(), mapperEndpoint.getUri(),
                            httpResponseAsyncResult.cause());
                    result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, httpResponseAsyncResult.cause()));
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
                        LOG.debug("mapping service [host: {}, port: {}, URI: {}] returned unexpected status code: {}",
                                mapperEndpoint.getHost(), mapperEndpoint.getPort(), mapperEndpoint.getUri(),
                                httpResponse.statusCode());
                        result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                                "could not invoke configured mapping service"));
                    }
                }
                resultHandler.handle(result.future());
            });
    }
}
