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

import javax.security.auth.x500.X500Principal;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client for accessing the legacy Device Registry's HTTP resources for the Device Registration, Credentials and
 * Tenant API.
 *
 */
@Deprecated
public final class LegacyDeviceRegistryHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyDeviceRegistryHttpClient.class);

    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final String URI_ADD_TENANT = "/" + TenantConstants.TENANT_ENDPOINT;
    private static final String TEMPLATE_URI_TENANT_INSTANCE = String.format("/%s/%%s", TenantConstants.TENANT_ENDPOINT);

    private static final String TEMPLATE_URI_REGISTRATION_INSTANCE = String.format("/%s/%%s/%%s", RegistrationConstants.REGISTRATION_ENDPOINT);

    private static final String TEMPLATE_URI_CREDENTIALS_INSTANCE = String.format("/%s/%%s/%%s/%%s", CredentialsConstants.CREDENTIALS_ENDPOINT);
    private static final String TEMPLATE_URI_CREDENTIALS_BY_DEVICE = String.format("/%s/%%s/%%s", CredentialsConstants.CREDENTIALS_ENDPOINT);

    private final CrudHttpClient httpClient;

    /**
     * Creates a new client for a host and port.
     *
     * @param vertx The vert.x instance to use.
     * @param host The host to invoke the operations on.
     * @param port The port that the service is bound to.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public LegacyDeviceRegistryHttpClient(final Vertx vertx, final String host, final int port) {
        this.httpClient = new CrudHttpClient(vertx, host, port);
    }

    // tenant management

    /**
     * Adds configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #addTenant(JsonObject, int)} with
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param requestPayload The request payload as specified by the Tenant API.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the tenant has been created successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<MultiMap> addTenant(final JsonObject requestPayload) {
        return addTenant(requestPayload, HttpURLConnection.HTTP_CREATED);
    }

    /**
     * Adds configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #addTenant(JsonObject, String, int)} with
     * <em>application/json</em> as content type and {@link HttpURLConnection#HTTP_CREATED}
     * as the expected status code.
     *
     * @param requestPayload The request payload as specified by the Tenant API.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<MultiMap> addTenant(final JsonObject requestPayload, final int expectedStatusCode) {
        return addTenant(requestPayload, CONTENT_TYPE_APPLICATION_JSON, expectedStatusCode);
    }

    /**
     * Adds configuration information for a tenant.
     *
     * @param requestPayload The request payload as specified by the Tenant API.
     * @param contentType The content type to set in the request.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<MultiMap> addTenant(final JsonObject requestPayload, final String contentType,
            final int expectedStatusCode) {

        return httpClient.create(URI_ADD_TENANT, requestPayload, contentType, response -> response.statusCode() == expectedStatusCode);
    }

    /**
     * Gets configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #getTenant(String, int)} with
     * {@link HttpURLConnection#HTTP_OK} as the expected status code.
     *
     * @param tenantId The tenant to get information for.
     * @return A future indicating the outcome of the operation.
     *         The future will contain the response payload if the request succeeded.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<Buffer> getTenant(final String tenantId) {
        return getTenant(tenantId, HttpURLConnection.HTTP_OK);
    }

    /**
     * Gets configuration information for a tenant.
     *
     * @param tenantId The tenant to get information for.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will contain the response payload if the response
     *         contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<Buffer> getTenant(final String tenantId, final int expectedStatusCode) {

        final String uri = String.format(TEMPLATE_URI_TENANT_INSTANCE, tenantId);
        return httpClient.get(uri, status -> status == expectedStatusCode);
    }

    /**
     * Updates configuration information for a tenant.
     *
     * @param tenantId The tenant to update information for.
     * @param requestPayload The configuration information to set.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     */
    public Future<Void> updateTenant(final String tenantId, final JsonObject requestPayload, final int expectedStatusCode) {

        final String uri = String.format(TEMPLATE_URI_TENANT_INSTANCE, tenantId);
        return httpClient.update(uri, requestPayload, status -> status == expectedStatusCode).mapEmpty();
    }

    /**
     * Removes configuration information for a tenant.
     * <p>
     * This method simply invokes {@link #removeTenant(String, int)} with
     * {@link HttpURLConnection#HTTP_NO_CONTENT} as the expected status code.
     *
     * @param tenantId The tenant to remove.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the tenant has been removed.
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
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
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
     * This method simply invokes {@link #registerDevice(String, String, JsonObject)}
     * with an empty JSON object as additional data.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the registration information has been added successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> registerDevice(final String tenantId, final String deviceId) {
        return registerDevice(tenantId, deviceId, new JsonObject());
    }

    /**
     * Adds registration information for a device.
     * <p>
     * The device will be enabled by default if not specified otherwise
     * in the additional data.
     * <p>
     * This method simply invokes {@link #registerDevice(String, String, JsonObject, int)}
     * with {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param data Additional properties to register with the device.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the registration information has been added successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> registerDevice(final String tenantId, final String deviceId, final JsonObject data) {
        return registerDevice(tenantId, deviceId, data, HttpURLConnection.HTTP_CREATED);
    }

    /**
     * Adds registration information for a device.
     * <p>
     * The device will be enabled by default if not specified otherwise
     * in the additional data.
     * <p>
     * This method simply invokes {@link #registerDevice(String, String, JsonObject, String, int)}
     * with <em>application/json</em> as the content type.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param data Additional properties to register with the device.
     * @param expectedStatus The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> registerDevice(final String tenantId, final String deviceId, final JsonObject data,
            final int expectedStatus) {
        return registerDevice(tenantId, deviceId, data, CONTENT_TYPE_APPLICATION_JSON, expectedStatus);
    }

    /**
     * Adds registration information for a device.
     * <p>
     * The device will be enabled by default if not specified otherwise
     * in the additional data.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param data Additional properties to register with the device.
     * @param contentType The content type to set on the request.
     * @param expectedStatus The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> registerDevice(
            final String tenantId,
            final String deviceId,
            final JsonObject data,
            final String contentType,
            final int expectedStatus) {

        Objects.requireNonNull(tenantId);
        final JsonObject requestJson = Optional.ofNullable(data).map(json -> json.copy()).orElse(null);
        if (deviceId != null && requestJson != null) {
            // we only add the device ID if the client provided a JSON object
            // so that we can also test the case where the client POSTs an empty body
            requestJson.put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        }
        final String uri = String.format("/%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, tenantId);
        return httpClient.create(uri, requestJson, contentType, response -> response.statusCode() == expectedStatus);
    }

    /**
     * Updates registration information for a device.
     * <p>
     * This method simply invokes {@link #updateDevice(String, String, JsonObject, String, int)}
     * with <em>application/json</em> as the content type and
     * {@link HttpURLConnection#HTTP_NO_CONTENT} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param data Additional properties to register with the device.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the registration information has been updated successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> updateDevice(final String tenantId, final String deviceId, final JsonObject data) {
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
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> updateDevice(
            final String tenantId,
            final String deviceId,
            final JsonObject data,
            final String contentType,
            final int expectedStatus) {

        Objects.requireNonNull(tenantId);
        final String requestUri = String.format(TEMPLATE_URI_REGISTRATION_INSTANCE, tenantId, deviceId);
        final JsonObject requestJson = data.copy();
        requestJson.put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        return httpClient.update(requestUri, requestJson, contentType, status -> status == expectedStatus).mapEmpty();
    }

    /**
     * Gets registration information for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation.
     *         The future will contain the response payload if the request succeeded.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
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
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the registration information has been removed.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> deregisterDevice(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        final String requestUri = String.format(TEMPLATE_URI_REGISTRATION_INSTANCE, tenantId, deviceId);
        return httpClient.delete(requestUri, status -> status == HttpURLConnection.HTTP_NO_CONTENT);
    }

    // credentials management

    /**
     * Adds credentials for a device.
     * <p>
     * This method simply invokes {@link #addCredentials(String, JsonObject, int)}
     * with {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param credentialsSpec The JSON object to be sent in the request body.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the credentials have been added successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> addCredentials(final String tenantId, final JsonObject credentialsSpec) {
        return addCredentials(tenantId, credentialsSpec, HttpURLConnection.HTTP_CREATED);
    }

    /**
     * Adds credentials for a device.
     * <p>
     * This method simply invokes {@link #addCredentials(String, JsonObject, String, int)}
     * with <em>application/json</em> as the content type and
     * {@link HttpURLConnection#HTTP_CREATED} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param credentialsSpec The JSON object to be sent in the request body.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> addCredentials(final String tenantId, final JsonObject credentialsSpec,
            final int expectedStatusCode) {
        return addCredentials(tenantId, credentialsSpec, CONTENT_TYPE_APPLICATION_JSON, expectedStatusCode);
    }

    /**
     * Adds credentials for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param credentialsSpec The JSON object to be sent in the request body.
     * @param contentType The content type to set on the request.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contained the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<MultiMap> addCredentials(
            final String tenantId,
            final JsonObject credentialsSpec,
            final String contentType,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format("/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, tenantId);
        return httpClient.create(uri, credentialsSpec, contentType, response -> response.statusCode() == expectedStatusCode);
    }

    /**
     * Gets all credentials registered for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation.
     *         The future will contain the response payload if the request succeeded.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Buffer> getCredentials(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, tenantId, deviceId);
        return httpClient.get(uri, status -> status == HttpURLConnection.HTTP_OK);
    }

    /**
     * Gets credentials of a specific type for a device.
     * <p>
     * This method simply invokes {@link #getCredentials(String, String, String, int)}
     * {@link HttpURLConnection#HTTP_OK} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param authId The authentication identifier of the device.
     * @param type The type of credentials to retrieve.
     * @return A future indicating the outcome of the operation.
     *         The future will contain the response payload if the request succeeded.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Buffer> getCredentials(final String tenantId, final String authId, final String type) {
        return getCredentials(tenantId, authId, type, HttpURLConnection.HTTP_OK);
    }

    /**
     * Gets credentials of a specific type for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param authId The authentication identifier of the device.
     * @param type The type of credentials to retrieve.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will contain the response payload if the response contains
     *         the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Buffer> getCredentials(final String tenantId, final String authId, final String type, final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, tenantId, authId, type);
        return httpClient.get(uri, status -> status == expectedStatusCode);
    }

    /**
     * Updates credentials of a specific type for a device.
     * <p>
     * This method simply invokes {@link #updateCredentials(String, String, String, JsonObject, int)}
     * with {@link HttpURLConnection#HTTP_NO_CONTENT} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param authId The authentication identifier of the device.
     * @param type The type of credentials to update.
     * @param credentialsSpec The JSON object to be sent in the request body.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the credentials have been updated successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> updateCredentials(final String tenantId, final String authId, final String type, final JsonObject credentialsSpec) {
        return updateCredentials(tenantId, authId, type, credentialsSpec, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Updates credentials of a specific type for a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param authId The authentication identifier of the device.
     * @param type The type of credentials to update.
     * @param credentialsSpec The JSON object to be sent in the request body.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contains the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> updateCredentials(
            final String tenantId,
            final String authId,
            final String type,
            final JsonObject credentialsSpec,
            final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, tenantId, authId, type);
        return httpClient.update(uri, credentialsSpec, status -> status == expectedStatusCode).mapEmpty();
    }

    /**
     * Removes credentials of a specific type from a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param authId The authentication identifier of the device.
     * @param type The type of credentials to remove.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the credentials have been removed successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> removeCredentials(final String tenantId, final String authId, final String type) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, tenantId, authId, type);
        return httpClient.delete(uri, status -> status == HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Removes all credentials from a device.
     * <p>
     * This method simply invokes {@link #removeAllCredentials(String, String, int)}
     * with {@link HttpURLConnection#HTTP_NO_CONTENT} as the expected status code.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the credentials have been removed successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> removeAllCredentials(final String tenantId, final String deviceId) {

        return removeAllCredentials(tenantId, deviceId, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Removes all credentials from a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param expectedStatusCode The status code indicating a successful outcome.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed if the response contains the expected status code.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    public Future<Void> removeAllCredentials(final String tenantId, final String deviceId, final int expectedStatusCode) {

        Objects.requireNonNull(tenantId);
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, tenantId, deviceId);
        return httpClient.delete(uri, status -> status == expectedStatusCode);
    }

    // convenience methods

    /**
     * Creates a tenant and adds a device to it with a given password.
     * <p>
     * This method simply invokes {@link #addDeviceForTenant(TenantObject, String, JsonObject, String)}
     * with no extra data.
     *
     * @param tenant The tenant to create.
     * @param deviceId The identifier of the device to add to the tenant.
     * @param password The password to use for the device's credentials.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public Future<MultiMap> addDeviceForTenant(final TenantObject tenant, final String deviceId,
            final String password) {

        return addDeviceForTenant(tenant, deviceId, new JsonObject(), password);
    }

    /**
     * Creates a tenant and adds a device to it with a given password.
     * <p>
     * The password will be added as a hashed password
     * using the device identifier as the authentication identifier.
     *
     * @param tenant The tenant to create.
     * @param deviceId The identifier of the device to add.
     * @param data The data to register for the device.
     * @param password The password to use for the device's credentials.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public Future<MultiMap> addDeviceForTenant(
            final TenantObject tenant,
            final String deviceId,
            final JsonObject data,
            final String password) {

        Objects.requireNonNull(tenant);
        final CredentialsObject credentialsSpec =
                CredentialsObject.fromClearTextPassword(deviceId, deviceId, password, null, null);

        return addTenant(JsonObject.mapFrom(tenant))
                .compose(ok -> registerDevice(tenant.getTenantId(), deviceId, data))
                .compose(ok -> addCredentials(tenant.getTenantId(), JsonObject.mapFrom(credentialsSpec)));
    }

    /**
     * Adds a device with a given password to an existing tenant.
     * <p>
     * The password will be added as a hashed password
     * using the device identifier as the authentication identifier.
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

        return addDeviceToTenant(tenantId, deviceId, new JsonObject(), password);
    }

    /**
     * Adds a device with a given password to an existing tenant.
     * <p>
     * The password will be added as a hashed password
     * using the device identifier as the authentication identifier.
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
            final JsonObject data,
            final String password) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(data);
        Objects.requireNonNull(password);

        final CredentialsObject credentialsSpec =
                CredentialsObject.fromClearTextPassword(deviceId, deviceId, password, null, null);

        return registerDevice(tenantId, deviceId, data)
                .compose(ok -> addCredentials(tenantId, JsonObject.mapFrom(credentialsSpec)));
    }

    /**
     * Creates a tenant and adds a device to it with a given client certificate.
     * <p>
     * The device will be registered with a set of <em>x509-cert</em> credentials
     * using the client certificate's subject DN as authentication identifier.
     *
     * @param tenant The tenant to create.
     * @param deviceId The identifier of the device to add to the tenant.
     * @param deviceCert The device's client certificate.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenant or certificate are {@code null}.
     */
    public Future<MultiMap> addDeviceForTenant(final TenantObject tenant, final String deviceId,
            final X509Certificate deviceCert) {

        Objects.requireNonNull(tenant);

        return addTenant(JsonObject.mapFrom(tenant))
                .compose(ok -> registerDevice(tenant.getTenantId(), deviceId))
                .compose(ok -> {
                    final CredentialsObject credentialsSpec =
                            CredentialsObject.fromClientCertificate(deviceId, deviceCert, null, null);
                    return addCredentials(tenant.getTenantId(), JsonObject.mapFrom(credentialsSpec));
                }).map(ok -> {
                    LOG.debug("registered device with client certificate [tenant-id: {}, device-id: {}, auth-id: {}]",
                            tenant.getTenantId(), deviceId, deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253));
                    return null;
                });
    }

    /**
     * Creates a tenant and adds a device to it with a given Pre-Shared Key.
     * <p>
     * The device will be registered with a set of <em>psk</em> credentials
     * using the device identifier as the authentication identifier and PSK identity.
     *
     * @param tenant The tenant to create.
     * @param deviceId The identifier of the device to add to the tenant.
     * @param key The shared key.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are are {@code null}.
     */
    public Future<MultiMap> addPskDeviceForTenant(final TenantObject tenant, final String deviceId, final String key) {
        return addPskDeviceForTenant(tenant, deviceId, new JsonObject(), key);
    }

    /**
     * Creates a tenant and adds a device to it with a given Pre-Shared Key.
     * <p>
     * The device will be registered with a set of <em>psk</em> credentials
     * using the device identifier as the authentication identifier and PSK identity.
     *
     * @param tenant The tenant to create.
     * @param deviceId The identifier of the device to add to the tenant.
     * @param deviceData Additional data to register for the device.
     * @param key The shared key.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are are {@code null}.
     */
    public Future<MultiMap> addPskDeviceForTenant(
            final TenantObject tenant,
            final String deviceId,
            final JsonObject deviceData,
            final String key) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(deviceData);
        Objects.requireNonNull(key);

        final CredentialsObject credentialsSpec =
                CredentialsObject.fromPresharedKey(deviceId, deviceId, key.getBytes(StandardCharsets.UTF_8), null, null);

        return addTenant(JsonObject.mapFrom(tenant))
                .compose(ok -> registerDevice(tenant.getTenantId(), deviceId, deviceData))
                .compose(ok -> addCredentials(tenant.getTenantId(), JsonObject.mapFrom(credentialsSpec)));

    }

    /**
     * Adds a device with a given Pre-Shared Key to an existing tenant.
     * <p>
     * The key will be added as a <em>psk</em> secret
     * using the device identifier as the authentication identifier and PSK identity.
     *
     * @param tenant The identifier of the tenant to add the device to.
     * @param deviceId The identifier of the device to add.
     * @param key The shared key.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public Future<MultiMap> addPskDeviceToTenant(final String tenant, final String deviceId, final String key) {

        final CredentialsObject credentialsSpec =
                CredentialsObject.fromPresharedKey(deviceId, deviceId, key.getBytes(StandardCharsets.UTF_8), null, null);

        return registerDevice(tenant, deviceId)
                .compose(ok -> addCredentials(tenant, JsonObject.mapFrom(credentialsSpec)));
    }

}
