/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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
import java.util.Objects;
import java.util.Optional;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.MessageFormatException;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.hono.client.ServiceInvocationException;

/**
 * Utility to prepare response with an error.
 */
public final class CoapErrorResponse {

    private CoapErrorResponse() {

    }

    /**
     * Checks if an error's cause is temporary only.
     *
     * @param error The error to check.
     * @return {@code true} if the cause is temporary only.
     */
    public static boolean isTemporaryError(final Throwable error) {
        return ServiceInvocationException.extractStatusCode(error) == HttpURLConnection.HTTP_UNAVAILABLE;
    }

    /**
     * Creates a CoAP response for an error.
     *
     * @param cause The cause of the error.
     * @param fallbackCode The response code to fall back to, if a more specific response code can not be determined
     *                     from the root cause.
     * @return The CoAP response containing the determined response code and error message.
     */
    public static Response newResponse(final Throwable cause, final ResponseCode fallbackCode) {

        final String message = Optional.ofNullable(cause)
                .map(ServiceInvocationException::getErrorMessageForExternalClient)
                .orElse(null);
        final ResponseCode code = toCoapCode(cause, fallbackCode);
        final Response response = newResponse(code, message);
        switch (code) {
        case SERVICE_UNAVAILABLE:
            // delay retry by 2 seconds, see http adapter, HttpUtils.serviceUnavailable(ctx, 2)
            response.getOptions().setMaxAge(2);
            break;
        default:
            break;
        }
        return response;
    }

    /**
     * Creates a CoAP response for a message and a response code.
     *
     * @param code The response code to use.
     * @param message The message to set as payload.
     * @return The CoAP response.
     * @throws NullPointerException if code is {@code null}.
     */
    public static Response newResponse(final ResponseCode code, final String message) {

        Objects.requireNonNull(code);
        final Response response = new Response(code);
        response.setPayload(message);
        response.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        return response;
    }

    /**
     * Gets a CoAP response code for an error.
     *
     * @param cause The cause of the error (may be {@code null}).
     * @param fallbackCode The response code to fall back to if a more specific response code can not be determined
     *                     from the cause.
     * @return The CoAP response code determined from the cause.
     * @throws NullPointerException if fallback code is {@code null}.
     */
    public static ResponseCode toCoapCode(final Throwable cause, final ResponseCode fallbackCode) {

        Objects.requireNonNull(fallbackCode);

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
        return fallbackCode;
    }
}
