/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests;

import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.Credentials;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateSecret;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.UrlEscapers;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.client.predicate.ResponsePredicateResult;

/**
 * A client for accessing the Device Registry's HTTP resources for the Device Registration, Credentials and Tenant API.
 *
 */
public final class DeviceRegistryHttpClient {

    /**
     * The URI pattern for addressing a tenant instance.
     */
    public static final String TEMPLATE_URI_TENANT_INSTANCE = String.format("/%s/%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.TENANT_HTTP_ENDPOINT);

    /**
     * The URI pattern for addressing a device instance.
     */
    public static final String TEMPLATE_URI_REGISTRATION_WITHOUT_ID = String.format("/%s/%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.DEVICES_HTTP_ENDPOINT);

    /**
     * The URI pattern for addressing adding a device without id.
     */
    public static final String TEMPLATE_URI_REGISTRATION_INSTANCE = String.format("/%s/%s/%%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.DEVICES_HTTP_ENDPOINT);

    /**
     * The URI pattern for addressing the credentials of a device.
     */
    public static final String TEMPLATE_URI_CREDENTIALS_BY_DEVICE = String.format("/%s/%s/%%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.CREDENTIALS_HTTP_ENDPOINT);
    /**
     * The URI pattern for addressing a device's credentials of a specific type.
     */
    public static final String TEMPLATE_URI_CREDENTIALS_INSTANCE = String.format("/%s/%s/%%s/%%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.CREDENTIALS_HTTP_ENDPOINT);

    /**
     * The URI pattern for searching devices.
     */
    public static final String TEMPLATE_URI_SEARCH_DEVICES_INSTANCE = String.format("/%s/%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.DEVICES_HTTP_ENDPOINT);

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistryHttpClient.class);

    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private final CrudHttpClient httpClient;
    private final Map<String, Object> tenantExtensions;
    private final String authenticationHeaderValue;

    /**
     * Creates a new client for a host and port.
     *
     * @param vertx The vert.x instance to use.
     * @param host The host to invoke the operations on.
     * @param port The port that the service is bound to.
     * @param tenantExtensions The value of the extensions field for all tenants to be created.
     *
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public DeviceRegistryHttpClient(final Vertx vertx, final String host, final int port, final Map<String, Object> tenantExtensions) {
        this.httpClient = new CrudHttpClient(vertx, host, port);
        this.tenantExtensions = Objects.requireNonNull(tenantExtensions);
        this.authenticationHeaderValue = IntegrationTestSupport.getRegistryManagementApiAuthHeader();
    }

    private static String credentialsByDeviceUri(final String tenant, final String deviceId) {
        return String.format(
                TEMPLATE_URI_CREDENTIALS_BY_DEVICE,
                Optional.ofNullable(tenant)
                    .map(t -> UrlEscapers.urlPathSegmentEscaper().escape(t))
                    .orElse(""),
                Optional.ofNullable(deviceId)
                    .map(d -> UrlEscapers.urlPathSegmentEscaper().escape(d))
                    .orElse(""));
    }

    private static String tenantInstanceUri(final String tenant) {
        return String.format(
                TEMPLATE_URI_TENANT_INSTANCE,
                Optional.ofNullable(tenant)
                    .map(t -> UrlEscapers.urlPathSegmentEscaper().escape(t))
                    .orElse(""));
    }

    private static String registrationInstanceUri(final String tenant, final String deviceId) {
        return String.format(
                TEMPLATE_URI_REGISTRATION_INSTANCE,
                Optional.ofNullable(tenant)
                    .map(t -> UrlEscapers.urlPathSegmentEscaper().escape(t))
                    .orElse(""),
                Optional.ofNullable(deviceId)
                    .map(d -> UrlEscapers.urlPathSegmentEscaper().escape(d))
                    .orElse(""));
    }

    private static String registrationWithoutIdUri(final String tenant) {
        return String.format(
                TEMPLATE_URI_REGISTRATION_WITHOUT_ID,
                Optional.ofNullable(tenant)
                    .map(t -> UrlEscapers.urlPathSegmentEscaper().escape(t))
                    .orElse(""));
    }

    private static String searchDevicesUri(final String tenant) {
        return String.format(TEMPLATE_URI_SEARCH_DEVICES_INSTANCE, tenant);
    }

    private static String searchTenantsUri() {
        return String.format("/%s/%s",
                RegistryManagementConstants.API_VERSION, RegistryManagementConstants.TENANT_HTTP_ENDPOINT);
    }

    private MultiMap getRequestHeaders() {
        return getRequestHeaders(null);
    }

    private MultiMap getRequestHeaders(final String contentType) {
        final var headers = MultiMap.caseInsensitiveMultiMap();
        Optional.ofNullable(contentType)
            .ifPresent(v -> headers.add(HttpHeaders.CONTENT_TYPE, v));
        Optional.ofNullable(authenticationHeaderValue)
            .ifPresent(v -> headers.add(HttpHeaders.AUTHORIZATION, v));
        return headers;
    }

    // tenant management

    /**
     * Adds configuration information for a tenant without an ID nor payload.
     * <p>
     * The tenant identifier will be generated by the service implementation.
     * This method simply invokes {@link #addTenant(String, Tenant, String, int)} with
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @return A future indicating the outcome of the operation. The future will succeed if the tenant has been created
     *         successfully. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> addTenant() {
        return addTenant(null, (Tenant) null, null, HttpURLConnection.HTTP_CREATED);
    }

    /**
     * Adds configuration information for a tenant without a payload.
     * <p>
     * This method simply invokes {@link #addTenant(String, Tenant, String, int)} with
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The id of the tenant to add.
     * @return A future indicating the outcome of the operation. The future will succeed if the tenant has been created
     *         successfully. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> addTenant(final String tenantId) {
        return addTenant(tenantId, (Tenant) null, null, HttpURLConnection.HTTP_CREATED);
    }

    /**
     * Adds configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #addTenant(String, Tenant, int)} with
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The id of the tenant to add.
     * @param requestPayload The request payload as specified by the Tenant management API.
     * @return A future indicating the outcome of the operation. The future will succeed if the tenant has been created
     *         successfully. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> addTenant(final String tenantId, final Tenant requestPayload) {
        return addTenant(tenantId, requestPayload, HttpURLConnection.HTTP_CREATED);
    }

    /**
     * Adds configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #addTenant(String, Tenant, String, int)} with <em>application/json</em> as
     * content type and {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The id of the tenant to add.
     * @param requestPayload The request payload as specified by the Tenant management API.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> addTenant(final String tenantId, final Tenant requestPayload,
            final int expectedStatusCode) {
        return addTenant(tenantId, requestPayload, CONTENT_TYPE_APPLICATION_JSON, expectedStatusCode);
    }

    /**
     * Adds configuration information for a tenant.
     *
     * @param tenantId The id of the tenant to add.
     * @param requestPayload The request payload as specified by the Tenant management API.
     * @param contentType The content type to set in the request.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> addTenant(final String tenantId, final Tenant requestPayload, final String contentType,
            final int expectedStatusCode) {

        final Tenant enrichedTenant = requestPayload != null ? new Tenant(requestPayload) : new Tenant().setEnabled(true);

        if (tenantExtensions != null && !tenantExtensions.isEmpty()) {
            final HashMap<String, Object> extensions = new HashMap<>(enrichedTenant.getExtensions());
            extensions.putAll(tenantExtensions);
            enrichedTenant.setExtensions(extensions);
        }

        return addTenant(
                tenantId,
                JsonObject.mapFrom(enrichedTenant),
                contentType != null ? contentType : "application/json",
                expectedStatusCode);
    }

    /**
     * Adds configuration information for a tenant.
     *
     * @param tenantId The id of the tenant to add.
     * @param requestPayload The request payload as specified by the Tenant management API.
     * @param contentType The content type to set in the request.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> addTenant(
            final String tenantId,
            final JsonObject requestPayload,
            final String contentType,
            final int expectedStatusCode) {

        final String uri = tenantInstanceUri(tenantId);
        Optional.ofNullable(requestPayload)
            .ifPresent(p -> LOG.debug("adding tenant: {}", p.encodePrettily()));
        return httpClient.create(
                uri,
                Optional.ofNullable(requestPayload).map(JsonObject::toBuffer).orElse(null),
                getRequestHeaders(contentType),
                ResponsePredicate.status(expectedStatusCode));
    }

    /**
     * Gets configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #getTenant(String, int)} with {@link HttpURLConnection#HTTP_OK} as the expected
     * status code.
     *
     * @param tenantId The tenant to get information for.
     * @return A future indicating the outcome of the operation. The future will contain the response if the
     *         request succeeded. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> getTenant(final String tenantId) {
        return getTenant(tenantId, HttpURLConnection.HTTP_OK);
    }

    /**
     * Gets configuration information for a tenant.
     *
     * @param tenantId The tenant to get information for.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will contain the response if the
     *         response contained the expected status code. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> getTenant(final String tenantId, final int expectedStatusCode) {

        final String uri = tenantInstanceUri(tenantId);
        return httpClient.get(uri, getRequestHeaders(), ResponsePredicate.status(expectedStatusCode));
    }

    /**
     * Updates configuration information for a tenant.
     *
     * @param tenantId The tenant to update information for.
     * @param requestPayload The payload to set, as specified by the Tenant management API.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> updateTenant(final String tenantId, final Tenant requestPayload,
            final int expectedStatusCode) {

        final String uri = tenantInstanceUri(tenantId);
        final JsonObject payload = JsonObject.mapFrom(requestPayload);
        return httpClient.update(
                uri,
                payload.toBuffer(),
                getRequestHeaders(CONTENT_TYPE_APPLICATION_JSON),
                ResponsePredicate.status(expectedStatusCode));
    }

    /**
     * Removes configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #removeTenant(String, int)} with {@link HttpURLConnection#HTTP_NO_CONTENT} as
     * the expected status code.
     *
     * @param tenantId The tenant to remove.
     * @return A future indicating the outcome of the operation. The future will succeed if the tenant has been removed.
     *         Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> removeTenant(final String tenantId) {

        return removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Removes configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #removeTenant(String, ResponsePredicate...)} requiring an outcome of
     * {@link HttpURLConnection#HTTP_NO_CONTENT} or {@link HttpURLConnection#HTTP_NOT_FOUND} if {@code ignoreMissing} is
     * {@code true}.
     *
     * @param tenantId The tenant to remove.
     * @param ignoreMissing Ignore a missing tenant.
     * @return A future indicating the outcome of the operation. The future will succeed if the tenant has been removed.
     *         Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> removeTenant(final String tenantId, final boolean ignoreMissing) {
        return removeTenant(tenantId, okOrIgnoreMissing(ignoreMissing));
    }

    /**
     * Removes configuration information for a tenant.
     *
     * @param tenantId The tenant to remove.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> removeTenant(final String tenantId, final int expectedStatusCode) {
        return removeTenant(tenantId, ResponsePredicate.status(expectedStatusCode));
    }

    /**
     * Removes configuration information for a tenant.
     *
     * @param tenantId The tenant to remove.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     */
    public Future<HttpResponse<Buffer>> removeTenant(final String tenantId, final ResponsePredicate ... successPredicates) {
        final String uri = tenantInstanceUri(tenantId);
        return httpClient.delete(uri, getRequestHeaders(), successPredicates);
    }

    /**
     * Finds tenants with optional filters, paging and sorting options.
     *
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response.
     * @param filters The filters are predicates that objects in the result set must match.
     * @param sortOptions A list of sort options.
     * @return A future indicating the outcome of the operation. The future will contain the response if the
     *         response contained the expected status code. Otherwise the future will fail.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<HttpResponse<Buffer>> searchTenants(
            final Optional<Integer> pageSize,
            final Optional<Integer> pageOffset,
            final List<String> filters,
            final List<String> sortOptions) {

        Objects.requireNonNull(pageSize);
        Objects.requireNonNull(pageOffset);
        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);

        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();

        pageSize.ifPresent(
                pSize -> queryParams.add(RegistryManagementConstants.PARAM_PAGE_SIZE, String.valueOf(pSize)));
        pageOffset.ifPresent(
                pOffset -> queryParams.add(RegistryManagementConstants.PARAM_PAGE_OFFSET, String.valueOf(pOffset)));
        filters.forEach(filterJson -> queryParams.add(RegistryManagementConstants.PARAM_FILTER_JSON, filterJson));
        sortOptions.forEach(sortJson -> queryParams.add(RegistryManagementConstants.PARAM_SORT_JSON, sortJson));

        return httpClient.get(searchTenantsUri(), getRequestHeaders(), queryParams, (ResponsePredicate[]) null);
    }

    /**
     * Finds tenants with optional filters, paging and sorting options.
     *
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response.
     * @param filters The filters are predicates that objects in the result set must match.
     * @param sortOptions A list of sort options.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will contain the response if the
     *         response contained the expected status code. Otherwise the future will fail.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<HttpResponse<Buffer>> searchTenants(
            final Optional<Integer> pageSize,
            final Optional<Integer> pageOffset,
            final List<String> filters,
            final List<String> sortOptions,
            final int expectedStatusCode) {

        Objects.requireNonNull(pageSize);
        Objects.requireNonNull(pageOffset);
        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);

        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();

        pageSize.ifPresent(
                pSize -> queryParams.add(RegistryManagementConstants.PARAM_PAGE_SIZE, String.valueOf(pSize)));
        pageOffset.ifPresent(
                pOffset -> queryParams.add(RegistryManagementConstants.PARAM_PAGE_OFFSET, String.valueOf(pOffset)));
        filters.forEach(filterJson -> queryParams.add(RegistryManagementConstants.PARAM_FILTER_JSON, filterJson));
        sortOptions.forEach(sortJson -> queryParams.add(RegistryManagementConstants.PARAM_SORT_JSON, sortJson));

        return httpClient.get(searchTenantsUri(), getRequestHeaders(), queryParams, ResponsePredicate.status(expectedStatusCode));
    }
    // device registration

    /**
     * Adds registration information for a device.
     * <p>
     * The device identifier will be generated by the service implementation and the device will be enabled by default.
     * <p>
     * This method simply invokes {@link #registerDevice(String, String, Device)}.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param device Additional properties to register with the device.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been added successfully. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> registerDevice(final String tenantId, final Device device) {
        return registerDevice(tenantId, null, device);
    }

    /**
     * Adds registration information for a device.
     * <p>
     * The device will be enabled by default.
     * <p>
     * This method simply invokes {@link #registerDevice(String, String, Device)} with an empty JSON object as
     * additional data.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been added successfully. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> registerDevice(final String tenantId, final String deviceId) {
        return registerDevice(tenantId, deviceId, new Device());
    }

    /**
     * Adds registration information for a device.
     * <p>
     * The device will be enabled by default if not specified otherwise in the additional data.
     * <p>
     * This method simply invokes {@link #registerDevice(String, String, Device, int)} with
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param device Additional properties to register with the device.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been added successfully. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> registerDevice(final String tenantId, final String deviceId, final Device device) {
        return registerDevice(tenantId, deviceId, device, HttpURLConnection.HTTP_CREATED);
    }

    /**
     * Adds registration information for a device.
     * <p>
     * The device will be enabled by default if not specified otherwise in the additional data.
     * <p>
     * This method simply invokes {@link #registerDevice(String, String, Device, String, int)} with
     * <em>application/json</em> as the content type.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param data Additional properties to register with the device.
     * @param expectedStatus The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> registerDevice(final String tenantId, final String deviceId, final Device data,
            final int expectedStatus) {
        return registerDevice(tenantId, deviceId, data, CONTENT_TYPE_APPLICATION_JSON, expectedStatus);
    }

    /**
     * Adds registration information for a device.
     * <p>
     * The device will be enabled by default if not specified otherwise in the additional data.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param device Additional properties to register with the device.
     * @param contentType The content type to set on the request.
     * @param expectedStatus The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> registerDevice(
            final String tenantId,
            final String deviceId,
            final Device device,
            final String contentType,
            final int expectedStatus) {

        return registerDevice(tenantId, deviceId, JsonObject.mapFrom(device), contentType, expectedStatus);
    }

    /**
     * Adds registration information for a device.
     * <p>
     * The device will be enabled by default if not specified otherwise in the additional data.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param device Additional properties to register with the device.
     * @param contentType The content type to set on the request.
     * @param expectedStatus The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> registerDevice(
            final String tenantId,
            final String deviceId,
            final JsonObject device,
            final String contentType,
            final int expectedStatus) {

        Objects.requireNonNull(tenantId);
        final String uri = Optional.ofNullable(deviceId)
                .map(id -> registrationInstanceUri(tenantId, id))
                .orElseGet(() -> registrationWithoutIdUri(tenantId));
        return httpClient.create(
                uri,
                Optional.ofNullable(device).map(JsonObject::toBuffer).orElse(null),
                getRequestHeaders(contentType),
                ResponsePredicate.status(expectedStatus));
    }

    /**
     * Updates registration information for a device.
     * <p>
     * This method simply invokes {@link #updateDevice(String, String, JsonObject, String, int)} with
     * <em>application/json</em> as the content type and {@link HttpURLConnection#HTTP_NO_CONTENT} as the expected
     * status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param data Additional properties to register with the device.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been updated successfully. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> updateDevice(final String tenantId, final String deviceId, final JsonObject data) {
        return updateDevice(tenantId, deviceId, data, CONTENT_TYPE_APPLICATION_JSON, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Updates registration information for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param data Additional properties to register with the device.
     * @param contentType The content type to set on the request.
     * @param expectedStatus The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> updateDevice(
            final String tenantId,
            final String deviceId,
            final JsonObject data,
            final String contentType,
            final int expectedStatus) {

        Objects.requireNonNull(tenantId);
        final String requestUri = registrationInstanceUri(tenantId, deviceId);
        return httpClient.update(
                requestUri,
                data.toBuffer(),
                getRequestHeaders(contentType),
                ResponsePredicate.status(expectedStatus));
    }

    /**
     * Gets registration information for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation. The future will contain the response if the
     *         request succeeded. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> getRegistrationInfo(final String tenantId, final String deviceId) {
        return getRegistrationInfo(tenantId, deviceId, HttpURLConnection.HTTP_OK);
    }

    /**
     * Gets registration information for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param expectedStatus The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will contain the response if the
     *         request succeeded. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> getRegistrationInfo(
            final String tenantId,
            final String deviceId,
            final int expectedStatus) {

        Objects.requireNonNull(tenantId);
        final String requestUri = registrationInstanceUri(tenantId, deviceId);
        return httpClient.get(requestUri, getRequestHeaders(), ResponsePredicate.status(expectedStatus));
    }

    /**
     * Removes registration information for all devices of a tenant.
     *
     * @param tenantId The tenant that the devices belong to.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been removed. Otherwise the future will fail.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    public Future<HttpResponse<Buffer>> deregisterDevicesOfTenant(final String tenantId) {

        Objects.requireNonNull(tenantId);
        final String requestUri = registrationWithoutIdUri(tenantId);
        return httpClient.delete(
                requestUri,
                getRequestHeaders(),
                ResponsePredicate.create(response -> {
            if (response.statusCode() == HttpURLConnection.HTTP_NO_CONTENT ||
                    response.statusCode() == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
                return ResponsePredicateResult.success();
            } else {
                return ResponsePredicateResult.failure(
                        String.format("expected status code 204 or 501 but got %d", response.statusCode()));
            }
        }));
    }

    /**
     * Removes registration information for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been removed. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> deregisterDevice(final String tenantId, final String deviceId) {
        return deregisterDevice(tenantId, deviceId, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Removes registration information for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param expectedStatus The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been removed. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> deregisterDevice(
            final String tenantId,
            final String deviceId,
            final int expectedStatus) {

        return deregisterDevice(tenantId, deviceId, ResponsePredicate.status(expectedStatus));

    }

    /**
     * Removes registration information for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param ignoreMissing Ignore a missing device.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been removed. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> deregisterDevice(
            final String tenantId,
            final String deviceId,
            final boolean ignoreMissing) {

        return deregisterDevice(tenantId, deviceId, okOrIgnoreMissing(ignoreMissing));
    }

    /**
     * Removes registration information for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been removed. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> deregisterDevice(
            final String tenantId,
            final String deviceId,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(tenantId);
        final String requestUri = registrationInstanceUri(tenantId, deviceId);
        return httpClient.delete(requestUri, getRequestHeaders(), successPredicates);
    }

    /**
     * Finds devices belonging to the given tenant with optional filters, paging and sorting options.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response.
     * @param filters The filters are predicates that objects in the result set must match.
     * @param sortOptions A list of sort options.
     * @param isGateway A filter for restricting the search to gateway ({@code True}) or edge ({@code False} devices only.
     *                  If <em>empty</em>, the search will not be restricted.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will contain the response if the
     *         response contained the expected status code. Otherwise the future will fail.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<HttpResponse<Buffer>> searchDevices(
            final String tenantId,
            final Optional<Integer> pageSize,
            final Optional<Integer> pageOffset,
            final List<String> filters,
            final List<String> sortOptions,
            final Optional<Boolean> isGateway,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(pageSize);
        Objects.requireNonNull(pageOffset);
        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);

        final String requestUri = searchDevicesUri(tenantId);
        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();

        pageSize.ifPresent(
                pSize -> queryParams.add(RegistryManagementConstants.PARAM_PAGE_SIZE, String.valueOf(pSize)));
        pageOffset.ifPresent(
                pOffset -> queryParams.add(RegistryManagementConstants.PARAM_PAGE_OFFSET, String.valueOf(pOffset)));
        filters.forEach(filterJson -> queryParams.add(RegistryManagementConstants.PARAM_FILTER_JSON, filterJson));
        sortOptions.forEach(sortJson -> queryParams.add(RegistryManagementConstants.PARAM_SORT_JSON, sortJson));
        isGateway.ifPresent(b -> queryParams.add(RegistryManagementConstants.PARAM_IS_GATEWAY, b.toString()));

        return httpClient.get(requestUri, getRequestHeaders(), queryParams, ResponsePredicate.status(expectedStatusCode));
    }

    // credentials management

    /**
     * Adds credentials for a device.
     * <p>
     * This method simply invokes {@link #addCredentials(String, String, Collection, int)} with
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device credentials belongs to.
     * @param secrets The secrets to add.
     * @return A future indicating the outcome of the operation. The future will succeed if the credentials have been
     *         added successfully. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> addCredentials(final String tenantId, final String deviceId,
            final Collection<CommonCredential> secrets) {
        return addCredentials(tenantId, deviceId, secrets, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Adds credentials for a device.
     * <p>
     * This method simply invokes {@link #addCredentials(String, String, Collection, String, int)} with
     * <em>application/json</em> as the content type and {@link HttpURLConnection#HTTP_CREATED} as the expected status
     * code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device credentials belongs to.
     * @param secrets The secrets to add.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> addCredentials(final String tenantId, final String deviceId,
            final Collection<CommonCredential> secrets, final int expectedStatusCode) {
        return addCredentials(tenantId, deviceId, secrets, CONTENT_TYPE_APPLICATION_JSON, expectedStatusCode);
    }

    /**
     * Adds credentials for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device credentials belongs to.
     * @param secrets The secrets to add.
     * @param contentType The content type to set on the request.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> addCredentials(
            final String tenantId,
            final String deviceId,
            final Collection<CommonCredential> secrets,
            final String contentType,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = credentialsByDeviceUri(tenantId, deviceId);

        return httpClient.get(uri, getRequestHeaders(), ResponsePredicate.status(HttpURLConnection.HTTP_OK))
                .compose(httpResponse -> {

                    // new list of secrets

                    // get a list, through an array - workaround for vert.x json issue
                    final List<CommonCredential> currentSecrets = new ArrayList<>(
                            Arrays.asList(Json.decodeValue(httpResponse.bodyAsBuffer(), CommonCredential[].class)));
                    currentSecrets.addAll(secrets);

                    // update

                    // encode array, not list - workaround for vert.x json issue
                    final var payload = Json.encodeToBuffer(currentSecrets.toArray(CommonCredential[]::new));
                    return httpClient.update(
                            uri,
                            payload,
                            getRequestHeaders(contentType),
                            ResponsePredicate.status(expectedStatusCode));
                });

    }

    /**
     * Gets all credentials registered for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation. The future will contain the response if the
     *         request succeeded. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> getCredentials(final String tenantId, final String deviceId) {

        return getCredentials(tenantId, deviceId, HttpURLConnection.HTTP_OK);
    }

    /**
     * Gets all credentials registered for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will contain the response if the
     *         response contains the expected status code. Otherwise the future will fail.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    public Future<HttpResponse<Buffer>> getCredentials(final String tenantId, final String deviceId,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = credentialsByDeviceUri(tenantId, deviceId);
        return httpClient.get(uri, getRequestHeaders(), ResponsePredicate.status(expectedStatusCode));
    }

    /**
     * Updates credentials of a specific type for a device.
     * <p>
     * This method simply invokes {@link #updateCredentials(String, String, Collection, int)} with
     * {@link HttpURLConnection#HTTP_NO_CONTENT} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param credentialsSpec The JSON object to be sent in the request body.
     * @return A future indicating the outcome of the operation. The future will succeed if the credentials have been
     *         updated successfully. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> updateCredentials(final String tenantId, final String deviceId,
            final CommonCredential credentialsSpec) {
        return updateCredentials(tenantId, deviceId, Collections.singleton(credentialsSpec),
                HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Updates credentials of a specific type for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param credentialsSpec The JSON array to be sent in the request body.
     * @param expectedResourceVersion The resource version that the credentials on record must match. The value of
     *                                this parameter will be included in the request's <em>if-match</em> header.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contains the expected status code.
     *         Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> updateCredentialsWithVersion(
            final String tenantId,
            final String deviceId,
            final Collection<CommonCredential> credentialsSpec,
            final String expectedResourceVersion,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = credentialsByDeviceUri(tenantId, deviceId);

        final MultiMap headers = getRequestHeaders(CONTENT_TYPE_APPLICATION_JSON)
                .add(HttpHeaders.IF_MATCH, expectedResourceVersion);

        // encode array not list, workaround for vert.x issue
        final var payload = Json.encodeToBuffer(credentialsSpec.toArray(CommonCredential[]::new));

        return httpClient.update(uri, payload, headers, ResponsePredicate.status(expectedStatusCode));
    }

    /**
     * Updates credentials of a specific type for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param credentialsSpec The JSON array to be sent in the request body.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contains the
     *         expected status code. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> updateCredentials(
            final String tenantId,
            final String deviceId,
            final Collection<CommonCredential> credentialsSpec,
            final int expectedStatusCode) {

        return updateCredentials(tenantId, deviceId, credentialsSpec, CONTENT_TYPE_APPLICATION_JSON,
                expectedStatusCode);
    }

    /**
     * Updates credentials of a specific type for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param credentialsSpec The JSON array to be sent in the request body.
     * @param contentType The content type to set on the request.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contains the
     *         expected status code. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> updateCredentials(
            final String tenantId,
            final String deviceId,
            final Collection<CommonCredential> credentialsSpec,
            final String contentType,
            final int expectedStatusCode) {

        Objects.requireNonNull(credentialsSpec);

        // encode array not list, workaround for vert.x issue
        final var payload = Json.encodeToBuffer(credentialsSpec.toArray(CommonCredential[]::new));

        return updateCredentialsRaw(tenantId, deviceId, payload, contentType, expectedStatusCode);

    }

    /**
     * Execute an update credentials request, with raw payload.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param payload The raw payload.
     * @param contentType The content type to set on the request.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contains the
     *         expected status code. Otherwise the future will fail.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> updateCredentialsRaw(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = credentialsByDeviceUri(tenantId, deviceId);

        return httpClient.update(
                uri,
                payload,
                getRequestHeaders(contentType),
                ResponsePredicate.status(expectedStatusCode));
    }

    // convenience methods

    /**
     * Creates a tenant and adds a device to it with a given password.
     * <p>
     * This method simply invokes {@link #addDeviceForTenant(String, Tenant, String, Device, String)} with no extra
     * data.
     *
     * @param tenantId The ID of the tenant to create.
     * @param tenant The tenant payload as specified by the Tenant management API.
     * @param deviceId The identifier of the device to add to the tenant.
     * @param password The password to use for the device's credentials.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> addDeviceForTenant(final String tenantId, final Tenant tenant, final String deviceId,
            final String password) {

        return addDeviceForTenant(tenantId, tenant, deviceId, new Device(), password);
    }

    /**
     * Creates a tenant and adds a device to it with a given password.
     * <p>
     * The password will be added as a hashed password using the device identifier as the authentication identifier.
     *
     * @param tenantId The ID of the tenant to create.
     * @param tenant The tenant payload as specified by the Tenant management API.
     * @param deviceId The identifier of the device to add.
     * @param device The data to register for the device.
     * @param password The password to use for the device's credentials.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public Future<HttpResponse<Buffer>> addDeviceForTenant(
            final String tenantId,
            final Tenant tenant,
            final String deviceId,
            final Device device,
            final String password) {

        Objects.requireNonNull(tenant);

        final PasswordCredential secret = Credentials.createPasswordCredential(deviceId, password);

        return addTenant(tenantId, tenant)
                .compose(ok -> registerDevice(tenantId, deviceId, device))
                .compose(ok -> addCredentials(tenantId, deviceId,
                        Collections.singleton(secret)));
    }

    /**
     * Adds a device with a given password to an existing tenant.
     * <p>
     * The password will be added as a hashed password using the device identifier as the authentication identifier.
     *
     * @param tenantId The identifier of the tenant to add the device to.
     * @param deviceId The identifier of the device to add.
     * @param password The password to use for the device's credentials.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public Future<HttpResponse<Buffer>> addDeviceToTenant(
            final String tenantId,
            final String deviceId,
            final String password) {

        return addDeviceToTenant(tenantId, deviceId, new Device(), password);
    }

    /**
     * Adds a device with a given password to an existing tenant.
     * <p>
     * The password will be added as a hashed password using the device identifier as the authentication identifier.
     *
     * @param tenantId The identifier of the tenant to add the device to.
     * @param deviceId The identifier of the device to add.
     * @param data The data to register for the device.
     * @param password The password to use for the device's credentials.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public Future<HttpResponse<Buffer>> addDeviceToTenant(
            final String tenantId,
            final String deviceId,
            final Device data,
            final String password) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(data);
        Objects.requireNonNull(password);

        final PasswordCredential secret = Credentials.createPasswordCredential(deviceId, password);

        return registerDevice(tenantId, deviceId, data)
                .compose(ok -> addCredentials(tenantId, deviceId, Collections.singletonList(secret)));
    }

    /**
     * Creates a tenant and adds a device to it with a given client certificate.
     * <p>
     * The device will be registered with a set of <em>x509-cert</em> credentials using the client certificate's subject
     * DN as authentication identifier.
     *
     * @param tenantId The identifier of the tenant to add the secret to.
     * @param tenant The tenant payload as specified by the Tenant management API.
     * @param deviceId The identifier of the device to add to the tenant.
     * @param deviceCert The device's client certificate.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenant or certificate are {@code null}.
     */
    public Future<Void> addDeviceForTenant(final String tenantId, final Tenant tenant, final String deviceId,
            final X509Certificate deviceCert) {

        Objects.requireNonNull(tenant);

        return addTenant(tenantId, tenant)
                .compose(ok -> registerDevice(tenantId, deviceId))
                .compose(ok -> {

                    final String authId = deviceCert.getSubjectX500Principal().getName();
                    final var credential = X509CertificateCredential.fromAuthId(authId, List.of(new X509CertificateSecret()));

                    return addCredentials(tenantId, deviceId, Collections.singleton(credential));

                })
                .onSuccess(ok -> LOG.debug(
                        "registered device with client certificate [tenant-id: {}, device-id: {}, auth-id: {}]",
                        tenantId, deviceId, deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253)))
                .mapEmpty();
    }

    /**
     * Creates a tenant and adds a device to it with a given Pre-Shared Key.
     * <p>
     * The device will be registered with a set of <em>psk</em> credentials using the device identifier as the
     * authentication identifier and PSK identity.
     *
     * @param tenantId The identifier of the tenant to add the secret to.
     * @param tenant The tenant payload as specified by the Tenant management API.
     * @param deviceId The identifier of the device to add to the tenant.
     * @param key The shared key.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are are {@code null}.
     */
    public Future<HttpResponse<Buffer>> addPskDeviceForTenant(final String tenantId, final Tenant tenant, final String deviceId,
            final String key) {
        return addPskDeviceForTenant(tenantId, tenant, deviceId, new Device(), key);
    }

    /**
     * Creates a tenant and adds a device to it with a given Pre-Shared Key.
     * <p>
     * The device will be registered with a set of <em>psk</em> credentials using the device identifier as the
     * authentication identifier and PSK identity.
     *
     * @param tenantId The identifier of the tenant to add the secret to.
     * @param tenant The tenant payload as specified by the Tenant management API.
     * @param deviceId The identifier of the device to add to the tenant.
     * @param deviceData Additional data to register for the device.
     * @param key The shared key.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are are {@code null}.
     */
    public Future<HttpResponse<Buffer>> addPskDeviceForTenant(
            final String tenantId,
            final Tenant tenant,
            final String deviceId,
            final Device deviceData,
            final String key) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(deviceData);
        Objects.requireNonNull(key);

        final var pskCredentials = Credentials.createPSKCredential(deviceId, key);

        return addTenant(tenantId, tenant)
                .compose(ok -> registerDevice(tenantId, deviceId, deviceData))
                .compose(ok -> addCredentials(tenantId, deviceId, List.of(pskCredentials)));
    }

    /**
     * Adds a device with a given Pre-Shared Key to an existing tenant.
     * <p>
     * The key will be added as a <em>psk</em> secret using the device identifier as the authentication identifier and
     * PSK identity.
     *
     * @param tenantId The identifier of the tenant to add the device to.
     * @param deviceId The identifier of the device to add.
     * @param key The shared key.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public Future<HttpResponse<Buffer>> addPskDeviceToTenant(final String tenantId, final String deviceId, final String key) {

        final var pskCredentials = Credentials.createPSKCredential(deviceId, key);

        return registerDevice(tenantId, deviceId)
                .compose(ok -> addCredentials(tenantId, deviceId, List.of(pskCredentials)));
    }

    private static ResponsePredicate okOrIgnoreMissing(final boolean ignoreMissing) {
        if (ignoreMissing) {
            return response -> {
                if (response.statusCode() == HttpURLConnection.HTTP_NO_CONTENT || response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND ) {
                    return ResponsePredicateResult.success();
                } else {
                    return ResponsePredicateResult.failure("Response code must be either 204 or 404: was " + response.statusCode());
                }
            };
        } else {
            return ResponsePredicate.SC_NO_CONTENT;
        }
    }

}
