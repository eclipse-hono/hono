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
package org.eclipse.hono.deviceregistry.service.device;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.client.util.MessagingClient;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.DeviceStatus;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Abstract helper class for sending auto-provisioning event.
 */
public abstract class AbstractAutoProvisioningEventSender implements Lifecycle {
    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());
    protected final DeviceManagementService deviceManagementService;
    protected final MessagingClient<EventSender> eventClients;
    protected final Vertx vertx;

    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Creates an instance of {@link AbstractAutoProvisioningEventSender}.
     *
     * @param vertx The vert.x instance to use.
     * @param deviceManagementService The device management service.
     * @param eventClients The messaging clients to send auto-provisioned events.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public AbstractAutoProvisioningEventSender(final Vertx vertx,
            final DeviceManagementService deviceManagementService,
            final MessagingClient<EventSender> eventClients) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(deviceManagementService);
        Objects.requireNonNull(eventClients);

        this.vertx = vertx;
        this.deviceManagementService = deviceManagementService;
        this.eventClients = eventClients;
    }

    /**
     * {@inheritDoc}
     *
     * @return The future returned by the configured {@linkplain EventSender#start() event sender's start() method}.
     */
    @Override
    public final Future<Void> start() {
        if (started.compareAndSet(false, true)) {
            LOG.debug("starting up");
            // decouple establishment of the sender's downstream connection from this component's
            // start-up process and instead rely on the event sender's readiness check to succeed
            // once the connection has been established
            eventClients.start();
        }
        return Future.succeededFuture();
    }

    /**
     * {@inheritDoc}
     *
     * @return The future returned by the configured {@linkplain EventSender#stop() event sender's stop() method}.
     */
    @Override
    public final Future<Void> stop() {
        if (started.compareAndSet(true, false)) {
            LOG.debug("shutting down");
            return eventClients.stop();
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * Send an auto-provisioning event with content type
     * {@value EventConstants#CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION}.
     *
     * @param tenantId The tenant identifier.
     * @param tenant The tenant information.
     * @param deviceId The device identifier.
     * @param gatewayId The gateway identifier if an edge device is being auto-provisioned.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The future will be succeeded if the auto-provisioning
     *         event is sent successfully.
     * @throws NullPointerException if any of the parameters except gateway id is {@code null}.
     * @see "https://www.eclipse.org/hono/docs/api/event/#device-provisioning-notification"
     */
    protected Future<Void> sendAutoProvisioningEvent(
            final String tenantId,
            final Tenant tenant,
            final String deviceId,
            final String gatewayId,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);

        LOG.debug("sending auto-provisioning event [tenant-id: {}, device-id: {}, gateway-id: {}]", tenant, deviceId,
                gatewayId);

        // TODO to remove once able to send events without providing an argument of type TenantObject
        final TenantObject tenantConfig = DeviceRegistryUtils.convertTenant(tenantId, tenant)
                .mapTo(TenantObject.class);
        final EventSender eventSender = eventClients.getClient(tenantConfig);

        return eventSender
                .sendEvent(tenantConfig, new RegistrationAssertion(deviceId),
                        EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION, null,
                        assembleAutoProvisioningEventProperties(tenantId, gatewayId), span.context())
                .onSuccess(ok -> {
                    span.log("sent auto-provisioning event successfully");
                    LOG.debug(
                            "sent auto-provisioning event successfully [tenant-id: {}, device-id: {}, gateway-id: {}]",
                            tenantId, deviceId, gatewayId);
                })
                .onFailure(t -> LOG.warn(
                        "error sending auto-provisioning event [tenant-id: {}, device-id: {}, gateway-id: {}]",
                        tenantId, deviceId, gatewayId));
    }

    /**
     * Update the device registration information that the auto-provisioning notification has been successfully sent.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The edge device identifier.
     * @param device The edge device registration information.
     * @param deviceVersion The version of the device registration information to check before update,
     *                      may be {@link Optional#empty()}.
     * @param span The span to be used for tracing this operation.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected Future<Void> updateAutoProvisioningNotificationSent(final String tenantId,
            final String deviceId, final Device device, final Optional<String> deviceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(device);
        Objects.requireNonNull(deviceVersion);
        Objects.requireNonNull(span);

        Optional.ofNullable(device.getStatus())
                .ifPresentOrElse(
                        status -> status.setAutoProvisioningNotificationSent(true),
                        () -> device.setStatus(new DeviceStatus().setAutoProvisioningNotificationSent(true)));

        return deviceManagementService.updateDevice(tenantId, deviceId, device, deviceVersion, span)
                .compose(result -> {
                    if (HttpURLConnection.HTTP_NO_CONTENT == result.getStatus()) {
                        return Future.succeededFuture();
                    } else {
                        final String errorMessage = String.format(
                                "error updating device with 'AutoProvisioningNotificationSent=true' [status: %s, tenant-id: %s, device-id: %s, device-version: %s]",
                                result.getStatus(), tenantId, deviceId, deviceVersion.orElse(""));
                        LOG.warn(errorMessage);
                        Tags.HTTP_STATUS.set(span, result.getStatus());
                        deviceVersion.ifPresent(version -> span.setTag("device-registration-version", version));
                        TracingHelper.logError(span,
                                "error updating device with 'AutoProvisioningNotificationSent=true'");
                        return Future.failedFuture(errorMessage);
                    }
                });
    }

    private static Map<String, Object> assembleAutoProvisioningEventProperties(final String tenantId,
            final String gatewayId) {
        final HashMap<String, Object> props = new HashMap<>();

        props.put(MessageHelper.APP_PROPERTY_ORIG_ADDRESS, EventConstants.EVENT_ENDPOINT);
        props.put(MessageHelper.APP_PROPERTY_REGISTRATION_STATUS, EventConstants.RegistrationStatus.NEW.name());
        props.put(MessageHelper.APP_PROPERTY_ORIG_ADAPTER, Constants.PROTOCOL_ADAPTER_TYPE_DEVICE_REGISTRY);
        props.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        Optional.ofNullable(gatewayId)
                .ifPresent(id -> props.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, id));

        return props;
    }
}
