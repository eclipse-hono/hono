/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.util;

import java.util.Objects;

import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.service.HealthCheckProvider;

import io.vertx.ext.healthchecks.HealthCheckHandler;


/**
 * Adapts a {@link org.eclipse.hono.client.util.ServiceClient} to the {@link HealthCheckProvider} interface.
 *
 */
public final class ServiceClientAdapter implements HealthCheckProvider {

    private final ServiceClient serviceClient;

    private ServiceClientAdapter(final ServiceClient serviceClient) {
        this.serviceClient = Objects.requireNonNull(serviceClient);
    }

    /**
     * Creates a new adapter for a service client.
     *
     * @param serviceClient The client to adapt.
     * @return The adapter.
     * @throws NullPointerException if client is {@code null}.
     */
    public static ServiceClientAdapter forClient(final ServiceClient serviceClient) {
        return new ServiceClientAdapter(serviceClient);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to {@link ServiceClient#registerReadinessChecks(HealthCheckHandler)}.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        serviceClient.registerReadinessChecks(readinessHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to {@link ServiceClient#registerLivenessChecks(HealthCheckHandler)}.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        serviceClient.registerLivenessChecks(livenessHandler);
    }

}
