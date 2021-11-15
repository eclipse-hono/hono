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

package org.eclipse.hono.client.notification;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;

import io.vertx.kafka.client.serialization.JsonObjectDeserializer;

/**
 * Configuration properties for Kafka consumers used for Hono's notifications.
 *
 * Record keys will be deserialized with {@link StringDeserializer}, the values with {@link JsonObjectDeserializer}.
 */
public class NotificationKafkaConsumerConfigProperties extends KafkaConsumerConfigProperties {

    /**
     * Creates an instance.
     */
    public NotificationKafkaConsumerConfigProperties() {
        super(StringDeserializer.class, JsonObjectDeserializer.class);
    }

}
