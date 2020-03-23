/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.service.device;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * An abstract base class implementation for {@link RegistrationService}.
 * <p>
 * The default implementation of <em>assertRegistration</em> relies on
 * {@link AbstractRegistrationService#processAssertRegistration(DeviceKey, Span)} to retrieve a device's registration
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

    protected TenantInformationService tenantInformationService;

    /**
     * Set tenant information service.
     * @param tenantInformationService The tenant information service.
     */
    @Autowired
    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = tenantInformationService;
    }

    /**
     * Gets device registration data by device ID.
     *
     * @param deviceKey The ID of the device to get registration data for.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The <em>status</em> will be
     *            <ul>
     *            <li><em>200 OK</em> if a device with the given ID is registered for the tenant. The <em>payload</em>
     *            will contain the properties registered for the device.</li>
     *            <li><em>404 Not Found</em> if no device with the given identifier is registered for the tenant.</li>
     *            </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/device-registration/#get-registration-information"> Device
     *      Registration API - Get Registration Information</a>
     */
    protected abstract Future<RegistrationResult> processAssertRegistration(DeviceKey deviceKey, Span span);

    /**
     * Takes the 'viaGroups' list of a device and resolves all group ids with the ids of the devices that are
     * a member of those groups.
     *
     * @param tenantId The tenant the device belongs to.
     * @param viaGroups The viaGroups list of a device. This list contains the ids of groups.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. A failed future with 
     *           a {@link ServiceInvocationException} is returned, if there was an error resolving the groups.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected abstract Future<JsonArray> resolveGroupMembers(String tenantId, JsonArray viaGroups, Span span);

    @Override
    public final Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId) {
        return assertRegistration(tenantId, deviceId, NoopSpan.INSTANCE);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Subclasses may override this method in order to implement a more sophisticated approach for asserting
     * registration status, e.g. using cached information etc. This method requires a functional
     * {@link #processAssertRegistration(DeviceKey, Span) processAssertRegistration} method to work.
     */
    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(tenantKeyResult -> tenantKeyResult.isError()
                        ? Future.succeededFuture(RegistrationResult.from(tenantKeyResult.getStatus()))
                        : processAssertRegistration(DeviceKey.from(tenantKeyResult.getPayload(), deviceId), span)
                        .compose(result -> {
                            if (isDeviceEnabled(result)) {
                                final JsonObject deviceData = result.getPayload()
                                        .getJsonObject(RegistrationConstants.FIELD_DATA);
                                return createSuccessfulRegistrationResult(tenantId, deviceId, deviceData, span);
                            } else {
                                TracingHelper.logError(span, "device not enabled");
                                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
                            }
                        })
                );
    }

    @Override
    public final Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId, final String gatewayId) {
        return assertRegistration(tenantId, deviceId, gatewayId, NoopSpan.INSTANCE);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Subclasses may override this method in order to implement a more sophisticated approach for asserting
     * registration status, e.g. using cached information etc. This method requires a functional
     * {@link #processAssertRegistration(DeviceKey, Span) processAssertRegistration} method to work.
     */
    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId, final String gatewayId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(span);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result ->  {

                    if (result.isError()) {
                        return Future.succeededFuture(RegistrationResult.from(result.getStatus()));
                    }

                    final TenantKey tenantKey = result.getPayload();

                    final Future<RegistrationResult> deviceInfoTracker = processAssertRegistration(DeviceKey.from(tenantKey, deviceId), span);
                    final Future<RegistrationResult> gatewayInfoTracker = processAssertRegistration(DeviceKey.from(tenantKey, gatewayId), span);

                    return CompositeFuture
                            .all(deviceInfoTracker, gatewayInfoTracker)
                            .compose(ok -> {

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
                                        return createSuccessfulRegistrationResult(tenantId, deviceId, deviceData, span);
                                    } else {
                                        log.debug("gateway not authorized");
                                        TracingHelper.logError(span, "gateway not authorized");
                                        return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_FORBIDDEN));
                                    }
                                }
                            });
                });
    }

    /**
     * Checks if a gateway is authorized to act <em>on behalf of</em> a device.
     * <p>
     * This default implementation checks if the gateway's identifier matches the value of the
     * {@link RegistrationConstants#FIELD_VIA} property in the device's registration information. The property may
     * either contain a single String value or a JSON array of Strings.
     * Alternatively, the {@link RegistrationConstants#FIELD_VIA_GROUPS} property value in the device's registration
     * information is checked against the group membership defined in the gateway's registration information.
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
    protected boolean isGatewayAuthorized(final String gatewayId, final JsonObject gatewayData, final String deviceId, final JsonObject deviceData) {
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(gatewayData);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(deviceData);

        final JsonArray via = convertObjectToJsonArray(deviceData.getValue(RegistrationConstants.FIELD_VIA));
        final JsonArray viaGroups = convertObjectToJsonArray(deviceData.getValue(RegistrationConstants.FIELD_VIA_GROUPS));
        final JsonArray gatewayDataMemberOf = convertObjectToJsonArray(gatewayData.getValue(RegistrationConstants.FIELD_MEMBER_OF));

        return isGatewayInVia(gatewayId, via) || anyGatewayGroupInViaGroups(gatewayDataMemberOf, viaGroups);
    }

    private boolean isGatewayInVia(final String gatewayId, final JsonArray via) {
        return via
                .stream()
                .filter(String.class::isInstance)
                .anyMatch(gatewayId::equals);
    }

    private boolean anyGatewayGroupInViaGroups(final JsonArray gatewayDataMemberOf, final JsonArray viaGroups) {
        return viaGroups
                .stream()
                .filter(String.class::isInstance)
                .anyMatch(gatewayDataMemberOf::contains);
    }

    private JsonArray convertObjectToJsonArray(final Object object) {
        final JsonArray array;
        if (object instanceof JsonArray) {
            array = (JsonArray) object;
        } else {
            array = new JsonArray();
            if (object instanceof String) {
                array.add((String) object);
            }
        }
        return array;
    }

    private Future<RegistrationResult> createSuccessfulRegistrationResult(
            final String tenantId,
            final String deviceId,
            final JsonObject deviceData,
            final Span span) {
        return getAssertionPayload(tenantId, deviceId, deviceData, span)
                .compose(payload -> Future.succeededFuture(RegistrationResult.from(
                        HttpURLConnection.HTTP_OK,
                        payload,
                        getRegistrationAssertionCacheDirective(deviceId, tenantId))))
                .recover(thr -> Future.succeededFuture(RegistrationResult.from(ServiceInvocationException.extractStatusCode(thr))));
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
     * @param registrationInfo The device registration information.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed with the payload
     *         or it will fail with a {@link ServiceInvocationException} indicating the cause of the failure.
     */
    protected final Future<JsonObject> getAssertionPayload(final String tenantId, final String deviceId,
            final JsonObject registrationInfo) {
        return getAssertionPayload(tenantId, deviceId, registrationInfo, NoopSpan.INSTANCE);
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
     * @param registrationInfo The device registration information.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed with the payload
     *         or it will fail with a {@link ServiceInvocationException} indicating the cause of the failure.
     */
    protected final Future<JsonObject> getAssertionPayload(final String tenantId, final String deviceId,
            final JsonObject registrationInfo, final Span span) {
        return getSupportedGatewaysForDevice(tenantId, deviceId, registrationInfo, span)
                .compose(via -> {
                    final JsonObject result = new JsonObject()
                            .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
                    if (!via.isEmpty()) {
                        result.put(RegistrationConstants.FIELD_VIA, via);
                    }
                    final JsonObject defaults = registrationInfo.getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS);
                    if (defaults != null) {
                        result.put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, defaults);
                    }
                    return Future.succeededFuture(result);
                });
    }

    /**
     * Checks if a given device allows a gateway to possibly act on behalf of the given device. This is determined by checking whether the
     * {@link RegistrationConstants#FIELD_VIA} property or the {@link RegistrationConstants#FIELD_VIA_GROUPS} property
     * of the registration data of the given device have one or more entries.
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
        final Object viaGroups = registrationInfo.getValue(RegistrationConstants.FIELD_VIA_GROUPS);

        return (viaObj instanceof String && !((String) viaObj).isEmpty())
                || (viaObj instanceof JsonArray && !((JsonArray) viaObj).isEmpty())
                || (viaGroups instanceof String && !((String) viaGroups).isEmpty())
                || (viaGroups instanceof JsonArray && !((JsonArray) viaGroups).isEmpty());
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
     * To compile this list of gateways, this default implementation gets the list of gateways from the value of the
     * {@link RegistrationConstants#FIELD_VIA} property in the device's registration information and resolves
     * the members of the groups that are in the {@link RegistrationConstants#FIELD_VIA_GROUPS} property of the device.
     * <p>
     * Subclasses may override this method to provide a different means to determine the supported gateways.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param registrationInfo The device's registration information.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed with the list of gateways as a JSON array of Strings (never {@code null})
     *         or it will fail with a {@link ServiceInvocationException} indicating the cause of the failure.
     */
    protected Future<JsonArray> getSupportedGatewaysForDevice(final String tenantId, final String deviceId,
            final JsonObject registrationInfo, final Span span) {
        final JsonArray via = convertObjectToJsonArray(registrationInfo.getValue(RegistrationConstants.FIELD_VIA));
        final JsonArray viaGroups = convertObjectToJsonArray(registrationInfo.getValue(RegistrationConstants.FIELD_VIA_GROUPS));

        final Future<JsonArray> resultFuture;
        if (viaGroups.isEmpty()) {
            resultFuture = Future.succeededFuture(via);
        } else {
            resultFuture = resolveGroupMembers(tenantId, viaGroups, span).compose(groupMembers -> {
                for (final Object gateway : groupMembers) {
                    if (!via.contains(gateway)) {
                        via.add(gateway);
                    }
                }
                return Future.succeededFuture(via);
            }).recover(thr -> {
                log.debug("failed to resolve group members", thr);
                TracingHelper.logError(span, "failed to resolve group members: " + thr.getMessage());
                return Future.failedFuture(thr);
            });
        }
        return resultFuture;
    }
}
