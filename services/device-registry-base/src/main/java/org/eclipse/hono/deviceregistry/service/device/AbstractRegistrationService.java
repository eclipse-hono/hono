/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceStatus;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * An abstract base class implementation for {@link RegistrationService}.
 * <p>
 * The default implementation of <em>assertRegistration</em> relies on
 * {@link AbstractRegistrationService#getRegistrationInformation(DeviceKey, Span)} to retrieve a device's registration
 * information from persistent storage. Thus, subclasses need to override (and implement) this method in order to get a
 * working implementation of the default assertion mechanism.
 */
public abstract class AbstractRegistrationService implements RegistrationService, Lifecycle {

    /**
     * The default number of seconds that information returned by this service's operations may be cached for.
     */
    public static final int DEFAULT_MAX_AGE_SECONDS = 300;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRegistrationService.class);
    private static final String MSG_DEVICE_NOT_ENABLED = "device not enabled";
    private static final String MSG_NO_SUCH_DEVICE = "no such device";

    /**
     * The service to use for retrieving information about tenants.
     */
    protected TenantInformationService tenantInformationService = new NoopTenantInformationService();

    private EdgeDeviceAutoProvisioner edgeDeviceAutoProvisioner;

    @Override
    public final Future<Void> start() {
        return startInternal()
            .compose(ok -> supportsEdgeDeviceAutoProvisioning() ? edgeDeviceAutoProvisioner.start() : Future.succeededFuture())
            .mapEmpty();
    }

    @Override
    public final Future<Void> stop() {
        return stopInternal()
            .compose(ok -> supportsEdgeDeviceAutoProvisioning() ? edgeDeviceAutoProvisioner.stop() : Future.succeededFuture())
            .mapEmpty();
    }

    /**
     * Enables subclasses to add custom startup logic, see {@link Lifecycle#start()}.
     *
     * @return A future indicating the outcome of the startup process.
     */
    protected Future<Void> startInternal() {
        return Future.succeededFuture();
    }

    /**
     * Enables subclasses to add custom shutdown logic, see {@link Lifecycle#stop()}.
     *
     * @return A future indicating the outcome of the shutdown process.
     */
    protected Future<Void> stopInternal() {
        return Future.succeededFuture();
    }

    /**
     * Sets the service to use for checking existence of tenants.
     * <p>
     * If not set, tenant existence will not be verified.
     *
     * @param tenantInformationService The tenant information service.
     * @throws NullPointerException if service is {@code null};
     */
    public final void setTenantInformationService(final TenantInformationService tenantInformationService) {
        Objects.requireNonNull(tenantInformationService);
        LOG.info("using {}", tenantInformationService);
        this.tenantInformationService = tenantInformationService;
    }

    /**
     * Sets an instance of the edge device auto-provisioner.
     * <p>
     * If set, edge device auto-provisioning will be performed. Defaults to {@code null} meaning edge device 
     * auto-provisioning is disabled.
     *
     * @param edgeDeviceAutoProvisioner An instance of the edge device auto-provisioner.
     * @throws NullPointerException if parameter is {@code null};
     */
    public void setEdgeDeviceAutoProvisioner(final EdgeDeviceAutoProvisioner edgeDeviceAutoProvisioner) {
        Objects.requireNonNull(edgeDeviceAutoProvisioner);
        this.edgeDeviceAutoProvisioner = edgeDeviceAutoProvisioner;
    }

    /**
     * Gets the information registered for a device.
     *
     * @param deviceKey The identifier of the device to get registration information for.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The <em>status</em> will be
     *            <ul>
     *            <li><em>200 OK</em>, if a device with the given ID is registered for the tenant.<br>
     *            The <em>payload</em> will contain a JSON object with the following properties:
     *              <ul>
     *              <li><em>device-id</em> - the device identifier</li>
     *              <li><em>data</em> - the information registered for the device, i.e. the registered
     *                  {@link org.eclipse.hono.service.management.device.Device}</li>
     *              </ul>
     *            </li>
     *            <li><em>404 Not Found</em>, if no device with the given identifier is registered for the tenant.</li>
     *            </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected abstract Future<RegistrationResult> getRegistrationInformation(DeviceKey deviceKey, Span span);

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
    Future<JsonArray> resolveGroupMembers(final String tenantId, final JsonArray viaGroups, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(viaGroups);
        Objects.requireNonNull(span);

        final var viaGroupsAsString = viaGroups.stream()
                .filter(String.class::isInstance).map(String.class::cast)
                .collect(Collectors.toSet());

        return processResolveGroupMembers(tenantId, viaGroupsAsString, span)
                .map(deviceIds -> {
                    final var result = new JsonArray();
                    deviceIds.forEach(result::add);
                    return result;
                });

    }

    /**
     * Takes the 'viaGroups' list of a device and resolves all group ids with the ids of the devices that are
     * a member of those groups.
     *
     * @param tenantId The tenant the device belongs to.
     * @param viaGroups The viaGroups list of a device. This list contains the ids of groups.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     * implementation should log (error) events on this span and it may set tags and use this span as the
     * parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. A set of device ID that belong to the provided groups.
     * A failed future with a {@link ServiceInvocationException} is returned, if there was an error resolving the groups.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected abstract Future<Set<String>> processResolveGroupMembers(String tenantId, Set<String> viaGroups, Span span);

    @Override
    public final Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId) {
        return assertRegistration(tenantId, deviceId, NoopSpan.INSTANCE);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Subclasses may override this method in order to implement a more sophisticated approach for asserting
     * registration status, e.g. using cached information etc. This method requires a functioning
     * {@link #getRegistrationInformation(DeviceKey, Span)} method to work.
     */
    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(tenantKeyResult -> {
                    if (tenantKeyResult.isError()) {
                        return Future.succeededFuture(RegistrationResult.from(tenantKeyResult.getStatus()));
                    } else {
                        return getRegistrationInformation(DeviceKey.from(tenantKeyResult.getPayload(), deviceId), span)
                                .compose(result -> {
                                    if (result.isNotFound()) {
                                        LOG.debug(MSG_NO_SUCH_DEVICE);
                                        TracingHelper.logError(span, MSG_NO_SUCH_DEVICE);
                                        return Future.succeededFuture(RegistrationResult.from(result.getStatus()));
                                    } else if (isDeviceEnabled(result)) {
                                        final JsonObject deviceData = result.getPayload()
                                                .getJsonObject(RegistrationConstants.FIELD_DATA);
                                        return createSuccessfulRegistrationResult(tenantId, deviceId, deviceData, span);
                                    } else {
                                        LOG.debug(MSG_DEVICE_NOT_ENABLED);
                                        TracingHelper.logError(span, MSG_DEVICE_NOT_ENABLED);
                                        return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
                                    }
                                });
                    }
                });
    }

    @Override
    public final Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId, final String gatewayId) {
        return assertRegistration(tenantId, deviceId, gatewayId, NoopSpan.INSTANCE);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Subclasses may override this method in order to implement a more sophisticated approach for asserting
     * registration status, e.g. using cached information etc. This method relies on
     * {@link #getRegistrationInformation(DeviceKey, Span)} to retrieve the information registered for
     * the given device.
     */
    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId, final String gatewayId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(span);

        return this.tenantInformationService.getTenant(tenantId, span)
                .compose(tenant -> {
                    final Future<RegistrationResult> deviceInfoTracker = getRegistrationInformation(DeviceKey.from(tenantId, deviceId), span);
                    final Future<RegistrationResult> gatewayInfoTracker = getRegistrationInformation(DeviceKey.from(tenantId, gatewayId), span);

                    return Future
                            .all(deviceInfoTracker, gatewayInfoTracker)
                            .compose(ok -> {

                                final RegistrationResult deviceResult = deviceInfoTracker.result();
                                final RegistrationResult gatewayResult = gatewayInfoTracker.result();

                                if (deviceResult.isNotFound() && !gatewayResult.isNotFound()
                                        && isDeviceEnabled(gatewayResult)
                                        && hasAuthorityForAutoRegistration(gatewayResult)
                                        && supportsEdgeDeviceAutoProvisioning()) {

                                    final Device device = new Device()
                                            .setEnabled(true)
                                            .setVia(Collections.singletonList(gatewayId))
                                            .setStatus(new DeviceStatus().setAutoProvisioned(true));

                                    final JsonArray memberOf = gatewayResult.getPayload()
                                            .getJsonObject(RegistrationConstants.FIELD_DATA)
                                            .getJsonArray(RegistryManagementConstants.FIELD_MEMBER_OF);
                                    Optional.ofNullable(memberOf).ifPresent(array -> device.setViaGroups(array.stream()
                                        .filter(String.class::isInstance)
                                        .map(String.class::cast)
                                        .toList()));

                                    LOG.debug("auto-provisioning device {} for gateway {}", deviceId, gatewayId);
                                    return edgeDeviceAutoProvisioner.performAutoProvisioning(tenantId, tenant, deviceId, 
                                            gatewayId, device, span.context())
                                            .compose(newDevice -> {
                                                final JsonObject deviceData = JsonObject.mapFrom(newDevice);
                                                return createSuccessfulRegistrationResult(tenantId, deviceId,
                                                        deviceData, span);
                                            })
                                            .recover(this::convertToRegistrationResult);
                                } else if (!isDeviceEnabled(deviceResult)) {
                                    if (deviceResult.isNotFound()) {
                                        LOG.debug(MSG_NO_SUCH_DEVICE);
                                        TracingHelper.logError(span, MSG_NO_SUCH_DEVICE);
                                    } else {
                                        LOG.debug(MSG_DEVICE_NOT_ENABLED);
                                        TracingHelper.logError(span, MSG_DEVICE_NOT_ENABLED);
                                    }
                                    return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
                                } else if (!isDeviceEnabled(gatewayResult)) {
                                    if (gatewayResult.isNotFound()) {
                                        LOG.debug("no such gateway");
                                        TracingHelper.logError(span, "no such gateway");
                                    } else {
                                        LOG.debug("gateway not enabled");
                                        TracingHelper.logError(span, "gateway not enabled");
                                    }
                                    return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_FORBIDDEN));
                                } else {

                                    final JsonObject deviceData = deviceResult.getPayload()
                                            .getJsonObject(RegistrationConstants.FIELD_DATA, new JsonObject());
                                    final JsonObject gatewayData = gatewayResult.getPayload()
                                            .getJsonObject(RegistrationConstants.FIELD_DATA, new JsonObject());

                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Device data: {}", deviceData.encodePrettily());
                                        LOG.debug("Gateway data: {}", gatewayData.encodePrettily());
                                    }

                                    if (isGatewayAuthorized(gatewayId, gatewayData, deviceId, deviceData)) {
                                        if (supportsEdgeDeviceAutoProvisioning()) {
                                            final Device device = deviceData.mapTo(Device.class);
                                            return edgeDeviceAutoProvisioner
                                                    .sendDelayedAutoProvisioningNotificationIfNeeded(tenantId, tenant,
                                                            deviceId, gatewayId, device, span)
                                                    .compose(v -> createSuccessfulRegistrationResult(tenantId, deviceId,
                                                            deviceData, span));
                                        } else {
                                            return createSuccessfulRegistrationResult(tenantId, deviceId, deviceData, span);
                                        }
                                    } else {
                                        LOG.debug("gateway not authorized");
                                        TracingHelper.logError(span, "gateway not authorized");
                                        return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_FORBIDDEN));
                                    }
                                }
                            });
                })
                .recover(this::convertToRegistrationResult);
    }

    private boolean supportsEdgeDeviceAutoProvisioning() {
        return edgeDeviceAutoProvisioner != null;
    }

    /**
     * Checks if a gateway is authorized to act <em>on behalf of</em> a device.
     * <p>
     * This default implementation checks if the gateway's identifier matches the value of the
     * {@link RegistryManagementConstants#FIELD_VIA} property in the device's registration information. The property may
     * either contain a single String value or a JSON array of Strings.
     * Alternatively, the {@link RegistryManagementConstants#FIELD_VIA_GROUPS} property value in the device's registration
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

        final JsonArray via = convertObjectToJsonArray(deviceData.getValue(RegistryManagementConstants.FIELD_VIA));
        final JsonArray viaGroups = convertObjectToJsonArray(deviceData.getValue(RegistryManagementConstants.FIELD_VIA_GROUPS));
        final JsonArray gatewayDataMemberOf = convertObjectToJsonArray(gatewayData.getValue(RegistryManagementConstants.FIELD_MEMBER_OF));

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
        if (object instanceof JsonArray jsonarray) {
            array = jsonarray;
        } else {
            array = new JsonArray();
            if (object instanceof String) {
                array.add(object);
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
                            .put(RequestResponseApiConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
                    if (!via.isEmpty()) {
                        result.put(RegistrationConstants.FIELD_VIA, via);
                    }
                    Optional.ofNullable(registrationInfo.getString(RegistrationConstants.FIELD_DOWNSTREAM_MESSAGE_MAPPER))
                            .ifPresent(value -> result.put(RegistrationConstants.FIELD_DOWNSTREAM_MESSAGE_MAPPER, value));

                    Optional.ofNullable(registrationInfo.getString(RegistrationConstants.FIELD_UPSTREAM_MESSAGE_MAPPER))
                            .ifPresent(value -> result.put(RegistrationConstants.FIELD_UPSTREAM_MESSAGE_MAPPER, value));

                    Optional.ofNullable(registrationInfo.getJsonObject(RequestResponseApiConstants.FIELD_PAYLOAD_DEFAULTS))
                            .ifPresent(value -> result.put(RequestResponseApiConstants.FIELD_PAYLOAD_DEFAULTS, value));

                    Optional.ofNullable(registrationInfo.getJsonObject(RegistrationConstants.FIELD_COMMAND_ENDPOINT))
                            .ifPresent(value -> result.put(RegistrationConstants.FIELD_COMMAND_ENDPOINT, value));
                    return Future.succeededFuture(result);
                });
    }

    /**
     * Checks if any gateways are allowed to act on behalf of a given device.
     * <p>
     * This is determined by checking whether the {@link RegistryManagementConstants#FIELD_VIA} property
     * or the {@link RegistryManagementConstants#FIELD_VIA_GROUPS} property of the registration data
     * of the given device have one or more entries.
     * <p>
     * Subclasses may override this method to provide a different means to determine gateway support.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param registrationInfo The device's registration information.
     * @return {@code true} if a gateway may act on behalf of the given device.
     */
    protected boolean isGatewaySupportedForDevice(
            final String tenantId,
            final String deviceId,
            final JsonObject registrationInfo) {

        final Object viaObj = registrationInfo.getValue(RegistryManagementConstants.FIELD_VIA);
        final Object viaGroups = registrationInfo.getValue(RegistryManagementConstants.FIELD_VIA_GROUPS);

        return (viaObj instanceof String objString && !objString.isEmpty())
                || (viaObj instanceof JsonArray jsonarray && !jsonarray.isEmpty())
                || (viaGroups instanceof String groupsString && !groupsString.isEmpty())
                || (viaGroups instanceof JsonArray jsonarray && !jsonarray.isEmpty());
    }

    private boolean isDeviceEnabled(final RegistrationResult registrationResult) {
        return registrationResult.isOk() &&
                isDeviceEnabled(registrationResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA));
    }

    private boolean isDeviceEnabled(final JsonObject registrationData) {
        return registrationData.getBoolean(RequestResponseApiConstants.FIELD_ENABLED, Boolean.TRUE);
    }

    private boolean hasAuthorityForAutoRegistration(final RegistrationResult registrationResult) {
        final JsonArray authorities = registrationResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA)
                .getJsonArray(RegistryManagementConstants.FIELD_AUTHORITIES);

        return authorities != null && authorities.contains(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED);
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
     * Gets the list of gateways that may act on behalf of a given device.
     * <p>
     * To compile this list of gateways, this default implementation gets the list of gateways from the value of the
     * {@link RegistryManagementConstants#FIELD_VIA} property in the device's registration information and resolves
     * the members of the groups that are in the {@link RegistryManagementConstants#FIELD_VIA_GROUPS} property of the device.
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
    protected Future<JsonArray> getSupportedGatewaysForDevice(
            final String tenantId,
            final String deviceId,
            final JsonObject registrationInfo,
            final Span span) {

        final JsonArray via = convertObjectToJsonArray(registrationInfo.getValue(RegistryManagementConstants.FIELD_VIA));
        final JsonArray viaGroups = convertObjectToJsonArray(registrationInfo.getValue(RegistryManagementConstants.FIELD_VIA_GROUPS));

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
                LOG.debug("failed to resolve group members", thr);
                TracingHelper.logError(span, "failed to resolve group members: " + thr.getMessage());
                return Future.failedFuture(thr);
            });
        }
        return resultFuture;
    }

    private Future<RegistrationResult> convertToRegistrationResult(final Throwable error) {
        LOG.debug("error occurred during device registration", error);
        return Future.succeededFuture(RegistrationResult.from(ServiceInvocationException.extractStatusCode(error),
                new JsonObject().put(Constants.JSON_FIELD_DESCRIPTION, error.getMessage())));
    }
}
