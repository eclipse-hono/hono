/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.telemetry.pubsub;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.pubsub.AbstractPubSubBasedMessageSender;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.DownstreamMessageProperties;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * A Pub/Sub based sender for publishing telemetry messages and events to Google Pub/Sub.
 */
public final class PubSubBasedDownstreamSender extends AbstractPubSubBasedMessageSender
        implements TelemetrySender, EventSender {

    /**
     * The name of Pub/Sub downstream message property containing the ID of the tenant the device belongs to.
     */
    private static final String PUBSUB_DOWNSTREAM_PROPERTY_TENANT_ID = "tenantId";

    /**
     * The name of the Pub/Sub downstream message property containing the ID of the device.
     */
    private static final String PUBSUB_DOWNSTREAM_PROPERTY_DEVICE_ID = "deviceId";

    /**
     * The name of the Pub/Sub downstream message property containing the ID of the device registry the device belongs to.
     */
    private static final String PUBSUB_DOWNSTREAM_PROPERTY_DEVICE_REGISTRY_ID = "deviceRegistryId";

    private static final String PUBSUB_PROPERTY_SUBFOLDER = "subFolder";

    private final boolean isDefaultsEnabled;

    /**
     * Creates a new Pub/Sub-based downstream sender.
     *
     * @param vertx The vert.x instance to use.
     * @param publisherFactory The factory to use for creating Pub/Sub publishers.
     * @param topic The topic to create the publisher for.
     * @param projectId The Google project id to use.
     * @param includeDefaults {@code true} if a device's default properties should be included in messages being sent.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public PubSubBasedDownstreamSender(
            final Vertx vertx,
            final PubSubPublisherFactory publisherFactory,
            final String topic,
            final String projectId,
            final boolean includeDefaults,
            final Tracer tracer) {
        super(publisherFactory, topic, projectId, tracer);
        Objects.requireNonNull(vertx);
        this.isDefaultsEnabled = includeDefaults;

        NotificationEventBusSupport.registerConsumer(vertx, TenantChangeNotification.TYPE,
                notification -> {
                    if (LifecycleChange.DELETE.equals(notification.getChange())) {
                        publisherFactory.getPublisher(topic, notification.getTenantId())
                                .ifPresent(publisher -> publisherFactory.closePublisher(topic,
                                        notification.getTenantId()));
                    }
                });
    }

    @Override
    public Future<Void> sendEvent(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final SpanContext context) {
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);

        if (log.isTraceEnabled()) {
            log.trace("sending event data [tenantId: {}, deviceId: {}, contentType: {}, properties: {}]",
                    tenant.getTenantId(), device.getDeviceId(), contentType, properties);
        }

        final String tenantId = tenant.getTenantId();
        final String deviceId = device.getDeviceId();

        final String origAddress = Optional.ofNullable(properties.get(MessageHelper.APP_PROPERTY_ORIG_ADDRESS))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse("");

        final List<String> subtopics = PubSubMessageHelper.getSubtopics(origAddress);
        final String subFolder = PubSubMessageHelper.getSubFolder(subtopics);
        final Map<String, Object> propsWithDefaults = addDefaults(
                EventConstants.EVENT_ENDPOINT,
                tenant,
                device,
                QoS.AT_LEAST_ONCE,
                contentType,
                properties,
                subFolder);

        final Span currentSpan = startSpan("forward event", tenantId, deviceId, References.CHILD_OF,
                context);

        final String topic = PubSubMessageHelper.getTopicName(EventConstants.EVENT_ENDPOINT, tenantId, subtopics);
        return sendAndWaitForOutcome(topic, tenantId, deviceId, payload, propsWithDefaults,
                currentSpan).onComplete(
                        ar -> currentSpan.finish());
    }

    @Override
    public Future<Void> sendTelemetry(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final QoS qos,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(qos);

        if (log.isTraceEnabled()) {
            log.trace("sending telemetry data [tenantId: {}, deviceId: {}, qos: {}, contentType: {}, properties: {}]",
                    tenant.getTenantId(), device.getDeviceId(), qos, contentType, properties);
        }

        final String tenantId = tenant.getTenantId();
        final String deviceId = device.getDeviceId();

        final String origAddress = Optional.ofNullable(properties.get(MessageHelper.APP_PROPERTY_ORIG_ADDRESS))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse("");

        final List<String> subtopics = PubSubMessageHelper.getSubtopics(origAddress);
        final String subFolder = PubSubMessageHelper.getSubFolder(subtopics);
        final Map<String, Object> propsWithDefaults = addDefaults(
                TelemetryConstants.TELEMETRY_ENDPOINT,
                tenant,
                device,
                qos,
                contentType,
                properties,
                subFolder);

        final Span currentSpan = startSpan(
                "forward telemetry",
                tenantId,
                deviceId,
                qos == QoS.AT_MOST_ONCE ? References.FOLLOWS_FROM : References.CHILD_OF,
                context);

        final String topic = PubSubMessageHelper.getTopicName(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId, subtopics);
        final var outcome = sendAndWaitForOutcome(topic, tenantId, deviceId, payload, propsWithDefaults,
                currentSpan).onComplete(
                        ar -> currentSpan.finish());

        if (qos == QoS.AT_MOST_ONCE) {
            return Future.succeededFuture();
        } else {
            return outcome;
        }
    }

    private Map<String, Object> addDefaults(
            final String topicEndpoint,
            final TenantObject tenant,
            final RegistrationAssertion device,
            final QoS qos,
            final String contentType,
            final Map<String, Object> properties,
            final String subFolder) {

        Objects.requireNonNull(topicEndpoint);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(qos);

        final Map<String, Object> messageProperties = Optional.ofNullable(properties)
                .map(HashMap::new)
                .orElseGet(HashMap::new);

        messageProperties.put(PUBSUB_DOWNSTREAM_PROPERTY_DEVICE_ID, device.getDeviceId());
        messageProperties.put(MessageHelper.APP_PROPERTY_QOS, qos.ordinal());
        messageProperties.put(PUBSUB_DOWNSTREAM_PROPERTY_TENANT_ID, tenant.getTenantId());
        messageProperties.put(PUBSUB_DOWNSTREAM_PROPERTY_DEVICE_REGISTRY_ID, tenant.getTenantId());
        messageProperties.put(PubSubMessageHelper.PUBSUB_PROPERTY_PROJECT_ID, projectId);
        messageProperties.put(PUBSUB_PROPERTY_SUBFOLDER, subFolder);

        Optional.ofNullable(contentType)
                .ifPresent(ct -> messageProperties.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, ct));

        return new DownstreamMessageProperties(
                topicEndpoint,
                isDefaultsEnabled ? tenant.getDefaults().getMap() : null,
                isDefaultsEnabled ? device.getDefaults() : null,
                messageProperties,
                tenant.getResourceLimits()).asMap();
    }

}
