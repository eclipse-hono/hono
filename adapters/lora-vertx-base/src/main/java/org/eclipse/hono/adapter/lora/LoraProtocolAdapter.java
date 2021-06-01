/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.SubjectDnCredentials;
import org.eclipse.hono.adapter.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.adapter.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.adapter.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.adapter.auth.device.X509AuthProvider;
import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.adapter.http.HonoBasicAuthHandler;
import org.eclipse.hono.adapter.http.HonoChainAuthHandler;
import org.eclipse.hono.adapter.http.X509AuthHandler;
import org.eclipse.hono.adapter.lora.providers.LoraProvider;
import org.eclipse.hono.adapter.lora.providers.LoraProviderMalformedPayloadException;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandEndpoint;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Timer.Sample;
import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tag;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
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

    private DeviceCredentialsAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;
    private DeviceCredentialsAuthProvider<SubjectDnCredentials> clientCertAuthProvider;
    private final Map<SubscriptionKey, LoraProvider> commandSubscriptions = new ConcurrentHashMap<>();
    private HttpClient httpClient = null;

    /**
     * Sets the LoRa providers that this adapter should support.
     *
     * @param providers The providers.
     * @throws NullPointerException if providers is {@code null}.
     */
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
    public void setUsernamePasswordAuthProvider(final DeviceCredentialsAuthProvider<UsernamePasswordCredentials> provider) {
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
    public void setClientCertAuthProvider(final DeviceCredentialsAuthProvider<SubjectDnCredentials> provider) {
        this.clientCertAuthProvider = Objects.requireNonNull(provider);
    }

    @Override
    public String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_LORA;
    }

    @Override
    protected void addRoutes(final Router router) {

        // the LoraWAN adapter always requires network providers to authenticate
        setupAuthorization(router);

        for (final LoraProvider provider : loraProviders) {
            for (final String pathPrefix : provider.pathPrefixes()) {
                router.route(HttpMethod.OPTIONS, pathPrefix)
                        .handler(this::handleOptionsRoute);

                router.route(provider.acceptedHttpMethod(), pathPrefix)
                        .consumes(provider.acceptedContentType())
                        .handler(ctx -> this.handleProviderRoute(HttpContext.from(ctx), provider));

                router.route(provider.acceptedHttpMethod(), pathPrefix).handler(ctx -> {
                    LOG.debug("request does not contain content-type header, will return 400 ...");
                    handle400(ctx, ERROR_MSG_MISSING_OR_UNSUPPORTED_CONTENT_TYPE);
                });
            }
        }
    }

    private void setupAuthorization(final Router router) {

        final ChainAuthHandler authHandler = new HonoChainAuthHandler(this::handleBeforeCredentialsValidation);
        authHandler.append(new X509AuthHandler(
                new TenantServiceBasedX509Authentication(getTenantClient(), tracer),
                Optional.ofNullable(clientCertAuthProvider).orElseGet(
                        () -> new X509AuthProvider(getCredentialsClient(), tracer))));
        authHandler.append(new HonoBasicAuthHandler(
                Optional.ofNullable(usernamePasswordAuthProvider).orElseGet(
                        () -> new UsernamePasswordAuthProvider(getCredentialsClient(), tracer)),
                getConfig().getRealm()));

        router.route().handler(authHandler);
    }

    @Override
    protected void customizeDownstreamMessageProperties(final Map<String, Object> properties, final HttpContext ctx) {

        properties.put(
                LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER,
                ctx.get(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER));

        Optional.ofNullable(ctx.get(LoraConstants.APP_PROPERTY_META_DATA))
            .map(LoraMetaData.class::cast)
            .ifPresent(metaData -> {
                Optional.ofNullable(metaData.getFunctionPort())
                    .ifPresent(port -> properties.put(LoraConstants.APP_PROPERTY_FUNCTION_PORT, port));
                final String json = Json.encode(metaData);
                properties.put(LoraConstants.APP_PROPERTY_META_DATA, json);
            });

        Optional.ofNullable(ctx.get(LoraConstants.APP_PROPERTY_ADDITIONAL_DATA))
            .map(JsonObject.class::cast)
            .ifPresent(data -> properties.put(LoraConstants.APP_PROPERTY_ADDITIONAL_DATA, data.encode()));
    }

    void handleProviderRoute(final HttpContext ctx, final LoraProvider provider) {

        LOG.debug("processing request from provider [name: {}, URI: {}", provider.getProviderName(),
                ctx.getRoutingContext().normalisedPath());
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
                    final SubscriptionKey key = new SubscriptionKey(gatewayDevice.getTenantId(),
                        gatewayDevice.getDeviceId());
                    if (!commandSubscriptions.containsKey(key)) {
                        createCommandConsumer(
                                gatewayDevice.getTenantId(),
                                gatewayDevice.getDeviceId(),
                                this::handleCommand,
                                currentSpan.context())
                                        .map(commandConsumer -> commandSubscriptions.put(key, provider));
                    }
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

    private void handleCommand(final CommandContext commandContext) {
        Tags.COMPONENT.set(commandContext.getTracingSpan(), getTypeName());
        final Sample timer = metrics.startTimer();
        final Command command = commandContext.getCommand();

        if (command.getGatewayId() == null) {
            LOG.debug("no gateway defined for command [{}]", command);
            TracingHelper.logError(commandContext.getTracingSpan(), "no gateway defined for command");
            commandContext.release();
            return;
        }
        final String tenant = command.getTenant();
        final String gatewayId = command.getGatewayId();

        final LoraProvider loraProvider = commandSubscriptions.get(new SubscriptionKey(tenant, gatewayId));
        if (loraProvider == null) {
            LOG.debug("received command for unknown gateway [{}] for tenant [{}]", gatewayId, tenant);
            TracingHelper.logError(commandContext.getTracingSpan(),
                    String.format("received command for unknown gateway [%s]", gatewayId));
            commandContext.release();
            return;
        }
        final Future<TenantObject> tenantTracker = getTenantConfiguration(tenant, commandContext.getTracingContext());
        tenantTracker
                .compose(tenantObject -> {
                    if (command.isValid()) {
                        return checkMessageLimit(tenantObject, command.getPayloadSize(), commandContext.getTracingContext());
                    } else {
                        return Future.failedFuture(
                                new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed command message"));
                    }
                })
                .compose(success -> getRegistrationClient().assertRegistration(tenant, gatewayId, null,
                        commandContext.getTracingContext()))
                .compose(registrationAssertion -> sendCommandToGateway(commandContext, loraProvider,
                        registrationAssertion.getCommandEndpoint()))
                .onSuccess(aVoid -> {
                    addMicrometerSample(commandContext, timer);
                    commandContext.accept();
                    metrics.reportCommand(
                            command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                            tenant,
                            tenantTracker.result(),
                            MetricsTags.ProcessingOutcome.FORWARDED,
                            command.getPayloadSize(),
                            timer);
                })
                .onFailure(t -> {
                    LOG.error("error sending command", t);
                    TracingHelper.logError(commandContext.getTracingSpan(), t);
                    commandContext.release();
                    metrics.reportCommand(
                            command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                            tenant,
                            tenantTracker.result(),
                            MetricsTags.ProcessingOutcome.from(t),
                            command.getPayloadSize(),
                            timer);
                });
    }

    private Future<Void> sendCommandToGateway(final CommandContext commandContext, final LoraProvider loraProvider,
            final CommandEndpoint commandEndpoint) {

        if (commandEndpoint == null) {
            return Future.failedFuture("gateway has no command endpoint defined");
        } else if (!commandEndpoint.isUriValid()) {
            return Future.failedFuture(String.format("gateway has command endpoint with invalid uri [%s]", commandEndpoint.getUri()));
        }

        final Command command = commandContext.getCommand();
        final Promise<Void> sendPromise = Promise.promise();
        final Buffer payload = Optional.ofNullable(command.getPayload()).orElseGet(Buffer::buffer);
        final LoraCommand loraCommand = loraProvider.getCommand(commandEndpoint, command.getDeviceId(), payload);
        commandContext.getTracingSpan().log(String.format("sending loraCommand to LNS [%s]", loraCommand.getUri()));
        LOG.debug("sending loraCommand to LNS [{}]", loraCommand.getUri());
        LOG.trace("command payload: {}", loraCommand.getPayload());
        final HttpClientRequest request = getHttpClient().postAbs(loraCommand.getUri())
                .handler(httpClientResponse -> {
                    Tags.HTTP_STATUS.set(commandContext.getTracingSpan(), httpClientResponse.statusCode());
                    if (StatusCodeMapper.isSuccessful(httpClientResponse.statusCode())) {
                        sendPromise.tryComplete();
                    } else {
                        sendPromise.tryFail(httpClientResponse.statusMessage());
                    }
                })
                .exceptionHandler(sendPromise::tryFail);
        commandEndpoint.getHeaders().forEach(request::putHeader);
        loraProvider.getDefaultHeaders().forEach(request::putHeader);
        request.end(loraCommand.getPayload().encode(), response -> {
            if (response.failed()) {
                sendPromise.tryFail(response.cause());
            }
        });
        return sendPromise.future();
    }

    private HttpClient getHttpClient() {
        if (httpClient != null) {
            return httpClient;
        }
        httpClient = vertx.createHttpClient();
        return httpClient;
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
