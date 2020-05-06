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

package org.eclipse.hono.tests.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.client.ServiceInvocationException;

import io.vertx.core.Future;

/**
 * An exception explicitly for HTTP protocol adapter errors.
 * 
 */
public class HttpProtocolException extends ServiceInvocationException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for an error code, a detail message and a root cause.
     * 
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    public HttpProtocolException(final int errorCode, final String msg, final Throwable cause) {
        super(errorCode, msg, cause);
    }

    /**
     * Creates a new exception for an error code and a detail message.
     * 
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    public HttpProtocolException(final int errorCode, final String msg) {
        super(errorCode, msg);
    }

    /**
     * Creates a new exception for an error code and a root cause.
     * 
     * @param errorCode The code representing the erroneous outcome.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    public HttpProtocolException(final int errorCode, final Throwable cause) {
        super(errorCode, cause);
    }

    /**
     * Creates a new exception for an error code.
     * 
     * @param errorCode The code representing the erroneous outcome.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    public HttpProtocolException(final int errorCode) {
        super(errorCode);
    }

    /**
     * Wrap a {@link ServiceInvocationException} into an {@link HttpProtocolException}.
     * <p>
     * Converts any {@link ServiceInvocationException} into a {@link HttpProtocolException} with the same status code
     * and message. Any other exception type is left unchanged.
     * <p>
     * Example of usage: <code><pre>
     * helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, device, PWD)
                .compose(ok -> {
                    // call to http protocol adapter ...
                    httpClient.create()
                        .recover(HttpProtocolException::transformInto)
                })
                .onComplete(ctx.asyncAssertFailure(t -> {
                    // ... evaluate only http protocol adapter errors
                    HttpProtocolException.assertProtocolError(HttpURLConnection.HTTP_NOT_FOUND, t);
                }));
     * </pre></code>
     * <p>
     * This flags the result error of the {@code httpClient.create()} as an error of the http protocol adapter. And it
     * later on asserts that this is really the case. Not using that construct would make it impossible in the final
     * {@code asyncAssertFailure} block to differentiate between an error from the {@code addDeviceForTenant} method or
     * the actual HTTP call to the protocol adapter.
     * 
     * @param <T> The return type of the future.
     * @param t The error to transform.
     * @return A completed future with the transformed error.
     */
    public static <T> Future<T> transformInto(final Throwable t) {
        if (t instanceof ServiceInvocationException) {
            return Future.failedFuture(
                    new HttpProtocolException(((ServiceInvocationException) t).getErrorCode(), t.getMessage(), t));
        }
        return Future.failedFuture(t);
    }

    /**
     * Assert that an error is an HTTP protocol adapter error and has the expected status code.
     * 
     * @param expectedStatusCode The expected status code.
     * @param actualError The error to test.
     */
    public static void assertProtocolError(final int expectedStatusCode, final Throwable actualError) {
        assertThat(actualError).isInstanceOf(HttpProtocolException.class);
        assertThat(((HttpProtocolException) actualError).getErrorCode()).isEqualTo(expectedStatusCode);
    }

}
