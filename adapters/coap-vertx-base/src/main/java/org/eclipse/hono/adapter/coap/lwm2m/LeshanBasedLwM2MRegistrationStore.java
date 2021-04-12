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


package org.eclipse.hono.adapter.coap.lwm2m;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.californium.core.coap.Request;
import org.eclipse.hono.adapter.client.command.CommandConsumer;
import org.eclipse.hono.adapter.coap.CoapProtocolAdapter;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.leshan.core.node.codec.DefaultLwM2mNodeDecoder;
import org.eclipse.leshan.core.node.codec.DefaultLwM2mNodeEncoder;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.server.californium.observation.ObservationServiceImpl;
import org.eclipse.leshan.server.californium.registration.CaliforniumRegistrationStore;
import org.eclipse.leshan.server.californium.request.CaliforniumLwM2mRequestSender;
import org.eclipse.leshan.server.model.StandardModelProvider;
import org.eclipse.leshan.server.observation.ObservationListener;
import org.eclipse.leshan.server.observation.ObservationService;
import org.eclipse.leshan.server.registration.ExpirationListener;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationUpdate;
import org.eclipse.leshan.server.request.LwM2mRequestSender2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;


/**
 * An Eclipse Leshan based registration store.
 *
 */
public class LeshanBasedLwM2MRegistrationStore implements LwM2MRegistrationStore, Lifecycle, ExpirationListener {

    private static final Logger LOG = LoggerFactory.getLogger(LeshanBasedLwM2MRegistrationStore.class);
    private static final String KEY_ENDPOINT_TYPE = "ep_type";

    private final Map<String, CommandConsumer> commandConsumers = new ConcurrentHashMap<>();
    private final CoapProtocolAdapter adapter;
    private final Tracer tracer;
    private final CaliforniumRegistrationStore registrationStore;

    private LwM2mRequestSender2 lwm2mRequestSender;
    private ObservationService observationService;
    private Map<String, EndpointType> resourcesToObserve = new ConcurrentHashMap<>();

