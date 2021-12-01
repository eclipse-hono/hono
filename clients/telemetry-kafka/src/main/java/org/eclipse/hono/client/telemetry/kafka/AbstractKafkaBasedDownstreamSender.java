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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.Tracer;
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
            final MessagingKafkaProducerConfigProperties config,
            final boolean includeDefaults,
            final Tracer tracer) {
        super(producerFactory, producerName, config, tracer);
        this.isDefaultsEnabled = includeDefaults;
    }

    /**
     * Adds default properties defined either at the device or tenant level are added to the message headers.
     *
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
     * @param properties Additional meta data that should be included in the downstream message.
     * @return The augmented properties.
     */
    protected final Map<String, Object> addDefaults(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final QoS qos,
            final String contentType,
            final Map<String, Object> properties) {

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

        // make sure that device provided TTL is capped at max TTL (if set)
        headerProperties.compute(MessageHelper.SYS_HEADER_PROPERTY_TTL, (k, v) -> {

            final long maxTtl = Optional.ofNullable(tenant)
                    .flatMap(t -> Optional.ofNullable(t.getResourceLimits()))
                    .map(ResourceLimits::getMaxTtl)
                    .orElse(TenantConstants.UNLIMITED_TTL);

            final long ttlSeconds;
            if (v instanceof Number) { // TTL is configured
                final long ttl = ((Number) v).longValue();
                if (maxTtl != TenantConstants.UNLIMITED_TTL && ttl > maxTtl) {
                    log.debug("limiting TTL [{}s] to max TTL [{}s]", ttl, maxTtl);
                    ttlSeconds = maxTtl;
                } else {
                    log.trace("keeping message TTL [{}s, max TTL: {}s]", ttl, maxTtl);
                    ttlSeconds = ttl;
                }
            } else {
                if (maxTtl != TenantConstants.UNLIMITED_TTL) {
                    log.debug("setting TTL to tenant's max TTL [{}s]", maxTtl);
                    ttlSeconds = maxTtl;
                } else {
                    return null;
                }
            }
            return ttlSeconds * 1000L; // API specification defines TTL in milliseconds
        });

        return headerProperties;
    }
}
