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

package org.eclipse.hono.service.registration;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Objects;

import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * An abstract base class implementation for {@link RegistrationService}.
 * <p>
 * The default implementation of <em>assertRegistration</em> relies on
 * {@link AbstractRegistrationService#getDevice(String, String, Span, Handler)} to retrieve a device's registration
 * information from persistent storage. Thus, subclasses need to override (and implement) this method in order to get a
 * working implementation of the default assertion mechanism.
 * 
 */
public abstract class AbstractRegistrationService implements RegistrationService {

    /**
     * The default number of seconds that information returned by this service's operations may be cached for.
     */
    public static final int DEFAULT_MAX_AGE_SECONDS = 300;

    private static final Logger log = LoggerFactory.getLogger(AbstractRegistrationService.class);

    /**
     * Gets device registration data by device ID.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to get registration data for.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @param resultHandler The handler to invoke with the result of the operation. The <em>status</em> will be
     *            <ul>
     *            <li><em>200 OK</em> if a device with the given ID is registered for the tenant. The <em>payload</em>
     *            will contain the properties registered for the device.</li>
     *            <li><em>404 Not Found</em> if no device with the given identifier is registered for the tenant.</li>
     *            </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/device-registration/#get-registration-information"> Device
     *      Registration API - Get Registration Information</a>
     */
    protected abstract void getDevice(
            String tenantId,
            String deviceId,
            Span span,
            Handler<AsyncResult<RegistrationResult>> resultHandler);

    @Override
    public final void assertRegistration(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        assertRegistration(tenantId, deviceId, NoopSpan.INSTANCE, resultHandler);
    }


    /**
     * {@inheritDoc}
     * <p>
     * Subclasses may override this method in order to implement a more sophisticated approach for asserting
     * registration status, e.g. using cached information etc. This method requires a functional
     * {@link #getDevice(String, String, Span, Handler) getDevice} method to work.
     */
    @Override
    public void assertRegistration(
            final String tenantId,
            final String deviceId,
            final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);
        Objects.requireNonNull(resultHandler);

        final Future<RegistrationResult> getResultTracker = Future.future();
        getDevice(tenantId, deviceId, span, getResultTracker);

        getResultTracker.compose(result -> {
            if (isDeviceEnabled(result)) {
                final JsonObject deviceData = result.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA);
                return Future.succeededFuture(createSuccessfulRegistrationResult(tenantId, deviceId, deviceData));
            } else {
                TracingHelper.logError(span, "device not enabled");
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
            }
        }).setHandler(resultHandler);
    }

    @Override
    public final void assertRegistration(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        assertRegistration(tenantId, deviceId, gatewayId, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Subclasses may override this method in order to implement a more sophisticated approach for asserting
     * registration status, e.g. using cached information etc. This method requires a functional
     * {@link #getDevice(String, String, Span, Handler) getDevice} method to work.
     */
    @Override
    public void assertRegistration(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(span);
        Objects.requireNonNull(resultHandler);

        final Future<RegistrationResult> deviceInfoTracker = Future.future();
        final Future<RegistrationResult> gatewayInfoTracker = Future.future();

        getDevice(tenantId, deviceId, span, deviceInfoTracker);
        getDevice(tenantId, gatewayId, span, gatewayInfoTracker);

        CompositeFuture.all(deviceInfoTracker, gatewayInfoTracker).compose(ok -> {

            final RegistrationResult deviceResult = deviceInfoTracker.result();
            final RegistrationResult gatewayResult = gatewayInfoTracker.result();

            if (!isDeviceEnabled(deviceResult)) {
                log.debug("device not enabled");
                TracingHelper.logError(span, "device not enabled");
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
            } else if (!isDeviceEnabled(gatewayResult)) {
                log.debug("gateway not enabled");
                TracingHelper.logError(span, "gateway not enabled");
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_FORBIDDEN));
            } else {

                final JsonObject deviceData = deviceResult.getPayload()
                        .getJsonObject(RegistrationConstants.FIELD_DATA, new JsonObject());
                final JsonObject gatewayData = gatewayResult.getPayload()
                        .getJsonObject(RegistrationConstants.FIELD_DATA, new JsonObject());

                if (log.isDebugEnabled()) {
                    log.debug("Device data: {}", deviceData.encodePrettily());
                    log.debug("Gateway data: {}", gatewayData.encodePrettily());
                }

                if (isGatewayAuthorized(gatewayId, gatewayData, deviceId, deviceData)) {
                    return Future.succeededFuture(createSuccessfulRegistrationResult(tenantId, deviceId, deviceData));
                } else {
                    log.debug("gateway not authorized");
                    TracingHelper.logError(span, "gateway not authorized");
                    return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_FORBIDDEN));
                }
            }
        }).setHandler(resultHandler);
    }

    /**
     * Checks if a gateway is authorized to act <em>on behalf of</em> a device.
     * <p>
     * This default implementation checks if the gateway's identifier matches the value of the
     * {@link RegistrationConstants#FIELD_VIA} property in the device's registration information. The property may
     * either contain a single String value or a JSON array of Strings.
     * <p>
     * Subclasses may override this method in order to implement a more sophisticated check.
     *
     * @param gatewayId The identifier of the gateway.
     * @param gatewayData The data registered for the gateway.
     * @param deviceId The identifier of the device.
     * @param deviceData The data registered for the device.
     * @return {@code true} if the gateway is authorized.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected boolean isGatewayAuthorized(final String gatewayId, final JsonObject gatewayData,
            final String deviceId, final JsonObject deviceData) {

        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(gatewayData);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(deviceData);

        final Object obj = deviceData.getValue(RegistrationConstants.FIELD_VIA);
        if (obj instanceof JsonArray) {
            // get the first matching entry
            return ((JsonArray) obj)
                    .stream()
                    .filter(String.class::isInstance)
                    .anyMatch(gatewayId::equals);
        } else if (obj instanceof String) {
            // compare the string directly
            return gatewayId.equals(obj);
        } else {
            // wrong type -> not authorized
            return false;
        }
    }

    private RegistrationResult createSuccessfulRegistrationResult(
            final String tenantId,
            final String deviceId,
            final JsonObject deviceData) {

        return RegistrationResult.from(
                HttpURLConnection.HTTP_OK,
                getAssertionPayload(tenantId, deviceId, deviceData),
                getRegistrationAssertionCacheDirective(deviceId, tenantId));
    }

    /**
     * Creates the payload of the assert Registration response message.
     * <p>
     * The returned JSON object may contain the {@link RegistrationConstants#FIELD_VIA} property of the device's
     * registration information and may also contain <em>default</em> values registered for the device under key
     * {@link RegistrationConstants#FIELD_PAYLOAD_DEFAULTS}.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to create the assertion token for.
     * @param registrationInfo The device's registration information.
     * @return The payload.
     */
    protected final JsonObject getAssertionPayload(final String tenantId, final String deviceId,
            final JsonObject registrationInfo) {

        final JsonObject result = new JsonObject()
                .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        final JsonArray viaObj = getSupportedGatewaysForDevice(tenantId, deviceId, registrationInfo);
        if (!viaObj.isEmpty()) {
            result.put(RegistrationConstants.FIELD_VIA, viaObj);
        }
        final JsonObject defaults = registrationInfo.getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS);
        if (defaults != null) {
            result.put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, defaults);
        }
        return result;
    }

    /**
     * Checks if a gateway may act on behalf of the given device. This is determined by checking whether the
     * {@link RegistrationConstants#FIELD_VIA} property of the device registration data has one or more entries.
     * <p>
     * Subclasses may override this method to provide a different means to determine gateway support.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param registrationInfo The device's registration information.
     * @return {@code true} if a gateway may act on behalf of the given device.
     */
    protected boolean isGatewaySupportedForDevice(final String tenantId, final String deviceId,
            final JsonObject registrationInfo) {
        final Object viaObj = registrationInfo.getValue(RegistrationConstants.FIELD_VIA);
        return (viaObj instanceof String && !((String) viaObj).isEmpty())
                || (viaObj instanceof JsonArray && !((JsonArray) viaObj).isEmpty());
    }

    private boolean isDeviceEnabled(final RegistrationResult registrationResult) {
        return registrationResult.isOk() &&
                isDeviceEnabled(registrationResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA));
    }

    private boolean isDeviceEnabled(final JsonObject registrationData) {
        return registrationData.getBoolean(RegistrationConstants.FIELD_ENABLED, Boolean.TRUE);
    }

    /**
     * Gets the cache directive to include in responses to the assert Registration operation.
     * <p>
     * Subclasses should override this method in order to return a specific directive other than the default.
     * <p>
     * This default implementation returns a directive to cache values for {@link #DEFAULT_MAX_AGE_SECONDS} seconds.
     * 
     * @param deviceId The identifier of the device that is the subject of the assertion.
     * @param tenantId The tenant that the device belongs to.
     * @return The cache directive.
     */
    protected CacheDirective getRegistrationAssertionCacheDirective(final String deviceId, final String tenantId) {
        return CacheDirective.maxAgeDirective(DEFAULT_MAX_AGE_SECONDS);
    }

    /**
     * Gets the list of gateways that may act on behalf of the given device.
     * <p>
     * This default implementation gets the list of gateways from the value of the
     * {@link RegistrationConstants#FIELD_VIA} property in the device's registration information.
     * <p>
     * Subclasses may override this method to provide a different means to determine the supported gateways.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param registrationInfo The device's registration information.
     * @return The list of gateways as a JSON array of Strings (never {@code null}).
     */
    protected JsonArray getSupportedGatewaysForDevice(final String tenantId, final String deviceId,
            final JsonObject registrationInfo) {
        Object viaObj = registrationInfo.getValue(RegistrationConstants.FIELD_VIA);
        if (viaObj instanceof String) {
            viaObj = new JsonArray().add(viaObj);
        }
        return viaObj instanceof JsonArray ? (JsonArray) viaObj : new JsonArray(Collections.emptyList());
    }

}