    /**
     * Creates a new store.
     *
     * @param registrationStore The component to use for storing LwM2M registration information.
     * @param adapter The protocol adapter to use for interacting with Hono's service components
     *                and the messaging infrastructure.
     * @param tracer Open Tracing tracer to use for tracking the processing of requests.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public LeshanBasedLwM2MRegistrationStore(
            final CaliforniumRegistrationStore registrationStore,
            final CoapProtocolAdapter adapter,
            final Tracer tracer) {
        this.registrationStore = Objects.requireNonNull(registrationStore);
        this.adapter = Objects.requireNonNull(adapter);
        this.tracer = Objects.requireNonNull(tracer);
        // observe Device and Firmware objects by default
        resourcesToObserve.put("/3/0", EndpointType.TELEMETRY);
        resourcesToObserve.put("/5/0", EndpointType.EVENT);
    }

    @Override
    public Future<Void> start() {
        registrationStore.setExpirationListener(this);
        final var modelProvider = new StandardModelProvider();
        final var nodeDecoder = new DefaultLwM2mNodeDecoder();

        final var observationServiceImpl = new ObservationServiceImpl(
                registrationStore,
                modelProvider,
                nodeDecoder);
        Optional.ofNullable(adapter.getSecureEndpoint()).ifPresent(ep -> {
            ep.addNotificationListener(observationServiceImpl);
            observationServiceImpl.setNonSecureEndpoint(ep);
        });
        Optional.ofNullable(adapter.getInsecureEndpoint()).ifPresent(ep -> {
            ep.addNotificationListener(observationServiceImpl);
            observationServiceImpl.setNonSecureEndpoint(ep);
        });

        observationServiceImpl.addListener(new ObservationListener() {

            @Override
            public void onResponse(
                    final Observation observation,
                    final Registration registration,
                    final ObserveResponse response) {

                adapter.runOnContext(go -> {
                    handleNotification(registration, response);
                });
            }

            @Override
            public void onError(
                    final Observation observation,
                    final Registration registration,
                    final Exception error) {
            }

            @Override
            public void newObservation(final Observation observation, final Registration registration) {
                LOG.debug("established new {} for {}", observation, registration);
            }

            @Override
            public void cancelled(final Observation observation) {
            }
        });

        lwm2mRequestSender = new CaliforniumLwM2mRequestSender(
                adapter.getSecureEndpoint(),
                adapter.getInsecureEndpoint(),
                observationServiceImpl,
                modelProvider,
                new DefaultLwM2mNodeEncoder(),
                nodeDecoder);

        observationService = observationServiceImpl;
        registrationStore.start();

        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stop() {
        if (registrationStore != null) {
            registrationStore.stop();
        }
        return Future.succeededFuture();
    }

    private Future<?> sendLifetimeAsTtdEvent(
            final Device originDevice,
            final Registration registration,
            final SpanContext tracingContext) {

        final int lifetime = registration.getBindingMode() == BindingMode.U
                ? registration.getLifeTimeInSec().intValue()
                : 20;
        return adapter.sendTtdEvent(
                originDevice.getTenantId(),
                originDevice.getDeviceId(),
                null,
                lifetime,
                tracingContext);
    }

    void setResourcesToObserve(final Map<String, EndpointType> mappings) {
        resourcesToObserve.clear();
        if (mappings != null) {
            resourcesToObserve.putAll(mappings);
        }
    }

    private Future<Map<String, EndpointType>> getResourcesToObserve(
            final String tenantId,
            final String deviceId,
            final SpanContext tracingContext) {

        // TODO determine resources to observe from device registration info
        return Future.succeededFuture(resourcesToObserve);
    }


    private void observeConfiguredResources(
            final Registration registration,
            final SpanContext tracingContext) {

        final String tenantId = LwM2MUtils.getTenantId(registration);
        final String deviceId = LwM2MUtils.getDeviceId(registration);

        final var span = tracer.buildSpan("observe configured resources")
                .addReference(References.FOLLOWS_FROM, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
                .withTag(LwM2MUtils.TAG_LWM2M_REGISTRATION_ID, registration.getId())
                .start();

        getResourcesToObserve(tenantId, deviceId, span.context())
            .onSuccess(resourcesToObserve -> {
                resourcesToObserve.entrySet()
                    .forEach(entry -> observeResource(
                            registration,
                            tenantId,
                            deviceId,
                            entry.getKey(),
                            entry.getValue(),
                            tracingContext));
            })
            .onComplete(r -> span.finish());
    }

    private boolean isResourceSupported(final Registration registration, final String resource) {
        return Stream.of(registration.getObjectLinks())
                .anyMatch(link -> resource.startsWith(link.getUrl()));
    }

    private void observeResource(
            final Registration registration,
            final String tenantId,
            final String deviceId,
            final String resource,
            final EndpointType endpoint,
            final SpanContext tracingContext) {

        final var span = tracer.buildSpan("observe resource")
                .addReference(References.CHILD_OF, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
                .withTag(LwM2MUtils.TAG_LWM2M_REGISTRATION_ID, registration.getId())
                .withTag(LwM2MUtils.TAG_LWM2M_RESOURCE, resource)
                .start();

        if (!isResourceSupported(registration, resource)) {
            TracingHelper.logError(span, "device does not support resource");
            span.finish();
            return;
        }

        lwm2mRequestSender.send(
                registration,
                new ObserveRequest(null, resource, Map.of(KEY_ENDPOINT_TYPE, endpoint.getCanonicalName())),
                req -> {
                    ((Request) req).setConfirmable(true);
                },
                30_000L, // we require the device to answer quickly
                response -> {
                    if (response.isFailure()) {
                        TracingHelper.logError(span, "failed to establish observation");
                    } else {
                        adapter.runOnContext(go -> {
                            handleNotification(registration, response);
                        });
                    }
                    span.setTag(Tags.HTTP_STATUS, response.getCode().getCode());
                    span.finish();
                },
                t -> {
                    TracingHelper.logError(span, Map.of(
                            Fields.MESSAGE, "failed to send observe request",
                            Fields.ERROR_KIND, "Exception",
                            Fields.ERROR_OBJECT, t));
                    span.finish();
                });
    }

    /**
     * Forwards a notification received from a device to downstream consumers.
     *
     * @param registration The device's LwM2M registration information.
     * @param response The response message that contains the notification.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    void handleNotification(
            final Registration registration,
            final ObserveResponse response) {

        Objects.requireNonNull(registration);
        Objects.requireNonNull(response);

        final var observation = response.getObservation();
        LOG.debug("handling notification for {}", observation);
        final Device authenticatedDevice = LwM2MUtils.getDevice(registration);
        final EndpointType endpoint = Optional.ofNullable(observation.getContext().get(KEY_ENDPOINT_TYPE))
                .map(EndpointType::fromString)
                .orElse(EndpointType.TELEMETRY);

        final Span currentSpan = tracer.buildSpan("process notification")
                .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER)
                .withTag(Tags.COMPONENT, adapter.getTypeName())
                .withTag(TracingHelper.TAG_TENANT_ID, authenticatedDevice.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, authenticatedDevice.getDeviceId())
                .withTag(LwM2MUtils.TAG_LWM2M_REGISTRATION_ID, registration.getId())
                .withTag(LwM2MUtils.TAG_LWM2M_RESOURCE, observation.getPath().toString())
                .start();

        final var ctx = NotificationContext.fromResponse(
                response,
                authenticatedDevice,
                adapter.getMetrics().startTimer(),
                currentSpan);

        final Map<String, Object> props = ctx.getDownstreamMessageProperties();
        props.put(MessageHelper.APP_PROPERTY_ORIG_ADAPTER, adapter.getTypeName());

        final Future<TenantObject> tenantTracker = adapter.getTenantClient().get(ctx.getTenantId(), currentSpan.context())
                .compose(tenantObject -> CompositeFuture.all(
                        adapter.isAdapterEnabled(tenantObject),
                        adapter.checkMessageLimit(tenantObject, ctx.getPayload().length(), currentSpan.context()))
                    .map(tenantObject));
        final Future<RegistrationAssertion> tokenTracker = adapter.getRegistrationAssertion(
                ctx.getTenantId(),
                ctx.getAuthenticatedDevice().getDeviceId(),
                null,
                currentSpan.context());

        CompositeFuture.join(tenantTracker, tokenTracker)
            .compose(ok -> {
                if (EndpointType.EVENT == endpoint) {
                    LOG.debug("forwarding observe response as event");
                    return adapter.getEventSender(tenantTracker.result()).sendEvent(
                            tenantTracker.result(),
                            tokenTracker.result(),
                            ctx.getContentType(),
                            ctx.getPayload(),
                            props,
                            currentSpan.context());
                } else {
                    LOG.debug("forwarding observe response as telemetry message");
                    return adapter.getTelemetrySender(tenantTracker.result()).sendTelemetry(
                            tenantTracker.result(),
                            tokenTracker.result(),
                            ctx.getRequestedQos(),
                            ctx.getContentType(),
                            ctx.getPayload(),
                            props,
                            currentSpan.context());
                }
            })
            .onComplete(r -> {
                final ProcessingOutcome outcome;
                if (r.succeeded()) {
                    outcome = ProcessingOutcome.FORWARDED;
                } else {
                    outcome = ClientErrorException.class.isInstance(r.cause()) ?
                            ProcessingOutcome.UNPROCESSABLE : ProcessingOutcome.UNDELIVERABLE;
                    TracingHelper.logError(currentSpan, r.cause());
                }
                adapter.getMetrics().reportTelemetry(
                        endpoint,
                        authenticatedDevice.getTenantId(),
                        tenantTracker.result(),
                        outcome,
                        EndpointType.EVENT == endpoint ? QoS.AT_LEAST_ONCE : QoS.AT_MOST_ONCE,
                        ctx.getPayload().length(),
                        TtdStatus.NONE,
                        ctx.getTimer());
                currentSpan.finish();
            });
    }

    @Override
    public void registrationExpired(final Registration registration, final Collection<Observation> observations) {
        Optional.ofNullable(commandConsumers.remove(registration.getId()))
            .ifPresent(consumer -> {
                adapter.runOnContext(go -> {
                    final Device device = LwM2MUtils.getDevice(registration);
                    consumer.close(null)
                        .onComplete(r -> observations.forEach(obs -> {
                            LOG.debug("canceling {} of {}", obs, device);
                            observationService.cancelObservation(obs);
                        }));
                });
            });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> addRegistration(final Registration registration, final SpanContext tracingContext) {

        final var device = LwM2MUtils.getDevice(registration);
        final var existingRegistration = registrationStore.addRegistration(registration);

        if (existingRegistration == null) {
            LOG.debug("added {} for {}", registration, device);
        } else {
            LOG.debug("replaced existing {} for {} with new {}", existingRegistration, device, registration);
        }

        return adapter.getCommandConsumerFactory()
            .createCommandConsumer(
                device.getTenantId(),
                device.getDeviceId(),
                commandContext -> {
                    Tags.COMPONENT.set(commandContext.getTracingSpan(), adapter.getTypeName());
                 // TODO add logic for sending commands to device
                },
                null,
                tracingContext)
            .compose(commandConsumer -> {
                commandConsumers.put(registration.getId(), commandConsumer);
                return sendLifetimeAsTtdEvent(device, registration, tracingContext);
            })
            .onSuccess(ok -> observeConfiguredResources(registration, tracingContext))
            .mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> updateRegistration(final RegistrationUpdate registrationUpdate, final SpanContext tracingContext) {

        final var updatedRegistration = registrationStore.updateRegistration(registrationUpdate);
        final var device = LwM2MUtils.getDevice(updatedRegistration.getPreviousRegistration());

        return sendLifetimeAsTtdEvent(device, updatedRegistration.getUpdatedRegistration(), tracingContext)
                .mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> removeRegistration(final String registrationId, final SpanContext tracingContext) {

        // this also removes all observations associated with the registration
        final var deregistration = registrationStore.removeRegistration(registrationId);
        final var device = LwM2MUtils.getDevice(deregistration.getRegistration());

        registrationExpired(deregistration.getRegistration(), deregistration.getObservations());
        return adapter.sendTtdEvent(
                device.getTenantId(),
                device.getDeviceId(),
                null,
                0,
                tracingContext)
                .mapEmpty();
    }
}
