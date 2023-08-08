/**
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.adapter.MapBasedTelemetryExecutionContext;
import org.eclipse.hono.adapter.coap.option.TimeOption;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;

import io.micrometer.core.instrument.Timer.Sample;
import io.opentracing.Span;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * A dictionary of relevant information required during the processing of a CoAP request message published by a device.
 *
 */
public final class CoapContext extends MapBasedTelemetryExecutionContext {

    /**
     * The query parameter which is used to indicate an empty notification.
     */
    public static final String PARAM_EMPTY_CONTENT = "empty";

    /**
     * The query parameter which is used to indicate, that a piggypacked response is supported by the device.
     * (Legacy support for device with firmware versions not supporting  piggypacked response.)
     */
    static final String PARAM_PIGGYBACKED = "piggy";
    private static final Set<String> SHORT_EP_NAMES = Set.of(
                            TelemetryConstants.TELEMETRY_ENDPOINT_SHORT,
                            EventConstants.EVENT_ENDPOINT_SHORT,
                            CommandConstants.COMMAND_RESPONSE_ENDPOINT_SHORT);

    private final CoapExchange exchange;
    private final DeviceUser originDevice;
    private final String authId;
    private final AtomicBoolean acceptTimerFlag = new AtomicBoolean(false);
    private final AtomicBoolean acceptFlag = new AtomicBoolean(false);
    private Sample timer;

    private CoapContext(
            final CoapExchange exchange,
            final DeviceUser originDevice,
            final DeviceUser authenticatedDevice,
            final String authId,
            final Span span) {
        super(span, authenticatedDevice);
        this.exchange = exchange;
        this.originDevice = originDevice;
        this.authId = authId;
    }

    /**
     * Creates a new context for a CoAP request.
     *
     * @param request The CoAP exchange representing the request.
     * @param originDevice The device that the message originates from.
     * @param authenticatedDevice The authenticated device that has uploaded the message or {@code null}
     *                            if the device has not been authenticated.
     * @param authId The authentication identifier of the request or {@code null} if the request is unauthenticated.
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of this context.
     * @return The context.
     * @throws NullPointerException if request, originDevice or span are {@code null}.
     */
    public static CoapContext fromRequest(
            final CoapExchange request,
            final DeviceUser originDevice,
            final DeviceUser authenticatedDevice,
            final String authId,
            final Span span) {

        Objects.requireNonNull(request);
        Objects.requireNonNull(originDevice);
        Objects.requireNonNull(span);
        return new CoapContext(request, originDevice, authenticatedDevice, authId, span);
    }

    /**
     * Creates a new context for a CoAP request.
     *
     * @param request The CoAP exchange representing the request.
     * @param originDevice The device that the message originates from.
     * @param authenticatedDevice The authenticated device that has uploaded the message or {@code null}
     *                            if the device has not been authenticated.
     * @param authId The authentication identifier of the request or {@code null} if the request is unauthenticated.
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of this context.
     * @param timer The object to use for measuring the time it takes to process the request.
     * @return The context.
     * @throws NullPointerException if request, originDevice, span or timer are {@code null}.
     */
    public static CoapContext fromRequest(
            final CoapExchange request,
            final DeviceUser originDevice,
            final DeviceUser authenticatedDevice,
            final String authId,
            final Span span,
            final Sample timer) {

        Objects.requireNonNull(request);
        Objects.requireNonNull(originDevice);
        Objects.requireNonNull(span);
        Objects.requireNonNull(timer);

        final CoapContext result = new CoapContext(request, originDevice, authenticatedDevice, authId, span);
        result.timer = timer;
        return result;
    }

    /**
     * Gets the device that the message originates from.
     *
     * @return The device.
     */
    public DeviceUser getOriginDevice() {
        return originDevice;
    }

    /**
     * Gets the identifier of the gateway that has acted on behalf of the device that
     * the message originates from.
     *
     * @return The gateway identifier or {@code null} if the message has not been uploaded
     *         by a gateway.
     */
    public String getGatewayId() {
        if (isDeviceAuthenticated() && !getOriginDevice().getDeviceId().equals(getAuthenticatedDevice().getDeviceId())) {
            return getAuthenticatedDevice().getDeviceId();
        } else {
            return null;
        }
    }

    /**
     * Gets the tenant identifier.
     *
     * @return The tenant.
     */
    public String getTenantId() {
        return originDevice.getTenantId();
    }

    /**
     * Gets the authentication identifier of the request.
     * <p>
     * Will be {@code null} for an unauthenticated request.
     *
     * @return The authentication identifier or {@code null}.
     */
    public String getAuthId() {
        return authId;
    }

