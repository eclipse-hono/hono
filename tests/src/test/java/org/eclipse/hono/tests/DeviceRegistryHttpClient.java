/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PskCredential;
import org.eclipse.hono.service.management.credentials.PskSecret;
import org.eclipse.hono.service.management.credentials.X509CertificateCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateSecret;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing the Device Registry's HTTP resources for the Device Registration, Credentials and Tenant API.
 *
 */
public final class DeviceRegistryHttpClient {

    /**
     * The URI pattern for adding a tenant.
     */
    public static final String URI_ADD_TENANT = String.format("/%s/%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.TENANT_HTTP_ENDPOINT);
    /**
     * The URI pattern for addressing a tenant instance.
     */
    public static final String TEMPLATE_URI_TENANT_INSTANCE = String.format("/%s/%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.TENANT_HTTP_ENDPOINT);

    /**
     * The URI pattern for addressing a device instance.
     */
    public static final String TEMPLATE_URI_REGISTRATION_WITHOUT_ID = String.format("/%s/%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.REGISTRATION_HTTP_ENDPOINT);

    /**
     * The URI pattern for addressing adding a device without id.
     */
    public static final String TEMPLATE_URI_REGISTRATION_INSTANCE = String.format("/%s/%s/%%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.REGISTRATION_HTTP_ENDPOINT);

    /**
     * The URI pattern for addressing the credentials of a device.
     */
    public static final String TEMPLATE_URI_CREDENTIALS_BY_DEVICE = String.format("/%s/%s/%%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.CREDENTIALS_ENDPOINT);
    /**
     * The URI pattern for addressing a device's credentials of a specific type.
     */
    public static final String TEMPLATE_URI_CREDENTIALS_INSTANCE = String.format("/%s/%s/%%s/%%s/%%s",
            RegistryManagementConstants.API_VERSION, RegistryManagementConstants.CREDENTIALS_ENDPOINT);

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistryHttpClient.class);

    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private final CrudHttpClient httpClient;

    /**
     * Creates a new client for a host and port.
     * 
     * @param vertx The vert.x instance to use.
     * @param host The host to invoke the operations on.
     * @param port The port that the service is bound to.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public DeviceRegistryHttpClient(final Vertx vertx, final String host, final int port) {
        this.httpClient = new CrudHttpClient(vertx, host, port);
    }

    // tenant management

    /**
     * Adds configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #addTenant(String, Tenant, int)} with
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The id of the tenant to add.
     * @param requestPayload The request payload as specified by the Tenant management API.
     * @return A future indicating the outcome of the operation. The future will succeed if the tenant has been created
     *         successfully. Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<MultiMap> addTenant(final String tenantId, final Tenant requestPayload) {
        return addTenant(tenantId, requestPayload, HttpURLConnection.HTTP_CREATED);
    }

    /**
     * Adds configuration information for a tenant without a payload.
     * <p>
     * This method simply invokes {@link #addTenant(String, Tenant, String, int)} with
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The id of the tenant to add.
     * @return A future indicating the outcome of the operation. The future will succeed if the tenant has been created
     *         successfully. Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<MultiMap> addTenant(final String tenantId) {
        return addTenant(tenantId, null, null, HttpURLConnection.HTTP_CREATED);
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
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<MultiMap> addTenant(final String tenantId, final Tenant requestPayload,
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
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<MultiMap> addTenant(final String tenantId, final Tenant requestPayload, final String contentType,
            final int expectedStatusCode) {

        final String uri = String.format("%s/%s", URI_ADD_TENANT, tenantId);
        final JsonObject payload = JsonObject.mapFrom(requestPayload);
        return httpClient.create(uri, payload, contentType,
                response -> response.statusCode() == expectedStatusCode, true);
    }

    /**
     * Gets configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #getTenant(String, int)} with {@link HttpURLConnection#HTTP_OK} as the expected
     * status code.
     * 
     * @param tenantId The tenant to get information for.
     * @return A future indicating the outcome of the operation. The future will contain the response payload if the
     *         request succeeded. Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<Buffer> getTenant(final String tenantId) {
        return getTenant(tenantId, HttpURLConnection.HTTP_OK);
    }

    /**
     * Gets configuration information for a tenant.
     * 
     * @param tenantId The tenant to get information for.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will contain the response payload if the
     *         response contained the expected status code. Otherwise the future will fail with a
     *         {@link ServiceInvocationException}.
     */
    public Future<Buffer> getTenant(final String tenantId, final int expectedStatusCode) {

        final String uri = String.format(TEMPLATE_URI_TENANT_INSTANCE, tenantId);
        return httpClient.get(uri, status -> status == expectedStatusCode);
    }

    /**
     * Updates configuration information for a tenant.
     * 
     * @param tenantId The tenant to update information for.
     * @param requestPayload The payload to set, as specified by the Tenant management API.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<MultiMap> updateTenant(final String tenantId, final Tenant requestPayload,
            final int expectedStatusCode) {

        final String uri = String.format(TEMPLATE_URI_TENANT_INSTANCE, tenantId);
        final JsonObject payload = JsonObject.mapFrom(requestPayload);
        return httpClient.update(uri, payload, status -> status == expectedStatusCode);
    }

    /**
     * Removes configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #removeTenant(String, int)} with {@link HttpURLConnection#HTTP_NO_CONTENT} as
     * the expected status code.
     * 
     * @param tenantId The tenant to remove.
     * @return A future indicating the outcome of the operation. The future will succeed if the tenant has been removed.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<Void> removeTenant(final String tenantId) {

        return removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Removes configuration information for a tenant.
     * 
     * @param tenantId The tenant to remove.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<Void> removeTenant(final String tenantId, final int expectedStatusCode) {

        final String uri = String.format(TEMPLATE_URI_TENANT_INSTANCE, tenantId);
        return httpClient.delete(uri, status -> status == expectedStatusCode);
    }

    // device registration

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
     *         has been added successfully. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> registerDevice(final String tenantId, final String deviceId) {
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
     *         has been added successfully. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> registerDevice(final String tenantId, final String deviceId, final Device device) {
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
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> registerDevice(final String tenantId, final String deviceId, final Device data,
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
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> registerDevice(
            final String tenantId,
            final String deviceId,
            final Device device,
            final String contentType,
            final int expectedStatus) {

        Objects.requireNonNull(tenantId);
        String uri = String.format(TEMPLATE_URI_REGISTRATION_WITHOUT_ID, tenantId);

        if (deviceId != null) {
            uri = String.format(TEMPLATE_URI_REGISTRATION_INSTANCE, tenantId, deviceId);
        }
        return httpClient.create(uri, JsonObject.mapFrom(device), contentType,
                response -> response.statusCode() == expectedStatus, true);
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
     *         has been updated successfully. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> updateDevice(final String tenantId, final String deviceId, final JsonObject data) {
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
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> updateDevice(
            final String tenantId,
            final String deviceId,
            final JsonObject data,
            final String contentType,
            final int expectedStatus) {

        Objects.requireNonNull(tenantId);
        final String requestUri = String.format(TEMPLATE_URI_REGISTRATION_INSTANCE, tenantId, deviceId);
        return httpClient.update(requestUri, data, contentType, status -> status == expectedStatus);
    }

    /**
     * Gets registration information for a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation. The future will contain the response payload if the
     *         request succeeded. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Buffer> getRegistrationInfo(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        final String requestUri = String.format(TEMPLATE_URI_REGISTRATION_INSTANCE, tenantId, deviceId);
        return httpClient.get(requestUri, status -> status == HttpURLConnection.HTTP_OK);
    }

    /**
     * Removes registration information for a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation. The future will succeed if the registration information
     *         has been removed. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> deregisterDevice(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        final String requestUri = String.format(TEMPLATE_URI_REGISTRATION_INSTANCE, tenantId, deviceId);
        return httpClient.delete(requestUri, status -> status == HttpURLConnection.HTTP_NO_CONTENT);
    }

    // credentials management

    /**
     * Add credentials for a device.
     * <p>
     * This method simply invokes {@link #addCredentials(String, String, Collection, int)} with
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device credentials belongs to.
     * @param secrets The secrets to add.
     * @return A future indicating the outcome of the operation. The future will succeed if the credentials have been
     *         added successfully. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> addCredentials(final String tenantId, final String deviceId,
            final Collection<CommonCredential> secrets) {
        return addCredentials(tenantId, deviceId, secrets, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Add credentials for a device.
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
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> addCredentials(final String tenantId, final String deviceId,
            final Collection<CommonCredential> secrets, final int expectedStatusCode) {
        return addCredentials(tenantId, deviceId, secrets, CONTENT_TYPE_APPLICATION_JSON, expectedStatusCode);
    }

    /**
     * Add credentials for a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device credentials belongs to.
     * @param secrets The secrets to add.
     * @param contentType The content type to set on the request.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contained the
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> addCredentials(
            final String tenantId,
            final String deviceId,
            final Collection<CommonCredential> secrets,
            final String contentType,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, tenantId, deviceId);

        return httpClient.get(uri, response -> response == HttpURLConnection.HTTP_OK)
                .compose(body -> {

                    // new list of secrets

                    // get a list, through an array - workaround for vert.x json issue
                    final List<CommonCredential> currentSecrets = new ArrayList<>(
                            Arrays.asList(Json.decodeValue(body, CommonCredential[].class)));
                    currentSecrets.addAll(secrets);

                    // update

                    // encode array, not list - workaround for vert.x json issue
                    final var payload = Json.encodeToBuffer(currentSecrets.toArray(CommonCredential[]::new));
                    return httpClient.update(uri, payload, contentType,
                            response -> response == expectedStatusCode, true);
                });

    }

    /**
     * Gets all credentials registered for a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation. The future will contain the response payload if the
     *         request succeeded. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Buffer> getCredentials(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, tenantId, deviceId);
        return httpClient.get(uri, status -> status == HttpURLConnection.HTTP_OK);
    }

    /**
     * Gets credentials of a specific type for a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param authId The authentication identifier of the device.
     * @param type The type of credentials to retrieve.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will contain the response payload if the
     *         response contains the expected status code. Otherwise the future will fail with a
     *         {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Buffer> getCredentials(final String tenantId, final String authId, final String type,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, tenantId, authId, type);
        return httpClient.get(uri, status -> status == expectedStatusCode);
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
     *         updated successfully. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> updateCredentials(final String tenantId, final String deviceId,
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
     * @param version The version of credentials to be sent as request header.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contains the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> updateCredentialsWithVersion(
            final String tenantId,
            final String deviceId,
            final Collection<CommonCredential> credentialsSpec,
            final String version,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, tenantId, deviceId);

        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.IF_MATCH, version)
                .add(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON);

        // encode array not list, workaround for vert.x issue
        final var payload = Json.encodeToBuffer(credentialsSpec.toArray(CommonCredential[]::new));

        return httpClient
                .update(uri, payload, headers, status -> status == expectedStatusCode, true)
                .compose(ok -> Future.succeededFuture());
    }

    /**
     * Updates credentials of a specific type for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param credentialsSpec The JSON array to be sent in the request body.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation. The future will succeed if the response contains the
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> updateCredentials(
            final String tenantId,
            final String deviceId,
            final Collection<CommonCredential> credentialsSpec,
            final int expectedStatusCode) {

        return updateCredentials(tenantId, deviceId, credentialsSpec, CrudHttpClient.CONTENT_TYPE_JSON,
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
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> updateCredentials(
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
     *         expected status code. Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> updateCredentialsRaw(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, tenantId, deviceId);

        return httpClient.update(uri, payload, contentType, status -> status == expectedStatusCode, true);
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
    public Future<MultiMap> addDeviceForTenant(final String tenantId, final Tenant tenant, final String deviceId,
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
    public Future<MultiMap> addDeviceForTenant(
            final String tenantId,
            final Tenant tenant,
            final String deviceId,
            final Device device,
            final String password) {

        Objects.requireNonNull(tenant);

        final PasswordCredential secret = IntegrationTestSupport.createPasswordCredential(deviceId, password);

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
    public Future<MultiMap> addDeviceToTenant(
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
    public Future<MultiMap> addDeviceToTenant(
            final String tenantId,
            final String deviceId,
            final Device data,
            final String password) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(data);
        Objects.requireNonNull(password);

        final PasswordCredential secret = IntegrationTestSupport.createPasswordCredential(deviceId, password);

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

                    final X509CertificateCredential credential = new X509CertificateCredential();
                    credential.setAuthId(deviceCert.getSubjectDN().getName());
                    credential.getSecrets().add(new X509CertificateSecret());

                    return addCredentials(tenantId, deviceId, Collections.singleton(credential));

                }).map(ok -> {
                    LOG.debug("registered device with client certificate [tenant-id: {}, device-id: {}, auth-id: {}]",
                            tenantId, deviceId, deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253));
                    return null;
                });
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
    public Future<MultiMap> addPskDeviceForTenant(final String tenantId, final Tenant tenant, final String deviceId,
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
    public Future<MultiMap> addPskDeviceForTenant(
            final String tenantId,
            final Tenant tenant,
            final String deviceId,
            final Device deviceData,
            final String key) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(deviceData);
        Objects.requireNonNull(key);

        final PskCredential credential = new PskCredential();
        credential.setAuthId(deviceId);

        final PskSecret secret = new PskSecret();
        secret.setKey(key.getBytes(StandardCharsets.UTF_8));
        credential.getSecrets().add(secret);

        return addTenant(tenantId, tenant)
                .compose(ok -> registerDevice(tenantId, deviceId, deviceData))
                .compose(ok -> addCredentials(tenantId, deviceId, Collections.singleton(credential)));

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
    public Future<MultiMap> addPskDeviceToTenant(final String tenantId, final String deviceId, final String key) {

        final PskCredential credential = new PskCredential();
        credential.setAuthId(deviceId);

        final PskSecret secret = new PskSecret();
        secret.setKey(key.getBytes(StandardCharsets.UTF_8));
        credential.getSecrets().add(secret);

        return registerDevice(tenantId, deviceId)
                .compose(ok -> addCredentials(tenantId, deviceId, Collections.singleton(credential)));
    }

}
