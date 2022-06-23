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

package org.eclipse.hono.adapter.sigfox.impl;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.adapter.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.adapter.http.HonoBasicAuthHandler;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.BaseEncoding;

import io.opentracing.Span;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ChainAuthHandler;
import io.vertx.ext.web.handler.CorsHandler;

/**
 * A Vert.x based Hono protocol adapter for receiving HTTP push messages from and sending commands to the SigFox
 * backend.
 */
public final class SigfoxProtocolAdapter
        extends AbstractVertxBasedHttpProtocolAdapter<SigfoxProtocolAdapterProperties> {

    private static final String DOWNLINK_DATA_FIELD = "downlinkData";

    private static final String SIGFOX_PROPERTY_PREFIX = "sigfox.";

    private static final String SIGFOX_PARAM_DEVICE_ID = "device";

    private static final String SIGFOX_PARAM_DATA = "data";

    private static final String SIGFOX_PARAM_TENANT = "tenant";

    private static final String SIGFOX_HEADER_PARAM_ACK = "ack";

    private static final String SIGFOX_QUERY_PARAM_ACK = "ack";

    private static final Logger LOG = LoggerFactory.getLogger(SigfoxProtocolAdapter.class);

    private DeviceCredentialsAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;

    /**
     * Handle message upload.
     */
    @FunctionalInterface
    private interface UploadHandler {
        void upload(HttpContext ctx, String tenant, String deviceId, Buffer payload, String contentType);
    }

    /**
     * Sets the provider to use for authenticating devices based on a username and password.
     * <p>
     * If not set explicitly using this method, a {@code UsernamePasswordAuthProvider} will be created during startup.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public void setUsernamePasswordAuthProvider(
            final DeviceCredentialsAuthProvider<UsernamePasswordCredentials> provider) {
        this.usernamePasswordAuthProvider = Objects.requireNonNull(provider);
    }

    @Override
    public String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_SIGFOX;
    }

    private void setupAuthorization(final Router router) {
        final ChainAuthHandler authHandler = ChainAuthHandler.any();

        authHandler.add(new HonoBasicAuthHandler(
                Optional.ofNullable(this.usernamePasswordAuthProvider).orElseGet(
                        () -> new UsernamePasswordAuthProvider(getCredentialsClient(), this.tracer)),
                getConfig().getRealm(),
                this::handleBeforeCredentialsValidation));

        router.route().handler(authHandler);
    }

    @Override
    protected void addRoutes(final Router router) {

        setupAuthorization(router);

        router.route("/data/telemetry/:" + SIGFOX_PARAM_TENANT)
                .method(HttpMethod.GET)
                .handler(dataCorsHandler())
                .handler(getBodyHandler())
                .handler(ctx -> dataHandler(HttpContext.from(ctx), this::uploadTelemetryMessage));

        router.route("/data/event/:" + SIGFOX_PARAM_TENANT)
                .method(HttpMethod.GET)
                .handler(dataCorsHandler())
                .handler(getBodyHandler())
                .handler(ctx -> dataHandler(HttpContext.from(ctx), this::uploadEventMessage));

        router.errorHandler(500, t -> LOG.warn("Unhandled exception", t.failure()));
    }

    private Handler<RoutingContext> dataCorsHandler() {
        return CorsHandler.create(getConfig().getCorsAllowedOrigin())
                .allowedMethod(HttpMethod.GET)
                .allowedHeader(Constants.HEADER_TIME_TILL_DISCONNECT)
                .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                .exposedHeader(Constants.HEADER_COMMAND)
                .exposedHeader(Constants.HEADER_COMMAND_REQUEST_ID);
    }

    private void dataHandler(final HttpContext ctx, final UploadHandler uploadHandler) {

        if (!ctx.isDeviceAuthenticated()) {
            LOG.warn("Not a device");
            return;
        }

        final Device gatewayDevice = ctx.getAuthenticatedDevice();

        final String deviceTenant = gatewayDevice.getTenantId();
        final String requestTenant = ctx.getRoutingContext().pathParam(SIGFOX_PARAM_TENANT);

        final String deviceId = ctx.getRoutingContext().queryParams().get(SIGFOX_PARAM_DEVICE_ID);
        final String strData = ctx.getRoutingContext().queryParams().get(SIGFOX_PARAM_DATA);
        final Buffer data = decodeData(strData);

        LOG.debug("{} handler - deviceTenant: {}, requestTenant: {}, deviceId: {}, data: {}",
                ctx.request().method(), deviceTenant, requestTenant, deviceId, strData);

        if ( requestTenant == null ) {
            ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing the tenant information in the request URL"));
            return;
        }

        if (!requestTenant.equals(deviceTenant)) {
            ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "tenant information mismatch"));
            return;
        }

        final String contentType = (data != null) ? CONTENT_TYPE_OCTET_STREAM
                : EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;

        uploadHandler.upload(ctx, deviceTenant, deviceId, data, contentType);
    }

    @Override
    protected void customizeDownstreamMessageProperties(final Map<String, Object> properties, final HttpContext ctx) {
        super.customizeDownstreamMessageProperties(properties, ctx);

        // pass along all query parameters that start with 'sigfox.'
        // If a key has multiple values, then only one of them will be mapped.

        for (final var entry : ctx.getRoutingContext().queryParams()) {
            if (entry.getKey() == null || !entry.getKey().startsWith(SIGFOX_PROPERTY_PREFIX)) {
                continue;
            }
            properties.put(entry.getKey(), entry.getValue());
        }

    }

    private static Buffer decodeData(final String data) {
        if (data == null) {
            return Buffer.buffer();
        }
        return Buffer.buffer(BaseEncoding.base16().decode(data.toUpperCase()));
    }

    @Override
    protected Integer getTimeUntilDisconnectFromRequest(final HttpContext ctx) {

        String ack = ctx.getRoutingContext().queryParams().get(SIGFOX_QUERY_PARAM_ACK);

        if (ack == null) {
            ack = ctx.request().getHeader(SIGFOX_HEADER_PARAM_ACK);
        }

        if (ack == null) {
            return null;
        }

        LOG.debug("Ack required for request?: '{}'", ack);

        if ( !Boolean.parseBoolean(ack)) {
            return null;
        }

        return getConfig().getTtdWhenAckRequired();
    }

    @Override
    protected boolean isCommandValid(final Command command, final Span span) {

        if (!super.isCommandValid(command, span)) {
            return false;
        }

        if (!command.isOneWay()) {
            // two way commands are not supported
            TracingHelper.logError(span, "command must be one way");
            return false;
        }

        if (command.getPayload() == null) {
            TracingHelper.logError(span, "command has no payload");
            return false;
        }

        if (command.getPayloadSize() != 8) {
            // wrong size
            TracingHelper.logError(span,
                    "command payload size must be exactly 8 bytes long, is " + command.getPayloadSize());
            return false;
        }

        return true;

    }

    @Override
    protected void setEmptyResponsePayload(final HttpServerResponse response, final Span currentSpan) {
        LOG.debug("Setting empty response for ACK");

        response.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
        currentSpan.log("responding with: no content");
    }

    @Override
    protected void setNonEmptyResponsePayload(final HttpServerResponse response, final CommandContext commandContext,
            final Span currentSpan) {

        currentSpan.log("responding with: payload");

        final Command command = commandContext.getCommand();
        response.setStatusCode(HttpURLConnection.HTTP_OK);
        final Buffer payload = convertToResponsePayload(command);

        LOG.debug("Setting response for ACK: {}", payload);

        HttpUtils.setResponseBody(response, payload, HttpUtils.CONTENT_TYPE_JSON);

    }

    private Buffer convertToResponsePayload(final Command command) {
        final JsonObject payload = new JsonObject();

        payload.put(command.getGatewayOrDeviceId(),
                new JsonObject()
                        .put(DOWNLINK_DATA_FIELD,
                                BaseEncoding
                                        .base16()
                                        .encode(command.getPayload().getBytes())
                                        .toLowerCase()));

        return payload.toBuffer();
    }
}
