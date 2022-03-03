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
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Helper to auto-provision edge devices connected via gateways.
 */
public class EdgeDeviceAutoProvisioner extends AbstractAutoProvisioningEventSender {

    private final AutoProvisionerConfigProperties config;
    private final Tracer tracer;

    /**
     * Creates an instance of {@link EdgeDeviceAutoProvisioner} to auto provision edge devices.
     *
     * @param vertx The vert.x instance.
     * @param deviceManagementService The device management service instance.
     * @param eventSenderProvider The provider for the messaging client to send auto-provisioned events.
     * @param config The auto-provisioning configuration.
     * @param tracer The OpenTracing tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public EdgeDeviceAutoProvisioner(final Vertx vertx,
            final DeviceManagementService deviceManagementService,
            final MessagingClientProvider<EventSender> eventSenderProvider,
            final AutoProvisionerConfigProperties config,
            final Tracer tracer) {
        super(vertx, deviceManagementService, eventSenderProvider);
        this.config = Objects.requireNonNull(config);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * Auto-provisions the edge device using the given device id and the given registration data.
     *
     * @param tenantId The id of the tenant for which the edge device should be provisioned.
     * @param tenant The tenant information.
     * @param deviceId The id of the edge device which should be provisioned, may be {@code null}.
     * @param gatewayId The id of the edge device's gateway.
     * @param device The registration data for the device to be auto-provisioned.
     * @param spanContext The tracing context to be used by this operation.
     *
     * @return A future indicating the outcome of the operation.
     *
     * @throws NullPointerException if any argument except deviceId is {@code null}.
     */
    public Future<Device> performAutoProvisioning(final String tenantId, final Tenant tenant, final String deviceId,
            final String gatewayId, final Device device, final SpanContext spanContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(device);
        Objects.requireNonNull(spanContext);

        final Span span = TracingHelper
                .buildChildSpan(tracer, spanContext, "auto-provision edge device connected via gateway", 
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
                                    return sendDelayedAutoProvisioningNotificationIfNeeded(tenantId, tenant, deviceId,
                                            gatewayId, readDevice, span).map(readDevice);
                                });
                    }

                    span.log("device created");
                    LOG.trace("device [{}] for gateway [{}] successfully created by auto-provisioning [tenant-id: {}]",
                            deviceId, gatewayId, tenantId);
                    return sendAutoProvisioningEvent(tenantId, tenant, deviceId, gatewayId, span)
                            .compose(sendEmptyEventOk -> deviceManagementService.readDevice(tenantId, deviceId, span)
                                    .compose(readDeviceResult -> {
                                        if (!readDeviceResult.isOk()) {
                                            LOG.warn("notification flag of device [{}] for gateway [{}] of tenant [tenant-id: {}] could not be updated",
                                                    deviceId, gatewayId, tenantId);
                                            return Future.failedFuture(
                                                    StatusCodeMapper.from(readDeviceResult.getStatus(),
                                                            String.format(
                                                                    "update of notification flag failed (status %d)",
                                                                    readDeviceResult.getStatus())));
                                        }

                                        final Device deviceData = readDeviceResult.getPayload();
                                        return updateAutoProvisioningNotificationSent(tenantId, deviceId, deviceData,
                                                readDeviceResult.getResourceVersion(), span)
                                                //auto-provisioning still succeeds even if the device registration cannot be updated with the notification flag
                                                .recover(error -> Future.succeededFuture())
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
     * @param tenant The tenant information.
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
    public Future<Void> sendDelayedAutoProvisioningNotificationIfNeeded(final String tenantId, final Tenant tenant,
            final String deviceId, final String gatewayId, final Device device, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenant);
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
                            return sendAutoProvisioningEvent(tenantId, tenant, deviceId, gatewayId, span)
                                    .compose(ok -> updateAutoProvisioningNotificationSent(tenantId, deviceId,
                                            readDevice, readDeviceResult.getResourceVersion(), span)
                                                    // auto-provisioning still succeeds even if the device registration
                                                    // cannot be updated with the notification flag
                                                    .recover(error -> Future.succeededFuture()));
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

}
