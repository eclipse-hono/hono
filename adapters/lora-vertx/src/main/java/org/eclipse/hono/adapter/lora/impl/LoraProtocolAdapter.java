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
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraProtocolAdapterProperties;
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
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.http.X509AuthHandler;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.noop.NoopSpan;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tag;
import io.opentracing.tag.Tags;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ChainAuthHandler;


/**
 * A Vert.x based Hono protocol adapter for receiving HTTP push messages from and sending commands to LoRa backends.
 */
public final class LoraProtocolAdapter extends AbstractVertxBasedHttpProtocolAdapter<LoraProtocolAdapterProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(LoraProtocolAdapter.class);

    private static final String ERROR_MSG_MISSING_OR_UNSUPPORTED_CONTENT_TYPE = "missing or unsupported content-type";
    private static final String ERROR_MSG_INVALID_PAYLOAD = "invalid payload";
    private static final String ERROR_MSG_JSON_MISSING_REQUIRED_FIELDS = "JSON Body does not contain required fields";
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
    @Autowired
    public void setLoraProviders(final List<LoraProvider> providers) {
        this.loraProviders.addAll(Objects.requireNonNull(providers));
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

        for (final LoraProvider current : loraProviders) {
            router.route(HttpMethod.OPTIONS, current.pathPrefix()).handler(this::handleOptionsRoute);

            router.route(current.acceptedHttpMethod(), current.pathPrefix()).consumes(current.acceptedContentType())
                    .handler(ctx -> this.handleProviderRoute(ctx, current));

            router.route(current.acceptedHttpMethod(), current.pathPrefix()).handler(ctx -> {
                TracingHelper.logError(getCurrentSpan(ctx), "Incoming request does not contain proper content type");
                LOG.debug("Incoming request does not contain proper content type. Will return 400.");
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

    @SuppressWarnings("unchecked")
    @Override
    protected void customizeDownstreamMessage(final Message downstreamMessage, final RoutingContext ctx) {
        MessageHelper.addProperty(downstreamMessage, LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER,
                ctx.get(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER));

        final Object normalizedProperties = ctx.get(LoraConstants.NORMALIZED_PROPERTIES);
        if (normalizedProperties instanceof Map) {
            for (final Map.Entry<String, Object> entry:
                 ((Map<String, Object>) normalizedProperties).entrySet()) {
                MessageHelper.addProperty(downstreamMessage, entry.getKey(), entry.getValue());
            }
        }

        final Object additionalData = ctx.get(LoraConstants.ADDITIONAL_DATA);
        if (additionalData != null) {
            MessageHelper.addProperty(downstreamMessage, LoraConstants.ADDITIONAL_DATA, additionalData);
        }

    }

    void handleProviderRoute(final RoutingContext ctx, final LoraProvider provider) {

        LOG.debug("Handling route for provider with path: [{}]", provider.pathPrefix());
        final Span currentSpan = getCurrentSpan(ctx);
        currentSpan.setTag(TAG_LORA_PROVIDER, provider.getProviderName());
        ctx.put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, provider.getProviderName());

        if (ctx.user() instanceof Device) {
            final Device gatewayDevice = (Device) ctx.user();
            currentSpan.setTag(TracingHelper.TAG_TENANT_ID, gatewayDevice.getTenantId());
            final JsonObject loraMessage = ctx.getBodyAsJson();

            LoraMessageType type = LoraMessageType.UNKNOWN;
            try {
                type = provider.extractMessageType(loraMessage);
                final String deviceId = provider.extractDeviceId(loraMessage);
                currentSpan.setTag(TAG_LORA_DEVICE_ID, deviceId);
                currentSpan.setTag(TracingHelper.TAG_DEVICE_ID, deviceId);

                switch (type) {
                case UPLINK:
                    final String payload = provider.extractPayload(loraMessage);
                    if (payload == null) {
                        throw new LoraProviderMalformedPayloadException("Payload == null", new NullPointerException("payload"));
                    }
                    final Buffer payloadInBuffer = new BufferImpl().appendString(payload);

                    final Map<String, Object> normalizedData = provider.extractNormalizedData(loraMessage);
                    ctx.put(LoraConstants.NORMALIZED_PROPERTIES, normalizedData);

                    final JsonObject additionalData = provider.extractAdditionalData(loraMessage);
                    ctx.put(LoraConstants.ADDITIONAL_DATA, additionalData);

                    final String contentType = LoraConstants.CONTENT_TYPE_LORA_BASE +  provider.getProviderName() + LoraConstants.CONTENT_TYPE_LORA_POST_FIX;

                    doUpload(ctx, gatewayDevice, deviceId, payloadInBuffer, contentType);
                    break;
                default:
                    LOG.debug("Received message '{}' of type [{}] for device [{}], will discard message.", loraMessage,
                            type, deviceId);
                    currentSpan.log(
                            "Received message of type '" + type + "' for device '" + deviceId + "' will be discarded.");
                    // Throw away the message but return 202 to not cause errors on the LoRa provider side
                    handle202(ctx);
                }
            } catch (final ClassCastException | LoraProviderMalformedPayloadException e) {
                LOG.debug("cannot parse request payload", e);
                TracingHelper.logError(currentSpan, "cannot parse request payload", e);
                handle400(ctx, ERROR_MSG_INVALID_PAYLOAD);
            }
        } else {
            final String userType = ctx.user() == null ? "null" : ctx.user().getClass().getName();
            TracingHelper.logError(
                    currentSpan,
                    Map.of(Fields.MESSAGE, "request contains unsupported type of user credentials",
                            "type", userType));
            LOG.debug("request contains unsupported type of credentials [{}], returning 401", userType);
            handle401(ctx);
        }
    }

    private Span getCurrentSpan(final RoutingContext ctx) {
        return (ctx.get(TracingHandler.CURRENT_SPAN) instanceof Span) ? ctx.get(TracingHandler.CURRENT_SPAN)
                : NoopSpan.INSTANCE;
    }

    void handleOptionsRoute(final RoutingContext ctx) {
        LOG.debug("Handling options method");

        if (ctx.user() instanceof Device) {
            // Some providers use OPTIONS request to check if request works. Therefore returning 200.
            LOG.debug("Accept OPTIONS request. Will return 200");
            handle200(ctx);
        } else {
            LOG.debug("Supplied credentials are not an instance of the user. Returning 401");
            TracingHelper.logError(getCurrentSpan(ctx), "Supplied credentials are not an instance of the user");
            handle401(ctx);
        }
    }

    private void doUpload(final RoutingContext ctx, final Device device, final String deviceId,
                          final Buffer payload, final String contentType) {
        LOG.trace("Got push message for tenant '{}' and device '{}'", device.getTenantId(), deviceId);
        if (deviceId != null && payload != null) {
            uploadTelemetryMessage(ctx, device.getTenantId(), deviceId, payload,
                    contentType);
        } else {
            LOG.debug("Got payload without mandatory fields: {}", ctx.getBodyAsJson());
            if (deviceId == null) {
                TracingHelper.logError(getCurrentSpan(ctx), "Got message without deviceId");
            }
            if (payload == null) {
                TracingHelper.logError(getCurrentSpan(ctx), "Got message without valid payload");
            }
            handle400(ctx, ERROR_MSG_JSON_MISSING_REQUIRED_FIELDS);
        }
    }

    private void handle202(final RoutingContext ctx) {
        Tags.HTTP_STATUS.set(getCurrentSpan(ctx), 202);
        ctx.response().setStatusCode(202);
        ctx.response().end();
    }

    private void handle401(final RoutingContext ctx) {
        Tags.HTTP_STATUS.set(getCurrentSpan(ctx), 401);
        HttpUtils.unauthorized(ctx, "Basic realm=\"" + getConfig().getRealm() + "\"");
    }

    private void handle400(final RoutingContext ctx, final String msg) {
        Tags.HTTP_STATUS.set(getCurrentSpan(ctx), 400);
        HttpUtils.badRequest(ctx, msg);
    }

    private void handle200(final RoutingContext ctx) {
        Tags.HTTP_STATUS.set(getCurrentSpan(ctx), 200);
        ctx.response().setStatusCode(200);
        ctx.response().end();
    }
}
