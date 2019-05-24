/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing Hono's Registration API.
 * <p>
 * An instance of this interface is always scoped to a specific tenant.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/api/device-registration-api/">
 * Registration API specification</a> for a description of the result codes returned.
 */
public interface RegistrationClient extends RequestResponseClient {

    /**
     * Asserts that a device is registered and <em>enabled</em>.
     * 
     * @param deviceId The ID of the device to get the assertion for.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         registration service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#assert-device-registration">
     *         Assert Device Registration</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<JsonObject> assertRegistration(String deviceId);

    /**
     * Asserts that a device is registered and <em>enabled</em>.
     * 
     * @param deviceId The ID of the device to get the assertion for.
     * @param gatewayId The gateway that wants to act on behalf of the device.
     *                  <p>
     *                  If not {@code null}, the service will verify that the gateway
     *                  is enabled and authorized to <em>act on behalf of</em> the
     *                  given device before asserting the device's registration status.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         registration service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#assert-device-registration">
     *         Assert Device Registration</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<JsonObject> assertRegistration(String deviceId, String gatewayId);

    /**
     * Asserts that a device is registered and <em>enabled</em>.
     * <p>
     * This default implementation simply returns the result of {@link #assertRegistration(String, String)}.
     * 
     * @param deviceId The ID of the device to get the assertion for.
     * @param gatewayId The gateway that wants to act on behalf of the device.
     *                  <p>
     *                  If not {@code null}, the service will verify that the gateway
     *                  is enabled and authorized to <em>act on behalf of</em> the
     *                  given device before asserting the device's registration status.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         registration service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#assert-device-registration">
     *         Assert Device Registration</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    default Future<JsonObject> assertRegistration(
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        return assertRegistration(deviceId, gatewayId);
    }

    /**
     * Gets registration information for a device.
     *
     * @param deviceId The id of the device to check.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         registration service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#get-registration-information">
     *         Get Registration Information</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<JsonObject> get(String deviceId);

    /**
     * Gets registration information for a device.
     * <p>
     * This default implementation simply returns the result of {@link #get(String)}.
     *
     * @param deviceId The id of the device to check.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the registration service.
     *         The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#get-registration-information"> Get
     *         Registration Information</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing the (error) status
     *         code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    default Future<JsonObject> get(String deviceId, final SpanContext context) {
        return get(deviceId);
    }

    /**
     * Registers a device with Hono.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it.
     *
     * @param deviceId The id of the device to register.
     * @param data The data to register with the device.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 201 has been received from the
     *         registration service.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<Void> register(String deviceId, JsonObject data);

    /**
     * Updates the data a device has been registered with.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it.
     *
     * @param deviceId The id of the device to register.
     * @param data The data to update the registration with (may be {@code null}).
     *             The original data will be <em>replaced</em> with this data, i.e.
     *             the data will not be merged with the existing data.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 204 has been received from the
     *         registration service.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<Void> update(String deviceId, JsonObject data);

    /**
     * Deregisters a device from Hono.
     * <p>
     * Once a device has been (successfully) deregistered, no more telemtry data can be uploaded
     * for it nor can commands be sent to it anymore.
     *
     * @param deviceId The id of the device to deregister.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 204 has been received from the
     *         registration service.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<Void> deregister(String deviceId);

    /**
     * Sets the given gateway as the last gateway that acted on behalf of the given device.
     * <p>
     * If a device connects directly instead of through a gateway, the device identifier is to be used as value for
     * the <em>gatewayId</em> parameter.
     * <p>
     * This association between device and its last-used gateway is used for scenarios where devices with multiple
     * potential gateways are used, along with gateways subscribing to command messages only using their gateway id.
     * In such scenarios, the value set here is needed to route command messages to the right gateway.
     *
     * @param deviceId The device id.
     * @param gatewayId The gateway id (or the device id if the last message came from the device directly).
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating whether the operation succeeded or not.
     * @throws NullPointerException if tenant id, device id or gateway id is {@code null}.
     */
    Future<Void> setLastUsedGateway(String deviceId, String gatewayId, SpanContext context);

    /**
     * Gets the gateway that last acted on behalf of the given device.
     * <p>
     * If the given device doesn't support usage via a gateway, a succeeded future with the given device id itself is
     * returned.
     * <p>
     * If no last-used gateway has been set for the given device yet, a failed future with status <em>Not Found</em>
     * is returned.
     *
     * @param deviceId The device id.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the registration service.
     *         In that case the value of the future will contain a <em>device-id</em> property with either the
     *         gateway id or with the device id itself if the device doesn't support usage via a gateway.
     *         <p>
     *         In case a status other then 200 is received, the future will fail with a
     *         {@link ServiceInvocationException} containing the (error) status code returned by the service.
     * @throws NullPointerException if tenant id or device id is {@code null}.
     */
    Future<JsonObject> getLastUsedGateway(String deviceId, SpanContext context);
}
