/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.http;

import static java.net.HttpURLConnection.*;

import java.util.Objects;

import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractVertxHttpServer;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * Base class for a Vert.x based Hono protocol adapter that uses the HTTP protocol. 
 * It provides access to the Telemetry and Event API.
 * 
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractVertxBasedHttpProtocolAdapter<T extends ServiceConfigProperties> extends AbstractVertxHttpServer<T> {

    /**
     * Uploads the body of an HTTP request as a telemetry message to the Hono server.
     * <p>
     * This method simply invokes {@link #uploadTelemetryMessage(RoutingContext, String, String, Buffer, String)}
     * with objects retrieved from the routing context.
     *
     * @param ctx The context to retrieve the message payload and content type from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadTelemetryMessage(final RoutingContext ctx, final String tenant, final String deviceId) {

        uploadTelemetryMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                ctx.getBody(),
                getContentType(ctx));
    }

    /**
     * Uploads a telemetry message to the Hono server.
     * <p>
     * Depending on the outcome of the attempt to upload the message to Hono, the HTTP response's code is
     * set as follows:
     * <ul>
     * <li>202 (Accepted) - if the telemetry message has been sent to the Hono server.</li>
     * <li>400 (Bad Request) - if the message payload is {@code null} or empty or if the content type is {@code null}.</li>
     * <li>503 (Service Unavailable) - if the message could not be sent to the Hono server, e.g. due to lack of connection or credit.</li>
     * </ul>
     * 
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @param contentType The content type of the message payload.
     * @throws NullPointerException if any of response, tenant or device ID is {@code null}.
     */
    public final void uploadTelemetryMessage(final RoutingContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {

        doUploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                payload,
                contentType,
                getTelemetrySender(tenant));
    }

    /**
     * Uploads the body of an HTTP request as an event message to the Hono server.
     * <p>
     * This method simply invokes {@link #uploadEventMessage(RoutingContext, String, String, Buffer, String)}
     * with objects retrieved from the routing context.
     *
     * @param ctx The context to retrieve the message payload and content type from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadEventMessage(final RoutingContext ctx, final String tenant, final String deviceId) {

        uploadEventMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                ctx.getBody(),
                getContentType(ctx));
    }

    /**
     * Uploads an event message to the Hono server.
     * <p>
     * Depending on the outcome of the attempt to upload the message to Hono, the HTTP response's code is
     * set as follows:
     * <ul>
     * <li>202 (Accepted) - if the telemetry message has been sent to the Hono server.</li>
     * <li>400 (Bad Request) - if the message payload is {@code null} or empty or if the content type is {@code null}.</li>
     * <li>503 (Service Unavailable) - if the message could not be sent to the Hono server, e.g. due to lack of connection or credit.</li>
     * </ul>
     * 
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @param contentType The content type of the message payload.
     * @throws NullPointerException if any of response, tenant or device ID is {@code null}.
     */
    public final void uploadEventMessage(final RoutingContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {

        doUploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                payload,
                contentType,
                getEventSender(tenant));
    }

    private void doUploadMessage(final RoutingContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final String contentType, final Future<MessageSender> senderTracker) {

        if (contentType == null) {
            badRequest(ctx.response(), String.format("%s header is missing", HttpHeaders.CONTENT_TYPE));
        } else if (payload == null || payload.length() == 0) {
            badRequest(ctx.response(), "missing body");
        } else {
            
            final Future<String> tokenTracker = getRegistrationAssertionHeader(ctx, tenant, deviceId);

            CompositeFuture.all(tokenTracker, senderTracker).setHandler(s -> {
                if (s.failed()) {
                    if (tokenTracker.failed()) {
                        LOG.debug("could not get registration assertion [tenant: {}, device: {}]", tenant, deviceId, s.cause());
                        endWithStatus(ctx.response(), HTTP_FORBIDDEN, null, null, null);
                    } else {
                        serviceUnavailable(ctx.response(), 5);
                    }
                } else {
                    sendToHono(ctx.response(), deviceId, payload, contentType, tokenTracker.result(), senderTracker.result());
                }
            });
        }
    }

    private void sendToHono(final HttpServerResponse response, final String deviceId, final Buffer payload,
            final String contentType, final String token, final MessageSender sender) {

        boolean accepted = sender.send(deviceId, payload.getBytes(), contentType, token);
        if (accepted) {
            response.setStatusCode(HTTP_ACCEPTED).end();
        } else {
            serviceUnavailable(response, 2,
                    "resource limit exceeded, please try again later",
                    "text/plain");
        }
    }

}
