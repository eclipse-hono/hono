/**
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
 */

package org.eclipse.hono.adapter.coap;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MapBasedExecutionContext;

import io.micrometer.core.instrument.Timer.Sample;
import io.vertx.core.buffer.Buffer;

/**
 * A dictionary of relevant information required during the processing of a CoAP request message published by a device.
 *
 */
public final class CoapContext extends MapBasedExecutionContext {

    /**
     * The query parameter which is used to indicate an empty notification.
     */
    public static final String PARAM_EMPTY_CONTENT = "empty";

    private final CoapExchange exchange;

    private Sample timer;

    private CoapContext(final CoapExchange exchange) {
        this.exchange = exchange;
    }

    /**
     * Creates a new context for a CoAP request.
     * 
     * @param request The CoAP exchange representing the request.
     * @return The context.
     * @throws NullPointerException if request is {@code null}.
     */
    public static CoapContext fromRequest(final CoapExchange request) {
        Objects.requireNonNull(request);
        return new CoapContext(request);
    }

    /**
     * Creates a new context for a CoAP request.
     * 
     * @param request The CoAP exchange representing the request.
     * @param timer The object to use for measuring the time it takes to process the request.
     * @return The context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static CoapContext fromRequest(final CoapExchange request, final Sample timer) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(timer);

        final CoapContext result = new CoapContext(request);
        result.timer = timer;
        return result;
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
     * Sends a response with the response code to the device.
     * 
     * @param responseCode The code to set in the response.
     */
    public void respondWithCode(final ResponseCode responseCode) {
        respond(new Response(responseCode));
    }

    /**
     * Sends a response to the device.
     * 
     * @param response The response to sent.
     */
    public void respond(final Response response) {
        exchange.respond(response);
    }

    /**
     * Gets the integer value of the provided query parameter.
     *
     * @param parameterName name of the query parameter
     * @return integer value or {@code null} if
     *         <ul>
     *         <li>the request doesn't contain the provided query parameter.</li>
     *         <li>the contained value cannot be parsed as an Integer</li>
     *         </ul>
     */
    private Integer getIntegerQueryParameter(final String parameterName) {

        try {
            final Optional<String> value = Optional
                    .ofNullable(exchange.getQueryParameter(parameterName));

            if (value.isPresent()) {
                return Integer.parseInt(value.get());
            }
        } catch (final NumberFormatException e) {
        }

        return null;
    }

}
