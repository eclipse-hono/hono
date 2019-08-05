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

package org.eclipse.hono.adapter.lora.impl;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import io.vertx.core.buffer.impl.BufferImpl;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraProtocolAdapterProperties;
import org.eclipse.hono.adapter.lora.providers.LoraProvider;
import org.eclipse.hono.adapter.lora.providers.LoraProviderMalformedPayloadException;
import org.eclipse.hono.adapter.lora.providers.LoraUtils;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.eclipse.hono.service.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.service.auth.device.X509AuthProvider;
import org.eclipse.hono.service.http.HonoBasicAuthHandler;
import org.eclipse.hono.service.http.HonoChainAuthHandler;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.X509AuthHandler;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.contrib.vertx.ext.web.TracingHandler;
import io.opentracing.noop.NoopSpan;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.handler.ChainAuthHandler;


/**
 * A Vert.x based Hono protocol adapter for receiving HTTP push messages from and sending commands to LoRa backends.
 */
public final class LoraProtocolAdapter extends AbstractVertxBasedHttpProtocolAdapter<LoraProtocolAdapterProperties> {

    private static final String ERROR_MSG_MISSING_OR_UNSUPPORTED_CONTENT_TYPE = "missing or unsupported content-type";
    private static final Logger LOG = LoggerFactory.getLogger(LoraProtocolAdapter.class);
    private static final String LORA_COMMAND_CONSUMER_DEVICE_ID = "lora";
    private static final int LORA_COMMAND_CONSUMER_RETRY_INTERVAL = 2_000;
    private static final String TAG_LORA_DEVICE_ID = "lora_device_id";
    private static final String TAG_LORA_PROVIDER = "lora_provider";
    private static final String JSON_MISSING_REQUIRED_FIELDS = "JSON Body does not contain required fields";
    private static final String INVALID_PAYLOAD = "Invalid payload";
    private static final String FIELD_BINARY_PAYLOAD = "payload";
    private static final String FIELD_ORIG_UPLINK_MESSAGE = "orig-message";

    private final List<LoraProvider> loraProviders = new ArrayList<>();

    private HonoClientBasedAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;
    private HonoClientBasedAuthProvider<SubjectDnCredentials> clientCertAuthProvider;
    private final AtomicBoolean startOfLoraCommandConsumersScheduled = new AtomicBoolean();

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

