/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessage;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.adapter.lora.LoraProtocolAdapterProperties;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;
import org.eclipse.hono.adapter.lora.providers.LoraProvider;
import org.eclipse.hono.adapter.lora.providers.LoraProviderMalformedPayloadException;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.eclipse.hono.service.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.service.auth.device.X509AuthProvider;
import org.eclipse.hono.service.http.HonoBasicAuthHandler;
import org.eclipse.hono.service.http.HonoChainAuthHandler;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.http.X509AuthHandler;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tag;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ChainAuthHandler;


/**
 * A Vert.x based protocol adapter for receiving HTTP push messages from a LoRa provider's network server.
 */
public final class LoraProtocolAdapter extends AbstractVertxBasedHttpProtocolAdapter<LoraProtocolAdapterProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(LoraProtocolAdapter.class);

    private static final String ERROR_MSG_MISSING_OR_UNSUPPORTED_CONTENT_TYPE = "missing or unsupported content-type";
    private static final String ERROR_MSG_INVALID_PAYLOAD = "invalid payload";
    private static final Tag<String> TAG_LORA_DEVICE_ID = new StringTag("lora_device_id");
    private static final Tag<String> TAG_LORA_PROVIDER = new StringTag("lora_provider");

    private final List<LoraProvider> loraProviders = new ArrayList<>();

    private HonoClientBasedAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;
    private HonoClientBasedAuthProvider<SubjectDnCredentials> clientCertAuthProvider;

    /**
     * Sets the LoRa providers that this adapter should support.
     *
     * @param providers The providers.
     * @throws NullPointerException if providers is {@code null}.
     */
    @Autowired(required = false)
    public void setLoraProviders(final List<LoraProvider> providers) {
        Objects.requireNonNull(providers);
        this.loraProviders.clear();
        this.loraProviders.addAll(providers);
    }

    /**
     * Sets the provider to use for authenticating devices based on a username and password.
     * <p>
     * If not set explicitly using this method, a {@code UsernamePasswordAuthProvider} will be created during startup.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public void setUsernamePasswordAuthProvider(final HonoClientBasedAuthProvider<UsernamePasswordCredentials> provider) {
        this.usernamePasswordAuthProvider = Objects.requireNonNull(provider);
    }

    /**
     * Sets the provider to use for authenticating devices based on a client certificate.
     * <p>
     * If not set explicitly using this method, a {@code SubjectDnAuthProvider} will be created during startup.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public void setClientCertAuthProvider(final HonoClientBasedAuthProvider<SubjectDnCredentials> provider) {
        this.clientCertAuthProvider = Objects.requireNonNull(provider);
    }

    @Override
    protected String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_LORA;
    }

    @Override
    protected void addRoutes(final Router router) {

        // the LoraWAN adapter always requires network providers to authenticate
        setupAuthorization(router);

        for (final LoraProvider provider : loraProviders) {
            router.route(HttpMethod.OPTIONS, provider.pathPrefix())
                .handler(this::handleOptionsRoute);

            router.route(provider.acceptedHttpMethod(), provider.pathPrefix())
                .consumes(provider.acceptedContentType())
                .handler(ctx -> this.handleProviderRoute(HttpContext.from(ctx), provider));

            router.route(provider.acceptedHttpMethod(), provider.pathPrefix()).handler(ctx -> {
                LOG.debug("request does not contain content-type header, will return 400 ...");
                handle400(ctx, ERROR_MSG_MISSING_OR_UNSUPPORTED_CONTENT_TYPE);
            });
        }
    }

    private void setupAuthorization(final Router router) {

        final ChainAuthHandler authHandler = new HonoChainAuthHandler();
        authHandler.append(new X509AuthHandler(
                new TenantServiceBasedX509Authentication(getTenantClientFactory(), tracer),
                Optional.ofNullable(clientCertAuthProvider).orElse(
                        new X509AuthProvider(getCredentialsClientFactory(), getConfig(), tracer))));
        authHandler.append(new HonoBasicAuthHandler(
                Optional.ofNullable(usernamePasswordAuthProvider).orElse(
                        new UsernamePasswordAuthProvider(getCredentialsClientFactory(), getConfig(), tracer)),
                getConfig().getRealm(), tracer));

        router.route().handler(authHandler);
    }

    @Override
    protected void customizeDownstreamMessage(final Message downstreamMessage, final HttpContext ctx) {

        MessageHelper.addProperty(downstreamMessage, LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER,
                ctx.get(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER));

        Optional.ofNullable(ctx.get(LoraConstants.APP_PROPERTY_META_DATA))
            .map(LoraMetaData.class::cast)
            .ifPresent(metaData -> {
                Optional.ofNullable(metaData.getFunctionPort())
                    .ifPresent(port -> MessageHelper.addProperty(downstreamMessage, LoraConstants.APP_PROPERTY_FUNCTION_PORT, port));
                final String json = Json.encode(metaData);
                MessageHelper.addProperty(downstreamMessage, LoraConstants.APP_PROPERTY_META_DATA, json);
            });

        Optional.ofNullable(ctx.get(LoraConstants.APP_PROPERTY_ADDITIONAL_DATA))
            .map(JsonObject.class::cast)
            .ifPresent(data -> MessageHelper.addProperty(downstreamMessage, LoraConstants.APP_PROPERTY_ADDITIONAL_DATA, data.encode()));
    }

    void handleProviderRoute(final HttpContext ctx, final LoraProvider provider) {

        LOG.debug("processing request from provider [name: {}, URI: {}", provider.getProviderName(), provider.pathPrefix());
        final Span currentSpan = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx.getRoutingContext()),
                "process message",
                getClass().getSimpleName())
                .start();

        TAG_LORA_PROVIDER.set(currentSpan, provider.getProviderName());
        ctx.put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, provider.getProviderName());

        if (ctx.isDeviceAuthenticated()) {
            final Device gatewayDevice = ctx.getAuthenticatedDevice();
            TracingHelper.setDeviceTags(currentSpan, gatewayDevice.getTenantId(), gatewayDevice.getDeviceId());

            try {
                final LoraMessage loraMessage = provider.getMessage(ctx.getRoutingContext());
                final LoraMessageType type = loraMessage.getType();
                currentSpan.log(Map.of("message type", type));
                final String deviceId = loraMessage.getDevEUIAsString();
                currentSpan.setTag(TAG_LORA_DEVICE_ID, deviceId);

                switch (type) {
                case UPLINK:
                    final UplinkLoraMessage uplinkMessage = (UplinkLoraMessage) loraMessage;
                    final Buffer payload = uplinkMessage.getPayload();

                    Optional.ofNullable(uplinkMessage.getMetaData())
                        .ifPresent(metaData -> ctx.put(LoraConstants.APP_PROPERTY_META_DATA, metaData));

                    Optional.ofNullable(uplinkMessage.getAdditionalData())
                            .ifPresent(additionalData -> ctx.put(LoraConstants.APP_PROPERTY_ADDITIONAL_DATA, additionalData));

                    final String contentType;
                    if (payload.length() > 0) {
                        contentType = String.format(
                                "%s%s",
                                LoraConstants.CONTENT_TYPE_LORA_BASE,
                                provider.getProviderName());
                    } else {
                        contentType = EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;
                    }

                    uploadTelemetryMessage(ctx, gatewayDevice.getTenantId(), deviceId, payload, contentType);
                    break;
                default:
                    LOG.debug("discarding message of unsupported type [tenant: {}, device-id: {}, type: {}]",
                            gatewayDevice.getTenantId(), deviceId, type);
                    currentSpan.log("discarding message of unsupported type");
                    // discard the message but return 202 to not cause errors on the LoRa provider side
                    handle202(ctx.getRoutingContext());
                }
            } catch (final LoraProviderMalformedPayloadException e) {
                LOG.debug("error processing request from provider [name: {}]", provider.getProviderName(), e);
                TracingHelper.logError(currentSpan, "error processing request", e);
                handle400(ctx.getRoutingContext(), ERROR_MSG_INVALID_PAYLOAD);
            }
        } else {
            handleUnsupportedUserType(ctx.getRoutingContext(), currentSpan);
        }
        currentSpan.finish();
    }

    void handleOptionsRoute(final RoutingContext ctx) {

        final Span currentSpan = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                "process OPTIONS request",
                getClass().getSimpleName())
                .start();

        if (ctx.user() instanceof Device) {
            // Some providers use OPTIONS request to check if request works. Therefore returning 200.
            handle200(ctx);
        } else {
            handleUnsupportedUserType(ctx, currentSpan);
        }
        currentSpan.finish();
    }

    private void handleUnsupportedUserType(final RoutingContext ctx, final Span currentSpan) {
        final String userType = Optional.ofNullable(ctx.user()).map(user -> user.getClass().getName()).orElse("null");
        TracingHelper.logError(
                currentSpan,
                Map.of(Fields.MESSAGE, "request contains unsupported type of user credentials",
                        "type", userType));
        LOG.debug("request contains unsupported type of credentials [{}], returning 401", userType);
        handle401(ctx);
    }

    private void handle200(final RoutingContext ctx) {
        ctx.response().setStatusCode(200);
        ctx.response().end();
    }

    private void handle202(final RoutingContext ctx) {
        ctx.response().setStatusCode(202);
        ctx.response().end();
    }

    private void handle401(final RoutingContext ctx) {
        HttpUtils.unauthorized(ctx, "Basic realm=\"" + getConfig().getRealm() + "\"");
    }

    private void handle400(final RoutingContext ctx, final String msg) {
        HttpUtils.badRequest(ctx, msg);
    }
}
