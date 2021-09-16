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


package org.eclipse.hono.deviceregistry.mongodb.model;

import java.util.Optional;

import org.eclipse.hono.service.management.credentials.CredentialsDto;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A data access object for reading and writing device data from/to a persistent store.
 *
 */
public interface CredentialsDao {

    /**
     * Initially persists a set of credentials.
     *
     * @param credentials The credentials to persist.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with the initial resource version if the credentials have been
     *         persisted successfully.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if credentials are {@code null}.
     */
    Future<String> create(CredentialsDto credentials, SpanContext tracingContext);

    /**
     * Gets device credentials by the device's identifier.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if an instance matching the given criteria exists, otherwise
     *         it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant or device ID are {@code null}.
     */
    Future<CredentialsDto> getByDeviceId(
            String tenantId,
            String deviceId,
            SpanContext tracingContext);

    /**
     * Gets device credentials by authentication identifier and type.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param authId The identifier that the device uses for authentication.
     * @param type The type of credentials to retrieve.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if an instance matching the given criteria exists, otherwise
     *         it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant ID, authentication identifier or type are {@code null}.
     */
    Future<CredentialsDto> getByAuthIdAndType(String tenantId, String authId, String type, SpanContext tracingContext);

    /**
     * Updates existing credentials of a device.
     *
     * @param credentials The credentials to set.
     * @param resourceVersion The resource version that the credentials instance is required to have.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with the new resource version if credentials with matching
     *         identifiers and resource version exist and have been updated.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if credentials or resource version are {@code null}.
     */
    Future<String> update(
            CredentialsDto credentials,
            Optional<String> resourceVersion,
            SpanContext tracingContext);

    /**
     * Deletes an existing set of credentials.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param resourceVersion The resource version that the credentials are required to have.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if credentials matching the given identifiers and resource version
     *         exist and have been deleted.
     *         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of tenant, device ID or resource version are {@code null}.
     */
    Future<Void> delete(String tenantId, String deviceId, Optional<String> resourceVersion, SpanContext tracingContext);

    /**
     * Deletes all credentials of devices belonging to a tenant.
     *
     * @param tenantId The tenant that the devices belong to.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if all credentials of devices that belong to the given tenant
     *         have been deleted.
     *         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    Future<Void> delete(String tenantId, SpanContext tracingContext);
}