    @Override
    protected void customizeDownstreamMessage(final Message downstreamMessage, final RoutingContext ctx) {
        MessageHelper.addProperty(downstreamMessage, LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER,
                ctx.get(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER));

        final Object normalizedProperties = ctx.get(LoraConstants.NORMALIZED_PROPERTIES);
        if (normalizedProperties != null && normalizedProperties instanceof Map) {
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

    void handleProviderRoute(final RoutingContext ctx, final LoraProvider provider) {
        LOG.debug("Handling route for provider with path: [{}]", provider.pathPrefix());
        final Span currentSpan = getCurrentSpan(ctx);
        ctx.put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, provider.getProviderName());
        currentSpan.setTag(TAG_LORA_PROVIDER, provider.getProviderName());

        if (ctx.user() instanceof Device) {
            final Device gatewayDevice = (Device) ctx.user();
            final JsonObject loraMessage = ctx.getBodyAsJson();
            currentSpan.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, gatewayDevice.getTenantId());

            LoraMessageType type = LoraMessageType.UNKNOWN;
            try {
                type = provider.extractMessageType(loraMessage);
                final String deviceId = provider.extractDeviceId(loraMessage);
                currentSpan.setTag(TAG_LORA_DEVICE_ID, deviceId);
                currentSpan.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);

                if (LoraMessageType.UPLINK.equals(type)) {
                    final String payload = provider.extractPayload(loraMessage);
                    if (payload == null){
                        throw new LoraProviderMalformedPayloadException("Payload == null", new NullPointerException("payload"));
                    }
                    final Buffer payloadInBuffer = new BufferImpl().appendString(payload);

                    final Map<String, Object> normalizedData = provider.extractNormalizedData(loraMessage);
                    ctx.put(LoraConstants.NORMALIZED_PROPERTIES, normalizedData);

                    final JsonObject additionalData = provider.extractAdditionalData(loraMessage);
                    ctx.put(LoraConstants.ADDITIONAL_DATA, additionalData);

                    final String contentType = LoraConstants.CONTENT_TYPE_LORA_BASE +  provider.getProviderName() + LoraConstants.CONTENT_TYPE_LORA_POST_FIX;

                    doUpload(ctx, gatewayDevice, deviceId, payloadInBuffer, contentType);
                } else {
                    LOG.debug("Received message '{}' of type [{}] for device [{}], will discard message.", loraMessage,
                            type, deviceId);
                    currentSpan.log(
                            "Received message of type '" + type + "' for device '" + deviceId + "' will be discarded.");
                    // Throw away the message but return 202 to not cause errors on the LoRa provider side
                    handle202(ctx);
                }
            } catch (final ClassCastException | LoraProviderMalformedPayloadException e) {
                LOG.debug("Got invalid payload '{}' which leads to exception: {}", loraMessage, e);
                TracingHelper.logError(currentSpan,
                        "Received message of type '" + type + "' has invalid payload; error: " + e);
                handle400(ctx, INVALID_PAYLOAD);
            }
        } else {
            LOG.debug("Supplied credentials are not an instance of the user. Returning 401");
            TracingHelper.logError(currentSpan, "Supplied credentials are not an instance of the user");
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
            handle400(ctx, JSON_MISSING_REQUIRED_FIELDS);
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

    @Override
    protected void onCommandConnectionEstablished(final HonoConnection commandConnection) {
        if (startOfLoraCommandConsumersScheduled.compareAndSet(false, true)) {
            scheduleStartLoraCommandConsumers();
        }
    }

    private void scheduleStartLoraCommandConsumers() {
        final List<String> commandEnabledTenants = getConfig().getCommandEnabledTenants();
        LOG.info("Starting Lora command consumers for tenants [{}] ...", commandEnabledTenants);
        for (final String tenantId : commandEnabledTenants) {
            scheduleStartLoraCommandConsumer(tenantId);
        }
    }

    private void scheduleStartLoraCommandConsumer(final String tenantId) {
        LOG.info("Starting Lora command consumer for tenant '{}' ...", tenantId);
        startLoraCommandConsumer(tenantId).recover(x -> {
            LOG.error("Error starting initial Lora command consumer for tenant [{}], retry in {} ms", tenantId,
                    LORA_COMMAND_CONSUMER_RETRY_INTERVAL, x);
            vertx.setTimer(LORA_COMMAND_CONSUMER_RETRY_INTERVAL, y -> scheduleStartLoraCommandConsumer(tenantId));
            return Future.succeededFuture();
        });
    }

    private Future<MessageConsumer> startLoraCommandConsumer(final String tenantId) {

        return getCommandConsumerFactory().createCommandConsumer(
                tenantId,
                LORA_COMMAND_CONSUMER_DEVICE_ID,
                receivedCommandContext -> commandConsumer(tenantId, receivedCommandContext),
                remoteClose -> {
                    LOG.info("Closing command consumer");
                },
                LORA_COMMAND_CONSUMER_RETRY_INTERVAL);
    }

    private void commandConsumer(final String tenantId, final CommandContext receivedCommandContext) {
        Tags.COMPONENT.set(receivedCommandContext.getCurrentSpan(), getTypeName());
        final Command command = receivedCommandContext.getCommand();
        final CommandData commandData = new CommandData();
        commandData.setCommand(command);

        getTenantConfiguration(tenantId, receivedCommandContext.getCurrentSpan().context()).compose(tenantObject -> {
            if (tenantObject.isAdapterEnabled(getTypeName())) {
                final String logMsg = "Adapter " + getTypeName() + " is enabled for tenant " + tenantId;
                LOG.debug(logMsg);
                return Future.succeededFuture();
            } else {
                final String logMsg = "Adapter " + getTypeName() + " is not enabled for tenant " + tenantId;
                LOG.error(logMsg);
                return Future.failedFuture(logMsg);
            }
        }).compose(adapterEnabledForTenant -> {
            // we accept the message before sending it to the device in order to provide a consistent behavior
            // across all protocol adapters, i.e. accepting the message only means that the message contains
            // a valid command which we are willing to deliver see
            // org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter.createCommandConsumer
            receivedCommandContext.accept(1);

            if (isValidLoraCommand(command)) {

                final String loraDeviceId = (String) command.getApplicationProperties()
                        .get(MessageHelper.APP_PROPERTY_DEVICE_ID);
                receivedCommandContext.getCurrentSpan().setTag(TAG_LORA_DEVICE_ID, loraDeviceId);
                commandData.setTargetDeviceId(loraDeviceId);
                LOG.debug("Got valid command {} for an actual lora device [{}]", command, loraDeviceId);

                final Future<JsonObject> loraGatewayFuture = getLoraDeviceGateway(tenantId, loraDeviceId);

                loraGatewayFuture
                        .compose(loraGateway -> getRegistrationAssertion(tenantId, loraDeviceId,
                                new Device(tenantId, loraGateway.getString("device-id")),
                                receivedCommandContext.getCurrentSpan().context())
                                        .compose(registrationAssertion -> {
                                            LOG.debug("Lora device {} registered and enabled for the Lora gateway.",
                                                    loraDeviceId);
                                            commandData.setGatewayAndExtractLoraNetworkData(loraGatewayFuture.result())
                                                    .compose(loraNetworkData -> getGatewayCredentials(tenantId,
                                                            loraNetworkData))
                                                    .compose(loraGatewayCredentials -> {
                                                        LOG.debug(
                                                                "Successfully received gateway credentials for lora device "
                                                                        + "'{}'",
                                                                commandData.getTargetDeviceId());
                                                        commandData.setGatewayCredentials(loraGatewayCredentials);
                                                        return sendCommandToDevice(commandData);
                                                    }).compose(httpResponse -> {
                                                        LOG.debug(
                                                                "Received status code '{}'. Response body '{}' from device {} for command {}",
                                                                httpResponse.statusCode(), httpResponse.body(),
                                                                commandData.getTargetDeviceId(), command);
                                                        sendResponseToApplication(command,
                                                                commandData.getTargetDeviceId(),
                                                                httpResponse,
                                                                receivedCommandContext.getCurrentSpan().context());
                                                        return Future.succeededFuture();
                                                    }).otherwise(sendCommandFailure -> {
                                                        LOG.error(
                                                                "Error sending command to device {}. Sending error response "
                                                                        + "to application with code [{}]",
                                                                commandData.getTargetDeviceId(),
                                                                HTTP_INTERNAL_ERROR, sendCommandFailure);

                                                        TracingHelper.logError(receivedCommandContext.getCurrentSpan(),
                                                                sendCommandFailure);
                                                        sendResponseToApplication(command, loraDeviceId,
                                                                getHttpResponseWithCode(HTTP_INTERNAL_ERROR,
                                                                        sendCommandFailure.getMessage()),
                                                                receivedCommandContext.getCurrentSpan().context());
                                                        return null;
                                                    });
                                            return Future.succeededFuture();
                                        }).otherwise(registrationAssertionFailure -> {
                                            LOG.error("Error asserting device registration. Sending error response to "
                                                    + "application with code [{}]", HTTP_FORBIDDEN,
                                                    registrationAssertionFailure);
                                            TracingHelper.logError(receivedCommandContext.getCurrentSpan(),
                                                    registrationAssertionFailure);
                                            sendResponseToApplication(command, loraDeviceId,
                                                    getHttpResponseWithCode(HTTP_FORBIDDEN,
                                                            registrationAssertionFailure.getMessage()),
                                                    receivedCommandContext.getCurrentSpan().context());
                                            return null;
                                        }))
                        .otherwise(loraGatewayException -> {
                            LOG.error("Error getting lora device gateway. Sending error response to application with "
                                    + "code [{}]", HTTP_INTERNAL_ERROR, loraGatewayException);
                            TracingHelper.logError(receivedCommandContext.getCurrentSpan(), loraGatewayException);
                            sendResponseToApplication(command, loraDeviceId,
                                    getHttpResponseWithCode(HTTP_INTERNAL_ERROR,
                                            loraGatewayException.getMessage()),
                                    receivedCommandContext.getCurrentSpan().context());
                            return null;
                        });

                return Future.succeededFuture();
            } else {
                LOG.debug("Got invalid command {} for actual lora device '{}'", command,
                        command.getApplicationProperties().get(MessageHelper.APP_PROPERTY_DEVICE_ID));
                return Future.failedFuture("Malformed command message.");
            }
        }).otherwise(validationException -> {
            LOG.error("Error trying to send command '{}'", command, validationException);
            TracingHelper.logError(receivedCommandContext.getCurrentSpan(), validationException);
            receivedCommandContext
                    .reject(new ErrorCondition(Constants.AMQP_BAD_REQUEST, validationException.getMessage()), 1);
            return null;
        });
    }

    private static boolean isValidLoraCommand(final Command command) {
        try {
            final String payload = command.getPayload().toJsonObject()
                    .getString(LoraConstants.FIELD_LORA_DOWNLINK_PAYLOAD);

            if (payload == null) {
                return false;
            }
        } catch (final ClassCastException | DecodeException e) {
            return false;
        }

        final Object loraDeviceIdObject = command.getApplicationProperties().get(MessageHelper.APP_PROPERTY_DEVICE_ID);
        if (!(loraDeviceIdObject instanceof String)) {
            return false;
        }
        return command.isValid();
    }

    private Future<HttpResponse<Buffer>> sendCommandToDevice(final CommandData commandData) {
        LOG.debug("Sending {} to device", commandData.getCommand());
        final Future<HttpResponse<Buffer>> responseHandler = Future.future();

        final Optional<LoraProvider> providerOptional = loraProviders.stream()
                .filter(loraProvider -> loraProvider.getProviderName().equals(commandData.getLoraProvider()))
                .findFirst();
        if (providerOptional.isPresent()) {
            final LoraProvider provider = providerOptional.get();
            LOG.debug("Using LoraProvider [{}] to send command to gateway", provider.getProviderName());
            provider.sendDownlinkCommand(commandData.getGateway(), commandData.getGatewayCredentials(),
                    commandData.getTargetDeviceId(), commandData.getCommand()).setHandler(r -> {
                        if (r.succeeded()) {
                            LOG.debug("Successfully sent message to lora provider");
                            responseHandler.complete(getHttpResponseWithCode(HTTP_OK, "OK"));
                        } else {
                            LOG.error("Got error from lora provider", r.cause());
                            responseHandler
                                    .fail("Could not send command to lora provider. " + commandData.getCommand());
                        }
                    });
        } else {
            LOG.error("No lora provider found for [{}]", commandData.getLoraProvider());
            responseHandler.fail("No suitable lora provider found to send command" + commandData.getCommand());
        }
        return responseHandler;
    }

    private void sendResponseToApplication(final Command command, final String loraDeviceId,
            final HttpResponse<Buffer> response, final SpanContext spanContext) {
        final Span currentSpan = TracingHelper.buildFollowsFromSpan(tracer, spanContext, "upload Command response")
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, command.getTenant())
                .withTag(TAG_LORA_DEVICE_ID, loraDeviceId)
                .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, command.getDeviceId())
                .withTag(Constants.HEADER_COMMAND_RESPONSE_STATUS, response.statusCode())
                .withTag(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId()).start();

