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

package org.eclipse.hono.service.management;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.EventBusMessage;

import io.vertx.core.json.JsonObject;

/**
 * Get a generic response.
 * 
 * @param <T> Payload type.
 */
public class Result<T> {

    private final int status;
    private final T payload;
    private final Optional<CacheDirective> cacheDirective;

    /**
     * Create a new instance.
     * 
     * @param status The HTTP status code.
     * @param payload The payload, may be {@code null}.
     * @param cacheDirective The caching directive, may be {@link Optional#empty()}, but not {@code null}.
     */
    protected Result(final int status, final T payload, final Optional<CacheDirective> cacheDirective) {
        Objects.requireNonNull(cacheDirective);

        this.status = status;
        this.payload = payload;
        this.cacheDirective = cacheDirective;
    }

    public Optional<CacheDirective> getCacheDirective() {
        return this.cacheDirective;
    }

    public T getPayload() {
        return this.payload;
    }

    public int getStatus() {
        return this.status;
    }

    public boolean isError() {
        return HttpUtils.isError(this.status);
    }

    /**
     * Create new response.
     * 
     * @param <T> Payload type.
     * @param <R> Response Type.
     * @param status Response status.
     * @param supplier Response type supplier.
     * @return New response of requested type.
     */
    public static <T, R extends Result<T>> R from(final int status, final IntFunction<R> supplier) {
        return supplier.apply(status);
    }

    /**
     * Create new response.
     * 
     * @param <T> Payload type.
     * @param status Response status.
     * @return New response of requested type.
     */
    public static <T> Result<T> from(final int status) {
        return new Result<>(status, null, Optional.empty());
    }

    /**
     * Create a response from the request.
     * 
     * @param request The request to use as base.
     * @param payloadMapper The mapper for mapping the payload to the JSON object required by the
     *            {@link EventBusMessage}.
     * @return A response message.
     */
    public EventBusMessage createResponse(final EventBusMessage request,
            final Function<T, JsonObject> payloadMapper) {

        final var result = request
                .getResponse(getStatus());

        this.cacheDirective.ifPresent(result::setCacheDirective);
        if (this.payload != null) {
            result.setJsonPayload(payloadMapper.apply(this.payload));
        }

        return result;

    }
}
