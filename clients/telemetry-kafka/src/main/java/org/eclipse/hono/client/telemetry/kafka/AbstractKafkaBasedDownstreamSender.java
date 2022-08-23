/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerHelper;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.util.DownstreamMessageProperties;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * A client for publishing downstream messages to a Kafka cluster.
 */
public abstract class AbstractKafkaBasedDownstreamSender extends AbstractKafkaBasedMessageSender<Buffer> {

    private final boolean isDefaultsEnabled;

    /**
     * Creates a new Kafka-based downstream sender.
     *
     * @param vertx The vert.x instance to use.
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerName The producer name to use.
     * @param config The Kafka producer configuration properties to use.
     * @param includeDefaults {@code true} if a device's default properties should be included in messages being sent.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public AbstractKafkaBasedDownstreamSender(
            final Vertx vertx,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final String producerName,
            final MessagingKafkaProducerConfigProperties config,
            final boolean includeDefaults,
            final Tracer tracer) {
        super(producerFactory, producerName, config, tracer);
        Objects.requireNonNull(vertx);
        this.isDefaultsEnabled = includeDefaults;

        NotificationEventBusSupport.registerConsumer(vertx, TenantChangeNotification.TYPE,
                notification -> {
                    if (LifecycleChange.DELETE.equals(notification.getChange())) {
                        producerFactory.getProducer(producerName)
                                .ifPresent(producer -> removeTenantTopicBasedProducerMetrics(producer, notification.getTenantId()));
                    }
                });
    }

    private void removeTenantTopicBasedProducerMetrics(final KafkaProducer<String, Buffer> producer, final String tenantId) {
        final HonoTopic topic = new HonoTopic(getTopicType(), tenantId);
        KafkaProducerHelper.removeTopicMetrics(producer, Stream.of(topic.toString()));
    }

    /**
     * Gets the type of topic that this sender uses.
     *
     * @return The topic type.
     */
    protected abstract HonoTopic.Type getTopicType();

    /**
     * Adds default properties defined either at the device or tenant level are added to the message headers.
     *
     * @param endpointName The endpoint that the message is targeted at.
     * @param tenant The tenant that the device belongs to.
     * @param device The registration assertion for the device that the data originates from.
     * @param qos The delivery semantics to use for sending the data.
     * @param contentType The content type of the data. If {@code null}, the content type will be determined
     *            from the following sources (in that order, the first one that is present is used):
     *            <ol>
     *            <li>the <em>contentType</em> parameter</li>
     *            <li>the property with key {@value org.eclipse.hono.util.MessageHelper#SYS_PROPERTY_CONTENT_TYPE} in the
     *            <em>properties</em> parameter</li>
     *            <li>the device default</li>
     *            <li>the tenant default</li>
     *            <li>the default content type ({@value org.eclipse.hono.util.MessageHelper#CONTENT_TYPE_OCTET_STREAM}
     *            if payload is not {@code null}</li>
     *            </ol>
     * @param payload The data to send in the message or {@code null}.
     * @param properties Additional meta data that should be included in the downstream message.
     * @return The augmented properties.
     * @throws NullPointerException if endpoint name, tenant, device or qos are {@code null}.
     */
    protected final Map<String, Object> addDefaults(
            final String endpointName,
            final TenantObject tenant,
            final RegistrationAssertion device,
            final QoS qos,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties) {

        Objects.requireNonNull(endpointName);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(qos);

        final Map<String, Object> messageProperties = Optional.ofNullable(properties)
                .map(HashMap::new)
                .orElseGet(HashMap::new);
        messageProperties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, device.getDeviceId());
        messageProperties.put(MessageHelper.APP_PROPERTY_QOS, qos.ordinal());
        Optional.ofNullable(contentType).ifPresent(ct -> messageProperties.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, ct));

        final var propsWithDefaults = new DownstreamMessageProperties(
                endpointName,
                isDefaultsEnabled ? tenant.getDefaults().getMap() : null,
                isDefaultsEnabled ? device.getDefaults() : null,
                messageProperties,
                tenant.getResourceLimits())
            .asMap();

        // set default content type if none has been set yet (also after applying properties and defaults)
        if (Strings.isNullOrEmpty(propsWithDefaults.get(MessageHelper.SYS_PROPERTY_CONTENT_TYPE))) {
            if (payload != null) {
                propsWithDefaults.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, MessageHelper.CONTENT_TYPE_OCTET_STREAM);
            }
        }

        return propsWithDefaults;
    }
}
