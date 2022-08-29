/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.util.CacheDirective;

import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * An operation response, including a resource version.
 *
 * @param <T> The type of the payload.
 */
public final class OperationResult<T> extends Result<T> {

    private final Optional<String> resourceVersion;

    /**
     * Create a new instance.
     *
     * @param status The HTTP status code.
     * @param payload The payload, may be {@code null}.
     * @param cacheDirective The caching directive, may be {@link Optional#empty()}, but not {@code null}.
     * @param resourceVersion The resource version, may be {@link Optional#empty()}, but not {@code null}.
     */
    private OperationResult(final int status, final T payload,
            final Optional<CacheDirective> cacheDirective, final Optional<String> resourceVersion) {
        super(status, payload, cacheDirective);
        this.resourceVersion = resourceVersion;
    }

    /**
     * Gets the version of the resource conveyed in this result.
     *
     * @return The version.
     */
    public Optional<String> getResourceVersion() {
        return this.resourceVersion;
    }

    /**
     * Create a new "ok" response.
     *
     * @param status The status of the response.
     * @param payload The payload of the response.
     * @param directive The cache directory of the response.
     * @param resourceVersion The optional resource version of the response.
     *
     * @param <T> The type of the payload.
     *
     * @return The new response object.
     */
    public static <T> OperationResult<T> ok(final int status, final T payload,
            final Optional<CacheDirective> directive, final Optional<String> resourceVersion) {
        Objects.requireNonNull(resourceVersion);

        return new OperationResult<>(status, payload, directive, resourceVersion);
    }

    /**
     * Create "empty" response.
     *
     * @param <T> The of payload.
     * @param status Response status.
     * @return New instance.
     */
    public static <T> OperationResult<T> empty(final int status) {
        return new OperationResult<>(status, null, Optional.empty(), Optional.empty());
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("resourceVersion", this.resourceVersion);
    }
}
