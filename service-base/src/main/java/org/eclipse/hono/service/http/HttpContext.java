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

package org.eclipse.hono.service.http;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ExecutionContext;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TelemetryExecutionContext;

import io.opentracing.SpanContext;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * Represents the context for the handling of a Vert.x HTTP request, wrapping the Vert.x {@link RoutingContext} as well
 * as implementing the {@link ExecutionContext} interface.
 */
public final class HttpContext implements TelemetryExecutionContext {

    private final RoutingContext routingContext;
    private SpanContext spanContext;

    private HttpContext(final RoutingContext routingContext) {
        this.routingContext = Objects.requireNonNull(routingContext);
    }

    /**
     * Creates a new HttpContext.
     *
     * @param routingContext The RoutingContext to wrap.
     *
     * @return The created HttpContext.
     * @throws NullPointerException if routingContext is {@code null}.
     */
    public static HttpContext from(final RoutingContext routingContext) {
        return new HttpContext(routingContext);
    }

    /**
     * Gets the wrapped RoutingContext.
     *
     * @return The RoutingContext.
     */
    public RoutingContext getRoutingContext() {
        return routingContext;
    }

    @Override
    public <T> T get(final String key) {
        return routingContext.get(key);
    }

    @Override
    public <T> T get(final String key, final T defaultValue) {
        final T value = routingContext.get(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public void put(final String key, final Object value) {
        routingContext.put(key, value);
    }

    @Override
    public void setTracingContext(final SpanContext spanContext) {
        this.spanContext = spanContext;
    }

    @Override
    public SpanContext getTracingContext() {
        return spanContext;
    }

    @Override
    public QoS getRequestedQos() {
        final String qos = routingContext.request().getHeader(Constants.HEADER_QOS_LEVEL);

        if (Strings.isNullOrEmpty(qos)) {
            return QoS.AT_MOST_ONCE;
        }

        final int qosLevel;
        try {
            qosLevel = Integer.parseInt(qos);
        } catch (final NumberFormatException e) {
            return QoS.UNKNOWN;
        }

        switch (qosLevel) {
            case 0:
                return QoS.AT_MOST_ONCE;
            case 1:
                return QoS.AT_LEAST_ONCE;
            default:
                return QoS.UNKNOWN;
        }
    }

    /**
     * Returns the underlying {@link RoutingContext}'s request.
     *
     * @return The underlying {@link RoutingContext}'s request.
     */
    public HttpServerRequest request() {
        return routingContext.request();
    }

    /**
     * Returns the underlying {@link RoutingContext}'s response.
     *
     * @return The underlying {@link RoutingContext}'s response.
     */
    public HttpServerResponse response() {
        return routingContext.response();
    }

    /**
     * Fails the underlying {@link RoutingContext} with the given cause.
     *
     * @param throwable The cause by which the underlying {@link RoutingContext} shall be failed.
     */
    public void fail(final Throwable throwable) {
        routingContext.fail(throwable);
    }

    /**
     * Gets the content type of the request payload.
     * <p>
     * The type is determined from the <em>Content-Type</em> HTTP header of the
     * request, if that header is set.
     * Otherwise, the {@linkplain MessageHelper#CONTENT_TYPE_OCTET_STREAM default
     * content type} is used.
     *
     * @return The type of the request payload.
     */
    public String getContentType() {

        final String contentType = routingContext.parsedHeaders().contentType().value();
        // contentType will be an empty string here if header isn't set
        return Strings.isNullOrEmpty(contentType) ? MessageHelper.CONTENT_TYPE_OCTET_STREAM : contentType;
    }

    /**
     * Gets the value of the {@link org.eclipse.hono.util.Constants#HEADER_TIME_TO_LIVE} HTTP header for a request.
     * If no such header can be found, the query is searched for containing a query parameter with the same key.
     *
     * @return The <em>time-to-live</em> duration or {@code null} if
     * <ul>
     *     <li>the request doesn't contain a {@link org.eclipse.hono.util.Constants#HEADER_TIME_TO_LIVE} header
     *     or query parameter.</li>
     *     <li>the contained value cannot be parsed as a Long</li>
     * </ul>
     */
    public Duration getTimeToLive() {

        try {
            return Optional.ofNullable(routingContext.request().getHeader(Constants.HEADER_TIME_TO_LIVE))
                    .map(ttlInHeader -> Long.parseLong(ttlInHeader))
                    .map(ttl -> Duration.ofSeconds(ttl))
                    .orElse(Optional.ofNullable(routingContext.request().getParam(Constants.HEADER_TIME_TO_LIVE))
                            .map(ttlInParam -> Long.parseLong(ttlInParam))
                            .map(ttl -> Duration.ofSeconds(ttl))
                            .orElse(null));
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Device getAuthenticatedDevice() {

        return Optional.ofNullable(routingContext.user()).map(user -> {
            if (DeviceUser.class.isInstance(user)) {
                return (Device) user;
            } else {
                return null;
            }
        }).orElse(null);
    }

    /**
     * Gets the value of the {@link org.eclipse.hono.util.Constants#HEADER_TIME_TILL_DISCONNECT} HTTP header for a request.
     * If no such header can be found, the query is searched for containing a query parameter with the same key.
     *
     * @return The time til disconnect or {@code null} if
     * <ul>
     *     <li>the request doesn't contain a {@link org.eclipse.hono.util.Constants#HEADER_TIME_TILL_DISCONNECT} header or query parameter.</li>
     *     <li>the contained value cannot be parsed as an Integer</li>
     * </ul>
     */
    public Integer getTimeTillDisconnect() {

        try {
            Optional<String> timeTilDisconnectHeader = Optional.ofNullable(request().getHeader(Constants.HEADER_TIME_TILL_DISCONNECT));

            if (timeTilDisconnectHeader.isEmpty()) {
                timeTilDisconnectHeader = Optional.ofNullable(request().getParam(Constants.HEADER_TIME_TILL_DISCONNECT));
            }

            if (timeTilDisconnectHeader.isPresent()) {
                return Integer.parseInt(timeTilDisconnectHeader.get());
            }
        } catch (final NumberFormatException e) {
        }

        return null;
    }

    /**
     * Gets the TTD value contained in a message received from a device.
     *
     * @return The TTD value
     */
    public MetricsTags.TtdStatus getTtdStatus() {
        return Optional.ofNullable((MetricsTags.TtdStatus) routingContext.get(MetricsTags.TtdStatus.class.getName()))
                .orElse(MetricsTags.TtdStatus.NONE);
    }
}
