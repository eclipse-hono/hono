/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.http.HttpServerSpanHelper;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * Represents the context for the handling of a Vert.x HTTP request, wrapping the Vert.x {@link RoutingContext} as well
 * as implementing the {@link org.eclipse.hono.util.ExecutionContext} interface.
 */
public final class HttpContext implements TelemetryExecutionContext {

    private final RoutingContext routingContext;
    private final boolean eventEndpoint;
    private final QoS requestedQos;
    private final ResourceIdentifier requestedResource;

    private HttpContext(final RoutingContext routingContext) {
        this.routingContext = Objects.requireNonNull(routingContext);
        this.requestedResource = Optional.ofNullable(routingContext.request().path())
                .map(path -> {
                    final String resourcePath = path.substring(1);
                    return ResourceIdentifier.fromString(URLDecoder.decode(resourcePath, StandardCharsets.UTF_8));
                })
                .orElseThrow(() -> new IllegalArgumentException("HTTP request contains no URI"));

        this.eventEndpoint = EventConstants.isEventEndpoint(requestedResource.getEndpoint());
        this.requestedQos = determineRequestedQos(routingContext);
    }

    private QoS determineRequestedQos(final RoutingContext context) {
        final String qos = context.request().getHeader(Constants.HEADER_QOS_LEVEL);

        if (Strings.isNullOrEmpty(qos)) {
            if (isEventEndpoint()) {
                return QoS.AT_LEAST_ONCE;
            } else {
                return QoS.AT_MOST_ONCE;
            }
        }

        final int qosLevel;
        try {
            qosLevel = Integer.parseInt(qos);
        } catch (final NumberFormatException e) {
            return null;
        }

        switch (qosLevel) {
            case 0:
                return QoS.AT_MOST_ONCE;
            case 1:
                return QoS.AT_LEAST_ONCE;
            default:
                return null;
        }
    }

    /**
     * Creates a new HttpContext.
     *
     * @param routingContext The RoutingContext to wrap.
     *
     * @return The created HttpContext.
     * @throws NullPointerException if routingContext is {@code null}.
     * @throws IllegalArgumentException if the HTTP request has no URI or the URI contains illegal characters.
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
    public SpanContext getTracingContext() {
        return Optional.ofNullable(getTracingSpan()).map(Span::context).orElse(null);
    }

    @Override
    public Span getTracingSpan() {
        return HttpServerSpanHelper.serverSpan(routingContext);
    }

    @Override
    public QoS getRequestedQos() {
        return requestedQos;
    }

    /**
     * Checks if the requested QoS is acceptable for this message's type.
     *
     * @return {@code true} if the requested QoS is acceptable.
     */
    public boolean hasValidQoS() {
        if (isEventEndpoint()) {
            return requestedQos == QoS.AT_LEAST_ONCE;
        } else {
            return requestedQos != null;
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
     * Gets the value of the <em>Content-Type</em> HTTP header for a request.
     *
     * @return The content type or {@code null} if the request doesn't contain a
     *         <em>Content-Type</em> header.
     */
    public String getContentType() {

        final String contentType = routingContext.parsedHeaders().contentType().value();
        // contentType will be an empty string here if header isn't set
        return Strings.isNullOrEmpty(contentType) ? null : contentType;
    }

    /**
     * Checks if this request is for uploading an event.
     *
     * @return {@code true} if the request URI starts with the event endpoint.
     */
    public boolean isEventEndpoint() {
        return eventEndpoint;
    }

    /**
     * Gets the resource corresponding to this request's URI.
     *
     * @return The resource identifier.
     */
    public ResourceIdentifier getRequestedResource() {
        return requestedResource;
    }

    /**
     * {@inheritDoc}
     *
     * @return An optional containing the <em>time-to-live</em> duration or an empty optional if
     * <ul>
     *     <li>the request doesn't contain a {@link org.eclipse.hono.util.Constants#HEADER_TIME_TO_LIVE} header
     *     or query parameter.</li>
     *     <li>the contained value cannot be parsed as a Long</li>
     * </ul>
     */
    @Override
    public Optional<Duration> getTimeToLive() {

        if (!isEventEndpoint()) {
            return Optional.empty();
        }

        try {
            final Duration ttl = Optional.ofNullable(routingContext.request().getHeader(Constants.HEADER_TIME_TO_LIVE))
                    .or(() -> Optional.ofNullable(routingContext.request().getParam(Constants.HEADER_TIME_TO_LIVE)))
                    .map(Long::parseLong)
                    .map(Duration::ofSeconds)
                    .orElse(null);
            return Optional.ofNullable(ttl);
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeviceUser getAuthenticatedDevice() {

        return Optional.ofNullable(routingContext.user())
                .filter(DeviceUser.class::isInstance)
                .map(DeviceUser.class::cast)
                .orElse(null);
    }

    /**
     * Gets the value of the {@link org.eclipse.hono.util.Constants#HEADER_TIME_TILL_DISCONNECT} HTTP header for a request.
     * If no such header can be found, the query is searched for containing a query parameter with the same key.
     *
     * @return The time until disconnect or {@code null} if
     *         <ul>
     *           <li>the request doesn't contain a {@value org.eclipse.hono.util.Constants#HEADER_TIME_TILL_DISCONNECT}
     *           header or query parameter, or</li>
     *           <li>the contained value cannot be parsed as an integer.</li>
     *         </ul>
     */
    public Integer getTimeTillDisconnect() {

        return Optional.ofNullable(request().getHeader(Constants.HEADER_TIME_TILL_DISCONNECT))
                .or(() -> Optional.ofNullable(request().getParam(Constants.HEADER_TIME_TILL_DISCONNECT)))
                .map(ttd -> {
                    try {
                        return Integer.parseInt(ttd);
                    } catch (final NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
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

    /**
     * {@inheritDoc}
     *
     * @return The request URI.
     */
    @Override
    public String getOrigAddress() {
        return routingContext.request().uri();
    }
}