    /**
     * Sets a timer to trigger the sending of a separate ACK to a device.
     * <p>
     * This method first tries to find a {@value CoapConstants#TIMEOUT_TO_ACK} property value
     * in the tenant's CoAP adapter configuration.
     * If no such property is found, the value given in the timeoutMillis parameter is used instead.
     *
     * @param vertx The vert.x instance to set the timer on.
     * @param tenant The tenant that the device belongs to which the request being processed originates from.
     * @param timeoutMillis The number of milliseconds to wait for a separate ACK if no tenant specific value
     *            has been configured. {@code -1}, never use separate response, {@code 0}, always use separate response.
     * @throws NullPointerException if vertx or tenant are {@code null}.
     */
    public void startAcceptTimer(
            final Vertx vertx,
            final TenantObject tenant,
            final long timeoutMillis) {

        Objects.requireNonNull(vertx, "vert.x");
        Objects.requireNonNull(tenant, "tenant");

        final long ackTimeout = Optional.ofNullable(tenant.getAdapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP))
                .map(config -> {
                    long effectiveTimeoutMillis = timeoutMillis;
                    final Object value = config.getExtensions().get(CoapConstants.TIMEOUT_TO_ACK);
                    if (value instanceof Number) {
                        effectiveTimeoutMillis = ((Number) value).longValue();
                        if (timeoutMillis > 0 && effectiveTimeoutMillis == 0 && isPiggyBackedResponseSupportedByDevice()) {
                            // allow devices to explicitly indicate that they support piggy-backed responses
                            final Object deviceTriggeredValue = config.getExtensions().get(CoapConstants.DEVICE_TRIGGERED_TIMEOUT_TO_ACK);
                            if (deviceTriggeredValue instanceof Number) {
                                // tenant device triggered timeout
                                effectiveTimeoutMillis = ((Number) deviceTriggeredValue).longValue();
                            } else {
                                // general timeout
                                effectiveTimeoutMillis = timeoutMillis;
                            }
                        }
                    }
                    return effectiveTimeoutMillis;
                })
                .orElse(timeoutMillis);

