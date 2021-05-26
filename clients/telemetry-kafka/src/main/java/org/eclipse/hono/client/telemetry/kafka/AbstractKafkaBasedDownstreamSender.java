/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.telemetry.kafka;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A client for publishing downstream messages to a Kafka cluster.
 */
public abstract class AbstractKafkaBasedDownstreamSender extends AbstractKafkaBasedMessageSender {

    private final boolean isDefaultsEnabled;

    /**
     * Creates a new Kafka-based downstream sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerName The producer name to use.
     * @param config The Kafka producer configuration properties to use.
     * @param includeDefaults {@code true} if a device's default properties should be included in messages being sent.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public AbstractKafkaBasedDownstreamSender(
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final String producerName,
            final KafkaProducerConfigProperties config,
            final boolean includeDefaults,
            final Tracer tracer) {
        super(producerFactory, producerName, config, tracer);
        this.isDefaultsEnabled = includeDefaults;
    }

    /**
     * Sends a message downstream.
     * <p>
     * Default properties defined either at the device or tenant level are added to the message headers.
     *
     * @param topic The topic to send the message to.
     * @param tenant The tenant that the device belongs to.
     * @param device The registration assertion for the device that the data originates from.
     * @param qos The delivery semantics to use for sending the data.
     * @param contentType The content type of the data. If {@code null}, the content type be taken from the following
     *            sources (in that order, the first one that is present is used):
     *            <ol>
     *            <li>the <em>contentType</em> parameter</li>
     *            <li>the property with key {@link org.eclipse.hono.util.MessageHelper#SYS_PROPERTY_CONTENT_TYPE} in the
     *            <em>properties</em> parameter</li>
     *            <li>the device default</li>
     *            <li>the tenant default</li>
     *            <li>the {@linkplain org.eclipse.hono.util.MessageHelper#CONTENT_TYPE_OCTET_STREAM default content
     *            type}</li>
     *            </ol>
     * @param payload The data to send.
     * @param properties Additional meta data that should be included in the downstream message.
     * @param context The currently active OpenTracing span (may be {@code null}). An implementation should use this as
     *            the parent for any span it creates for tracing the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent downstream.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the data could
     *         not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if topic, tenant, device, or qos are {@code null}.
     */
    protected Future<Void> send(final HonoTopic topic, final TenantObject tenant, final RegistrationAssertion device,
            final QoS qos, final String contentType, final Buffer payload, final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(topic);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(qos);

        final String tenantId = tenant.getTenantId();
        final String deviceId = device.getDeviceId();
        log.trace("sending to Kafka [topic: {}, tenantId: {}, deviceId: {}, qos: {}, contentType: {}, properties: {}]",
                topic, tenantId, deviceId, qos, contentType, properties);
        final Map<String, Object> propsWithDefaults = addDefaults(tenant, device, qos, contentType, properties);

        if (QoS.AT_LEAST_ONCE.equals(qos)) {
            return sendAndWaitForOutcome(topic.toString(), tenantId, deviceId, payload, propsWithDefaults, context);
        } else {
            send(topic.toString(), tenantId, deviceId, payload, propsWithDefaults, context);
            return Future.succeededFuture();
        }
    }

    private Map<String, Object> addDefaults(final TenantObject tenant, final RegistrationAssertion device,
            final QoS qos, final String contentType, final Map<String, Object> properties) {

        final Map<String, Object> headerProperties = new HashMap<>();
        if (isDefaultsEnabled) {
            headerProperties.putAll(tenant.getDefaults().copy().getMap()); // (1) add tenant defaults
            headerProperties.putAll(device.getDefaults()); // (2) overwrite with device defaults
        }

        // (3) overwrite with properties provided by protocol adapter
        Optional.ofNullable(properties).ifPresent(headerProperties::putAll);

        // (4) overwrite by values of separate parameters
        headerProperties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, device.getDeviceId());
        headerProperties.put(MessageHelper.APP_PROPERTY_QOS, qos.ordinal());
        if (contentType != null) {
            headerProperties.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, contentType);
        }

        // (5) if still no content type present, set the default content type
        headerProperties.putIfAbsent(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, MessageHelper.CONTENT_TYPE_OCTET_STREAM);

        if (headerProperties.containsKey(MessageHelper.APP_PROPERTY_DEVICE_TTD)
                || headerProperties.containsKey(MessageHelper.SYS_HEADER_PROPERTY_TTL)) {

            if (!headerProperties.containsKey(MessageHelper.SYS_PROPERTY_CREATION_TIME)) {
                // TODO set this as creation time in the KafkaRecord?

                // must match http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-types-v1.0-os.html#type-timestamp
                // as defined in https://www.eclipse.org/hono/docs/api/telemetry/#forward-telemetry-data
                final long timestamp = Instant.now().toEpochMilli();
                headerProperties.put(MessageHelper.SYS_PROPERTY_CREATION_TIME, timestamp);
            }
        }

        return headerProperties;
    }
}
