/**
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
 */
package org.eclipse.hono.deviceregistry.service.device;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
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
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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

    private Tracer tracer = NoopTracerFactory.create();

    private DeviceManagementService deviceManagementService;

    private TenantInformationService tenantInformationService = new NoopTenantInformationService();

    private ProtonBasedDownstreamSender protonBasedDownstreamSender;

    private Future<HonoConnection> connectionAttempt;

    private Vertx vertx;

    private AutoProvisionerConfigProperties config;

    @Override
    public final Future<Void> start() {
        // Required since every endpoint may call the start() method of its referenced service leading to multiple calls
        // to start(). This results in an exception in the factory's connect() method.
        synchronized (this) {
            if (connectionAttempt == null) {
                connectionAttempt = protonBasedDownstreamSender.connect().map(connection -> {
                    LOG.info("connected to AMQP network");
                    if (vertx == null && connection != null) {
                        vertx = connection.getVertx();
                    }
                    return connection;
                }).recover(t -> {
                    LOG.warn("failed to connect to AMQP network", t);
                    return Future.failedFuture(t);
                });
            }
        }

        return connectionAttempt.mapEmpty();
    }

    @Override
    public final Future<Void> stop() {
        final Promise<Void> result = Promise.promise();
        protonBasedDownstreamSender.disconnect(result);
        return result.future();
    }

    /**
     * Sets the vert.x instance.
     *
     * @param vertx The vert.x instance.
     * @throws NullPointerException if vert.x is {@code null}.
     */
    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Sets the factory to use for creating a client for the AMQP Messaging Network.
     *
     * @param factory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Autowired
    public final void setProtonBasedDownstreamSender(final ProtonBasedDownstreamSender factory) {
        this.protonBasedDownstreamSender = Objects.requireNonNull(factory);
    }

    /**
     * Sets the {@link DeviceManagementService} to use.
     *
     * @param deviceManagementService The service to set.
     *
     * @throws NullPointerException if the service is {@code null}.
     */
    @Autowired
    public void setDeviceManagementService(final DeviceManagementService deviceManagementService) {
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
    @Autowired(required = false)
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
    @Autowired(required = false)
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
    @Autowired
    public void setConfig(final AutoProvisionerConfigProperties config) {
        this.config = Objects.requireNonNull(config);
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

            final Message msg = MessageHelper.newMessage(
                    ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, tenantId, deviceId),
                    EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION,
                    null,
                    tenantTracker.result().getPayload(),
                    props,
                    null,
                    false,
                    false);

            return protonBasedDownstreamSender.sendEvent(tenantTracker.result().getPayload(),
                        new RegistrationAssertion(deviceId),
                        EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION,
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
        final DeviceKey deviceKey = DeviceKey.from(TenantKey.from(tenantId), deviceId);
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
