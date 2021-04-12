/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.coap.lwm2m;

import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationUpdate;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A service for keeping track of registration info of LwM2M devices.
 *
 */
public interface LwM2MRegistrationStore {

    /**
     * Adds registration information for a device.
     *
     * @param registration The LwM2M registration data for the device.
     * @param tracingContext The Open Tracing context to use for tracking the processing of the request
     *                       or {@code null} if no tracing should be done.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the registration information has been added newly or has replaced
     *         an existing registration for the device.
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException} if the
     *         registration information could not be stored.
     * @throws NullPointerException if registration is {@code null}.
     */
    Future<Void> addRegistration(Registration registration, SpanContext tracingContext);

    /**
     * Updates registration information for a device.
     *
     * @param registrationUpdate The LwM2M registration data to update for the device.
     * @param tracingContext The Open Tracing context to use for tracking the processing of the request
     *                       or {@code null} if no tracing should be done.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the registration information has been updated successfully.
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException} if the
     *         registration information could not be updated.
     * @throws NullPointerException if registration is {@code null}.
     */
    Future<Void> updateRegistration(RegistrationUpdate registrationUpdate, SpanContext tracingContext);

    /**
     * Removes registration information for a device.
     *
     * @param registrationId The identifier of the registration to remove.
     * @param tracingContext The Open Tracing context to use for tracking the processing of the request
     *                       or {@code null} if no tracing should be done.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the registration information has been removed successfully.
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException} if the
     *         registration information could not be updated.
     * @throws NullPointerException if registration is {@code null}.
     */
    Future<Void> removeRegistration(String registrationId, SpanContext tracingContext);
}