        if (acceptTimerFlag.compareAndSet(false, true)) {
            if (ackTimeout < 0) {
                // always use piggy-backed response, never send separate ACK
                return;
            } else if (ackTimeout == 0) {
                // always send separate ACK and separate response
                accept();
            } else if (!acceptFlag.get()) {
                vertx.setTimer(ackTimeout, id -> {
                    accept();
                });
            }
        }
    }

    /**
     * Gets the CoAP exchange.
     *
     * @return The exchange.
     */
    public CoapExchange getExchange() {
        return exchange;
    }

    /**
     * Get payload of request.
     *
     * @return payload of request
     */
    public Buffer getPayload() {
        final byte[] payload = exchange.getRequestPayload();
        if (payload == null || payload.length == 0) {
            return Buffer.buffer();
        } else {
            return Buffer.buffer(payload);
        }
    }

    /**
     * Gets the media type that corresponds to the <em>content-format</em> option of the CoAP request.
     * <p>
     * The media type is determined as follows:
     * <ol>
     * <li>If the request's <em>URI-query</em> option contains the {@link #PARAM_EMPTY_CONTENT} parameter,
     * the media type is {@link EventConstants#CONTENT_TYPE_EMPTY_NOTIFICATION}.</li>
     * <li>Otherwise, if the request doesn't contain a <em>content-format</em> option, the media type
     * is {@code null}</li>
     * <li>Otherwise, if the content-format code is registered with IANA, the media type is the one
     * that has been registered for the code</li>
     * <li>Otherwise, the media type is <em>unknown/code</em> where code is the value of the content-format
     * option</li>
     * </ol>
     *
     * @return The media type or {@code null} if the request does not contain a content-format option.
     */
    public String getContentType() {
        if (isEmptyNotification()) {
            return EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;
        } else if (exchange.getRequestOptions().hasContentFormat()) {
            return MediaTypeRegistry.toString(exchange.getRequestOptions().getContentFormat());
        } else {
            return null;
        }
    }

    /**
     * Gets the object used for measuring the time it takes to process this request.
     *
     * @return The timer or {@code null} if not set.
     */
    public Sample getTimer() {
        return timer;
    }

    /**
     * Get CoAP query parameter.
     *
     * @param name parameter name
     * @return value of query parameter, or {@code null}, if not provided in request,
     */
    public String getQueryParameter(final String name) {
        return exchange.getQueryParameter(name);
    }

    /**
     * Gets the value of the {@link org.eclipse.hono.util.Constants#HEADER_TIME_TILL_DISCONNECT} query parameter of the
     * CoAP request.
     *
     * @return The time till disconnect or {@code null} if
     *         <ul>
     *         <li>the request doesn't contain a {@link org.eclipse.hono.util.Constants#HEADER_TIME_TILL_DISCONNECT}
     *         query parameter.</li>
     *         <li>the contained value cannot be parsed as an Integer</li>
     *         </ul>
     */
    public Integer getTimeUntilDisconnect() {
        return getIntegerQueryParameter(Constants.HEADER_TIME_TILL_DISCONNECT);
    }

    /**
     * Get command request id of response for command.
     *
     * @return command request id.
     */
    public String getCommandRequestId() {
        final List<String> pathList = exchange.getRequestOptions().getUriPath();
        if (pathList.size() == 2 || pathList.size() == 4) {
            return pathList.get(pathList.size() - 1);
        }
        return null;
    }

    /**
     * Get command response status of response for command.
     *
     * @return status, or {@code null}, if not available.
     */
    public Integer getCommandResponseStatus() {
        return getIntegerQueryParameter(Constants.HEADER_COMMAND_RESPONSE_STATUS);
    }

    /**
     * Checks if the device has explicitly indicated that it supports receiving
     * a CoAP response in an ACK message.
     *
     * @return {@code true} if the request URI contains a {@value #PARAM_PIGGYBACKED} query parameter.
     */
    private boolean isPiggyBackedResponseSupportedByDevice() {
        return exchange.getQueryParameter(PARAM_PIGGYBACKED) != null;
    }

    /**
     * Check, if request represents a empty notification, just to check, if commands are available.
     *
     * @return {@code true}, if request is a empty notification, {@code false}, otherwise.
     */
    public boolean isEmptyNotification() {
        return exchange.getQueryParameter(PARAM_EMPTY_CONTENT) != null;
    }

    /**
     * Checks if the exchange's request has been sent using a CONfirmable message.
     *
     * @return {@code true} if the request message is CONfirmable.
     */
    public boolean isConfirmable() {
        return exchange.advanced().getRequest().isConfirmable();
    }

    /**
     * Sends a response to the device.
     * <p>
     * This also accepts the exchange.
     *
     * @param responseCode The code to set in the response.
     */
    public void respondWithCode(final ResponseCode responseCode) {
        respond(new Response(responseCode));
    }

    /**
     * Sends a response to the device.
     * <p>
     * This also accepts the exchange.
     *
     * @param responseCode The code to set in the response.
     * @param description The message to include in the response body or {@code null} if
     *                    the response body should not include a description.
     */
    public void respondWithCode(final ResponseCode responseCode, final String description) {
        final Response response = new Response(responseCode);
        Optional.ofNullable(description).ifPresent(desc -> {
            response.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
            response.setPayload(description);
        });
        respond(response);
    }

    /**
     * Checks whether the request includes either the time option or the hono-time parameter which
     * indicates that the response should include a time option with the server time.
     *
     * @return <code>true</code> if response should include the time option containing the server time.
     */
    private boolean shouldResponseIncludeTimeOption() {
        return Optional.ofNullable(exchange.getRequestOptions())
                .map(opts -> opts.hasOption(TimeOption.DEFINITION))
                .orElse(false)
                || exchange.getQueryParameter(TimeOption.QUERY_PARAMETER_NAME) != null;
    }

    /**
     * Sends a response to the device.
     * <p>
     * This also accepts the exchange.
     *
     * @param response The response to send.
     * @return The response code from the response.
     */
    public ResponseCode respond(final Response response) {
        if (shouldResponseIncludeTimeOption()) {
            // Add a time option with the current time to the response
            response.getOptions().addOption(new TimeOption());
        }
        acceptFlag.set(true);
        exchange.respond(response);
        return response.getCode();
    }

    /**
     * Accept exchange.
     */
    public void accept() {
        if (acceptFlag.compareAndSet(false, true)) {
            exchange.accept();
        }
    }

    /**
     * Gets the integer value of the provided query parameter.
     *
     * @param parameterName The name of the query parameter.
     * @return The integer value or {@code null} if
     *         <ul>
     *         <li>the request doesn't contain the provided query parameter or</li>
     *         <li>the contained value cannot be parsed as an Integer.</li>
     *         </ul>
     */
    private Integer getIntegerQueryParameter(final String parameterName) {

        return Optional.ofNullable(exchange.getQueryParameter(parameterName))
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (final NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    @Override
    public QoS getRequestedQos() {
        return isConfirmable() ? QoS.AT_LEAST_ONCE : QoS.AT_MOST_ONCE;
    }

    /**
     * {@inheritDoc}
     *
     * @return An empty optional.
     */
    @Override
    public Optional<Duration> getTimeToLive() {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @return The <em>Uri-Path</em> request option prefixed with a <em>/</em> character.
     */
    @Override
    public String getOrigAddress() {
        return "/" + exchange.getRequestOptions().getUriPathString();
    }

    /**
     * Checks if the request URI contains the short version of an API endpoint name.
     *
     * @return {@code true} if the URI contains a short version.
     */
    public boolean hasShortEndpointName() {
        final String ep = exchange.getRequestOptions().getUriPath().get(0);
        return SHORT_EP_NAMES.contains(ep);
    }
}
