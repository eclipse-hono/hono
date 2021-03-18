/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.util.MessagingClient;
import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.DeviceStatus;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Implements gateway based auto-provisioning.
 */
public class AutoProvisioner implements Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(AutoProvisioner.class);

    private final AtomicBoolean started = new AtomicBoolean(false);

    private Tracer tracer = NoopTracerFactory.create();
    private TenantInformationService tenantInformationService = new NoopTenantInformationService();
    private DeviceManagementService deviceManagementService;
    private MessagingClient<EventSender> eventClients;
    private Vertx vertx;

    private AutoProvisionerConfigProperties config;

    /**
     * Sets the vert.x instance.
     *
     * @param vertx The vert.x instance.
     * @throws NullPointerException if vert.x is {@code null}.
     */
    public final void setVertx(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Sets the clients to use for sending events.
     *
     * @param clients The clients.
     * @throws NullPointerException if clients is {@code null}.
     */
    public void setEventSenders(final MessagingClient<EventSender> clients) {
        this.eventClients = Objects.requireNonNull(clients);
    }

    /**
     * Sets the {@link DeviceManagementService} to use.
     *
     * @param deviceManagementService The service to set.
     *
     * @throws NullPointerException if the service is {@code null}.
     */
    public final void setDeviceManagementService(final DeviceManagementService deviceManagementService) {
        this.deviceManagementService = Objects.requireNonNull(deviceManagementService);
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
        this.tenantInformationService = Objects.requireNonNull(tenantInformationService);
        LOG.info("using {}", tenantInformationService);
    }

    /**
     * Sets the OpenTracing {@code Tracer} to use for tracking the processing
     * of messages published by devices across Hono's components.
     * <p>
     * If not set explicitly, the {@code NoopTracer} from OpenTracing will
     * be used.
     *
     * @param opentracingTracer The tracer.
     * @throws NullPointerException if the opentracingTracer is {@code null}.
     */
    public final void setTracer(final Tracer opentracingTracer) {
        this.tracer = Objects.requireNonNull(opentracingTracer);
        LOG.info("using OpenTracing Tracer implementation [{}]", opentracingTracer.getClass().getName());
    }

    /**
     * Sets the configuration to use for auto-provisioning.
     *
     * @param config The configuration to set.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setConfig(final AutoProvisionerConfigProperties config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * {@inheritDoc}
     *
     * @return The future returned by the configured {@linkplain EventSender#start() event sender's start() method}.
     * @throws IllegalStateException if any of the vertx, eventSender or deviceManagementService properties are not set.
     */
    @Override
    public final Future<Void> start() {
        if (vertx == null) {
            throw new IllegalStateException("vert.x instance must be set");
        }
        if (eventClients == null || !eventClients.containsImplementations()) {
             throw new IllegalStateException("Event client must be set");
        }
        if (deviceManagementService == null) {
            throw new IllegalStateException("device management service is not set");
        }
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

    private Future<Void> sendAutoProvisioningEvent(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(span);

        LOG.debug("sending auto-provisioning event for device [{}] created via gateway [{}] [tenant-id: {}]", deviceId, gatewayId, tenantId);

        final Future<TenantResult<TenantObject>> tenantTracker = tenantInformationService.getTenant(tenantId, span);

        return tenantTracker.compose(ok -> {
            if (tenantTracker.result().isError()) {
                return Future.failedFuture(StatusCodeMapper.from(tenantTracker.result()));
            }

            final Map<String, Object> props = new HashMap<>();
            props.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
            props.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);
            props.put(MessageHelper.APP_PROPERTY_REGISTRATION_STATUS, EventConstants.RegistrationStatus.NEW.name());
            props.put(MessageHelper.APP_PROPERTY_ORIG_ADAPTER, Constants.PROTOCOL_ADAPTER_TYPE_DEVICE_REGISTRY);
            props.put(MessageHelper.APP_PROPERTY_ORIG_ADDRESS, EventConstants.EVENT_ENDPOINT);

            final EventSender eventSender = eventClients.getClient(tenantTracker.result().getPayload());

            return eventSender.sendEvent(
                    tenantTracker.result().getPayload(),
                    new RegistrationAssertion(deviceId),
                    EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION,
                    null,
                    props,
                    span.context())
                .onFailure(t -> LOG.info("error sending auto-provisioning event for device [{}] created via gateway [{}] [tenant-id: {}]",
                        deviceId, gatewayId, tenantId));
        });
    }

    /**
     * Auto-provisions the edge device using the given device id and the given registration data.
     *
     * @param tenantId The id of the tenant for which the edge device should be provisioned.
     * @param deviceId The id of the edge device which should be provisioned, may be {@code null}.
     * @param gatewayId The id of the edge device's gateway.
     * @param device The registration data for the device to be auto-provisioned.
     * @param spanContext The tracing context to be used by this operation.
     *
     * @return A future indicating the outcome of the operation.
     *
     * @throws NullPointerException if any argument except deviceId is {@code null}.
     */
    public Future<Device> performAutoProvisioning(final String tenantId, final String deviceId,
            final String gatewayId, final Device device, final SpanContext spanContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(device);
        Objects.requireNonNull(spanContext);

        final Span span = TracingHelper
                .buildChildSpan(tracer, spanContext, "auto-provision device for gateway", 
                        Constants.PROTOCOL_ADAPTER_TYPE_DEVICE_REGISTRY)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_GATEWAY_ID, gatewayId)
                .start();
        TracingHelper.setDeviceTags(span, tenantId, deviceId);

        return deviceManagementService.createDevice(tenantId, Optional.of(deviceId), device, span)
                // make sure an HTTP_CONFLICT result is handled as an OperationResult
                .recover(thr -> ServiceInvocationException.extractStatusCode(thr) == HttpURLConnection.HTTP_CONFLICT
                        ? Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CONFLICT))
                        : Future.failedFuture(thr))
                .compose(addEdgeDeviceResult -> {
                    if (addEdgeDeviceResult.isError()) {
                        if (addEdgeDeviceResult.getStatus() != HttpURLConnection.HTTP_CONFLICT) {
                            return Future.failedFuture(StatusCodeMapper.from(addEdgeDeviceResult.getStatus(),
                                    String.format("failed to add edge device (status %d)",
                                            addEdgeDeviceResult.getStatus())));
                        }

                        // handle HTTP_CONFLICT, meaning the device already exists
                        span.log("device already exists");
                        LOG.debug("device [{}] for gateway [{}] already created by concurrent auto-provisioning [tenant-id: {}]",
                                deviceId, gatewayId, tenantId);
                        return deviceManagementService.readDevice(tenantId, deviceId, span)
                                .compose(readDeviceResult -> {
                                    if (!readDeviceResult.isOk()) {
                                        span.log("reading device after conflict failed");
                                        LOG.warn("reading device after conflict failed for device [{}] of gateway [{}] of tenant [{}]: status: {}",
                                                deviceId, gatewayId, tenantId, readDeviceResult.getStatus());
                                        return Future.failedFuture(StatusCodeMapper.from(readDeviceResult.getStatus(),
                                                String.format("reading device after conflict failed (status %d)",
                                                        readDeviceResult.getStatus())));
                                    }

                                    if (!readDeviceResult.getPayload().getVia().contains(gatewayId)) {
                                        span.log("attempted to auto-provision same device via two different gateways at the same time");
                                        LOG.info("attempted to auto-provision device [{}] via gateway [{}] of tenant [{}] but the registration data's via contains only {}",
                                                deviceId, gatewayId, tenantId, readDeviceResult.getPayload().getVia());
                                        return Future.failedFuture(StatusCodeMapper.from(HttpURLConnection.HTTP_FORBIDDEN,
                                                "device already auto-provisioned for another gateway"));
                                    }

                                    final Device readDevice = readDeviceResult.getPayload();
                                    // ensure that a notification event gets sent (even if we might send duplicate events)
                                    return sendDelayedAutoProvisioningNotificationIfNeeded(tenantId, deviceId, gatewayId, readDevice, span).map(readDevice);
                                });
                    }

                    span.log("device created");
                    LOG.trace("device [{}] for gateway [{}] successfully created by auto-provisioning [tenant-id: {}]",
                            deviceId, gatewayId, tenantId);
                    return sendAutoProvisioningEvent(tenantId, deviceId, gatewayId, span)
                            .compose(sendEmptyEventOk -> deviceManagementService.readDevice(tenantId, deviceId, span)
                                    .compose(readDeviceResult -> {
                                        if (!readDeviceResult.isOk()) {
                                            span.log("update of notification flag failed");
                                            LOG.warn("notification flag of device [{}] for gateway [{}] of tenant [tenant-id: {}] could not be updated",
                                                    deviceId, gatewayId, tenantId);
                                            return Future.failedFuture(
                                                    StatusCodeMapper.from(readDeviceResult.getStatus(),
                                                            String.format(
                                                                    "update of notification flag failed (status %d)",
                                                                    readDeviceResult.getStatus())));
                                        }

                                        final Device deviceData = readDeviceResult.getPayload();
                                        return setAutoProvisioningNotificationSent(tenantId, deviceId, deviceData, span)
                                                .map(deviceData);
                                    }));
                })
                .onFailure(thr -> TracingHelper.logError(span, thr))
                .onComplete(ar -> span.finish());
    }

    /**
     * Notify northbound applications of an auto-provisioned device.
     *
     * @param tenantId The id of the tenant for which the edge device should be provisioned.
     * @param deviceId The id of the edge device which should be provisioned.
     * @param gatewayId The id of the edge device's gateway.
     * @param device The data of the edge device.
     * @param span The span to be used for tracing this operation.
     *
     * @return A future indicating the outcome of the operation.
     *
     * @throws NullPointerException if any argument except deviceId is {@code null}.
     *
     * @see AutoProvisionerConfigProperties#DEFAULT_RETRY_EVENT_SENDING_DELAY
     */
    public Future<Void> sendDelayedAutoProvisioningNotificationIfNeeded(final String tenantId,
            final String deviceId, final String gatewayId, final Device device, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(device);
        Objects.requireNonNull(span);

        if (!wasDeviceAutoProvisioned(device) || wasAutoProvisioningNotificationSent(device)) {
            // nothing to do
            return Future.succeededFuture();
        }
        // sending of the notification event hasn't (successfully) completed yet;
        // in order not to send a duplicate event while the other event is still getting sent, wait a bit here first,
        // then check the AutoProvisioningNotificationSent flag again
        LOG.info("device was auto-provisioned but notificationSent flag isn't set, wait {}ms, re-check flag and send event if needed [tenant-id: {}, device-id: {}]",
                config.getRetryEventSendingDelay(), tenantId, deviceId);
        span.log(String.format("notification event not sent yet, wait %dms and check again", config.getRetryEventSendingDelay()));
        final Promise<Void> resultPromise = Promise.promise();
        vertx.setTimer(config.getRetryEventSendingDelay(), tid -> {
            deviceManagementService.readDevice(tenantId, deviceId, span)
                    .compose(readDeviceResult -> {
                        if (!readDeviceResult.isOk()) {
                            span.log("sending of delayed notification failed");
                            LOG.warn("sending of delayed for device [{}] of gateway [{}] of tenant [tenant-id: {}] failed: status: {}",
                                    deviceId, gatewayId, tenantId, readDeviceResult.getStatus());
                            return Future.failedFuture(StatusCodeMapper.from(readDeviceResult.getStatus(),
                                    String.format("sending of delayed notification failed (status %d)",
                                            readDeviceResult.getStatus())));
                        }

                        final Device readDevice = readDeviceResult.getPayload();
                        if (!wasAutoProvisioningNotificationSent(readDevice)) {
                            LOG.debug("sending auto-provisioning event - notificationSent flag wasn't updated in between [tenant-id: {}, device-id: {}]",
                                    tenantId, deviceId);
                            span.log("sending event - notificationSent flag wasn't updated in between");
                            return sendAutoProvisioningEvent(tenantId, deviceId, gatewayId, span)
                                    .compose(ok -> setAutoProvisioningNotificationSent(tenantId, deviceId, readDevice, span))
                                    .mapEmpty();
                        } else {
                            LOG.debug("no need to send auto-provisioning event, notificationSent flag was set in between [tenant-id: {}, device-id: {}]",
                                    tenantId, deviceId);
                            span.log("no need to send event, notificationSent flag was set in between");
                            return Future.succeededFuture((Void) null);
                        }
                    }).onComplete(resultPromise);
        });
        return resultPromise.future();
    }

    private boolean wasDeviceAutoProvisioned(final Device registrationData) {
        return registrationData.getStatus() != null ? registrationData.getStatus().isAutoProvisioned() : false;
    }

    private boolean wasAutoProvisioningNotificationSent(final Device registrationData) {
        return registrationData.getStatus() != null ? registrationData.getStatus().isAutoProvisioningNotificationSent() : false;
    }

    private Future<OperationResult<Id>> setAutoProvisioningNotificationSent(final String tenantId,
            final String deviceId, final Device device, final Span span) {
        device.setStatus(new DeviceStatus().setAutoProvisioningNotificationSent(true));
        return deviceManagementService.updateDevice(tenantId, deviceId, device, Optional.empty(), span)
                .map(opResult -> {
                    if (opResult.isError()) {
                        LOG.debug("Error updating device with 'AutoProvisioningNotificationSent=true'; status: {} [tenant-id: {}, device-id: {}]",
                                opResult.getStatus(), tenantId, deviceId);
                        TracingHelper.logError(span, String.format("Error updating device with 'AutoProvisioningNotificationSent=true' (status: %d)",
                                opResult.getStatus()));
                    }
                    return opResult;
                });
    }
}
