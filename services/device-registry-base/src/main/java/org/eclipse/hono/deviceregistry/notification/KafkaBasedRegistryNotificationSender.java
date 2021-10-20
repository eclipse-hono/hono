/*
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

package org.eclipse.hono.deviceregistry.notification;

import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.notification.AbstractKafkaBasedNotificationSender;
import org.eclipse.hono.notification.deviceregistry.AbstractDeviceRegistryNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;

import io.vertx.core.json.JsonObject;

/**
 * A client for publishing notifications from Hono's device registry with Kafka.
 */
public class KafkaBasedRegistryNotificationSender
        extends AbstractKafkaBasedNotificationSender<AbstractDeviceRegistryNotification> {

    /**
     * Creates an instance.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param kafkaProducerConfig The Kafka producer configuration properties to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedRegistryNotificationSender(final KafkaProducerFactory<String, JsonObject> producerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig) {
        super(producerFactory, kafkaProducerConfig);
    }

    @Override
    protected String getKey(final AbstractDeviceRegistryNotification notification) {
        if (notification instanceof TenantChangeNotification) {
            return ((TenantChangeNotification) notification).getTenantId();
        } else if (notification instanceof DeviceChangeNotification) {
            return ((DeviceChangeNotification) notification).getDeviceId();
        } else if (notification instanceof CredentialsChangeNotification) {
            return ((CredentialsChangeNotification) notification).getDeviceId();
        } else {
            throw new IllegalArgumentException("unknown notification type");
        }
    }

    @Override
    protected String getTopic(final AbstractDeviceRegistryNotification notification) {
        return new HonoTopic(HonoTopic.Type.NOTIFICATION, notification.getAddress()).toString();
    }
}
