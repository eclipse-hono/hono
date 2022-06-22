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


import java.util.List;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.tenant.TenantDto;
import org.eclipse.hono.service.management.tenant.TenantWithId;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A data access object for reading and writing tenant data from/to a persistent store.
 *
 */
public interface TenantDao {

    /**
     * Initially persists a tenant instance.
     *
     * @param tenantConfig The tenant's configuration.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with the instance's (initial) resource version if the instance has been
     *         persisted successfully.
     *         Otherwise, it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant configuration is {@code null}.
     */
    Future<String> create(TenantDto tenantConfig, SpanContext tracingContext);

    /**
     * Gets a tenant by its identifier.
     *
     * @param tenantId The identifier of the tenant to get.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if an instance with the given identifier exists, otherwise
     *         it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant identifier is {@code null}.
     */
    Future<TenantDto> getById(String tenantId, SpanContext tracingContext);

    /**
     * Gets a tenant by its identifier or alias.
     *
     * @param tenantId The identifier or alias of the tenant to get.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if an instance with the given identifier or alias exists, otherwise
     *         it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant identifier is {@code null}.
     */
    Future<TenantDto> getByIdOrAlias(String tenantId, SpanContext tracingContext);

    /**
     * Gets a tenant by the subject DN of one of its configured trusted certificate authorities.
     *
     * @param subjectDn The subject DN.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if a tenant with a matching trust anchor exists, otherwise
     *         it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if subject DN is {@code null}.
     */
    Future<TenantDto> getBySubjectDn(X500Principal subjectDn, SpanContext tracingContext);

    /**
     * Finds tenants by search criteria.
     *
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response. This allows to
     *                   retrieve the whole result set page by page.
     * @param filters A list of filters. The filters are predicates that objects in the result set must match.
     * @param sortOptions A list of sort options. The sortOptions specify properties to sort the result set by.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a set of matching tenants or failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException}, if the query could not be
     *         executed.
     * @throws NullPointerException if any of the parameters other than tracing context are {@code null}.
     * @throws IllegalArgumentException if page size is &lt;= 0 or page offset is negative.
     */
    Future<SearchResult<TenantWithId>> find(
            int pageSize,
            int pageOffset,
            List<Filter> filters,
            List<Sort> sortOptions,
            SpanContext tracingContext);

    /**
     * Updates an existing tenant.
     *
     * @param tenantConfig The tenant configuration to set.
     * @param resourceVersion The resource version that the instance is required to have.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with the new resource version if an instance with the given
     *         identifier and required resource version exists and has been updated.
     *         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant configuration or resource version are {@code null}.
     */
    Future<String> update(
            TenantDto tenantConfig,
            Optional<String> resourceVersion,
            SpanContext tracingContext);

    /**
     * Gets the Tenants count.
     *
     * @param filter Filter for the Mongo DB count query 
     *                       or {@code null} if the total Tenant count must be returned.
     * @param tracingContext The context to track the processing of the request in.
     *                       If {@code null} is passed, no Span will be created.
     *
     * @return The Tenants count.
     */
    Future<Integer> count(JsonObject filter, SpanContext tracingContext);

    /**
     * Deletes an existing tenant.
     *
     * @param tenantId The identifier of the tenant to delete.
     * @param resourceVersion The resource version that the instance is required to have.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if a tenant matching the given identifier and resource version
     *         exists and has been deleted.
     *         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant ID or resource version are {@code null}.
     */
    Future<Void> delete(String tenantId, Optional<String> resourceVersion, SpanContext tracingContext);
}