        final CommandResponse commandResponse = CommandResponse.from(command.getRequestId(), command.getTenant(), command.getDeviceId(),
                response.body(), response.getHeader("Content-Type"), response.statusCode());
        if (commandResponse != null) {
            sendCommandResponse(command.getTenant(), commandResponse, currentSpan.context()).map(delivery -> {
                LOG.debug("delivered command response [command-request-id: {}] to application", command.getRequestId());
                currentSpan.log("delivered command response to application");
                return delivery;
            }).otherwise(error -> {
                LOG.debug("could not send command response [command-request-id: {}] to application",
                        command.getRequestId(), error);
                TracingHelper.logError(currentSpan, error);
                return null;
            }).setHandler(c -> currentSpan.finish());
        } else {
            final String errorMsg = String.format("command-request-id [%s] or status code [%s] is missing/invalid",
                    command.getRequestId(), response.statusCode());
            LOG.debug("cannot send command response from lora device [{}] to application: {}", loraDeviceId, errorMsg);
            TracingHelper.logError(currentSpan, errorMsg);
            currentSpan.finish();
        }
    }

    private Future<JsonObject> getLoraDeviceGateway(final String tenantId, final String loraDeviceId) {
        return getRegistrationClient(tenantId).compose(registrationClient -> registrationClient.get(loraDeviceId))
                .compose(this::extractGatewayId).compose(gatewayId -> getRegistrationClient(tenantId)
                        .compose(registrationClient -> registrationClient.get(gatewayId)));
    }

    private Future<CredentialsObject> getGatewayCredentials(final String tenantId, final JsonObject data) {
        final String authId = data.getString(LoraConstants.FIELD_AUTH_ID);
        return getCredentialsClientFactory().getOrCreateCredentialsClient(tenantId)
                .compose(credentialsClient -> credentialsClient.get(LoraConstants.FIELD_PSK, authId));
    }

    private Future<String> extractGatewayId(final JsonObject actualDevice) {
        LOG.debug("Retrieved device from device-registry {}", actualDevice);
        final JsonObject data = actualDevice.getJsonObject(RegistrationConstants.FIELD_DATA);
        final String gatewayId = data.getString(LoraConstants.FIELD_VIA);
        if (LoraUtils.isBlank(gatewayId)) {
            LOG.error("Lora device has no gateway configured :{}", gatewayId);
            return Future.failedFuture("Lora device has no gateway configured");
        } else {
            LOG.debug("Successfully retrieved the gateway Id: {}", gatewayId);
            return Future.succeededFuture(gatewayId);
        }
    }

    private HttpResponse<Buffer> getHttpResponseWithCode(final int statusCode, final String message) {
        return new HttpResponse<>() {

            @Override
            public HttpVersion version() {
                return null;
            }

            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public String statusMessage() {
                return message;
            }

            @Override
            public MultiMap headers() {
                return null;
            }

            @Override
            public String getHeader(final String headerName) {
                return "application/json";
            }

            @Override
            public MultiMap trailers() {
                return null;
            }

            @Override
            public String getTrailer(final String trailerName) {
                return null;
            }

            @Override
            public List<String> cookies() {
                return Collections.emptyList();
            }

            @Override
            public Buffer body() {
                return Buffer.buffer("command response");
            }

            @Override
            public Buffer bodyAsBuffer() {
                return null;
            }

            @Override
            public JsonArray bodyAsJsonArray() {
                return null;
            }
        };
    }

    /**
     * Data sent as command.
     */
    static class CommandData {

        private JsonObject gateway;
        private CredentialsObject gatewayCredentials;
        private String loraProvider;
        private Command command;
        private String targetDeviceId;

        JsonObject getGateway() {
            return gateway;
        }

        /**
         * sets the gateway and returns the lora network data contained in it.
         *
         * @param gateway target gateway
         * @return Future containing lora network data if present, otherwise a failed future is returned.
         */
        Future<JsonObject> setGatewayAndExtractLoraNetworkData(final JsonObject gateway) {
            this.gateway = gateway;

            if (LoraUtils.isValidLoraGateway(gateway)) {
                final JsonObject loraNetworkData = LoraUtils.getLoraConfigFromLoraGatewayDevice(gateway);
                this.loraProvider = loraNetworkData.getString(LoraConstants.FIELD_LORA_PROVIDER);
                return Future.succeededFuture(loraNetworkData);
            } else {
                LOG.debug("Not a valid lora gateway configuration");
                return Future.failedFuture("Not a valid lora gateway configuration");
            }
        }

        CredentialsObject getGatewayCredentials() {
            return gatewayCredentials;
        }

        void setGatewayCredentials(final CredentialsObject gatewayCredentials) {
            this.gatewayCredentials = gatewayCredentials;
        }

        String getLoraProvider() {
            return loraProvider;
        }

        Command getCommand() {
            return command;
        }

        void setCommand(final Command command) {
            this.command = command;
        }

        String getTargetDeviceId() {
            return targetDeviceId;
        }

        void setTargetDeviceId(final String targetDeviceId) {
            this.targetDeviceId = targetDeviceId;
        }

    }
}
