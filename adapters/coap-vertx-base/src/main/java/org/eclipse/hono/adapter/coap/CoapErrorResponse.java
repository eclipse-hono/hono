/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.MessageFormatException;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.client.ServiceInvocationException;

/**
 * Utility send response with an error.
 */
public class CoapErrorResponse {

    private CoapErrorResponse() {

    }

    /**
     * Check, if reported error cause indicates a temporary error.
     * 
     * @param cause reported error cause
     * @return {@code true}, if error is temporary and the client may retry its action, {@code false}, otherwise, when
     *         the client should not repeat this action.
     */
    public static boolean isTemporaryError(final Throwable cause) {
        if (ServiceInvocationException.class.isInstance(cause)) {
            return ((ServiceInvocationException) cause).getErrorCode() == HttpURLConnection.HTTP_UNAVAILABLE;
        }
        return false;
    }

    /**
     * Respond the coap exchange with the provide error cause.
     * 
     * @param exchange coap exchange to be responded
     * @param cause error cause
     * @return The response code included in the CoAP response.
     */
    public static ResponseCode respond(final CoapExchange exchange, final Throwable cause) {
        return respond(exchange, cause, ResponseCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * Respond the coap exchange with the provide error cause.
     * 
     * Convert http-code into coap-code, if available. Add cause message as payload for response.
     * 
     * @param exchange coap exchange to be responded
     * @param cause error cause
     * @param defaultCode default response code, if a more specific response code is not available.
     * @return The response code included in the CoAP response.
     */
    public static ResponseCode respond(final CoapExchange exchange, final Throwable cause, final ResponseCode defaultCode) {

        final String message = cause == null ? null : cause.getMessage();
        final ResponseCode code = toCoapCode(cause, defaultCode);
        switch (code) {
        case SERVICE_UNAVAILABLE:
            // delay retry by 2 seconds, see http adapter, HttpUtils.serviceUnavailable(ctx, 2)
            exchange.setMaxAge(2);
            break;
        default:
            break;
        }
        respond(exchange, message, code);
        return code;
    }

    /**
     * Respond the coap exchange with the provide error cause.
     * 
     * @param exchange coap exchange to be responded
     * @param message error message sent as payload.
     * @param code response code.
     */
    public static void respond(final CoapExchange exchange, final String message, final ResponseCode code) {
        final Response response = new Response(code);
        response.setPayload(message);
        response.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        exchange.respond(response);
    }

    /**
     * Gets a CoAP response code for an error.
     * 
     * @param cause The cause of the error.
     * @param defaultCode The default CoAP response code to use if the error cannot be mapped.
     * @return The CoAP response code.
     */
    public static ResponseCode toCoapCode(final Throwable cause, final ResponseCode defaultCode) {

        if (ServiceInvocationException.class.isInstance(cause)) {
            final int statusCode = ((ServiceInvocationException) cause).getErrorCode();

            final int codeClass = statusCode / 100;
            final int codeDetail = statusCode % 100;
            if (0 < codeClass && codeClass < 8 && 0 <= codeDetail && codeDetail < 32) {
                try {
                    return ResponseCode.valueOf(codeClass << 5 | codeDetail);
                } catch (MessageFormatException e) {
                    // ignore
                }
            }
        }
        return defaultCode;
    }
}
