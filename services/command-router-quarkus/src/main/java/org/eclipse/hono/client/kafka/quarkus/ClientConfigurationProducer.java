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


package org.eclipse.hono.client.kafka.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A producer of application scoped Kafka client configuration.
 *
 */
@ApplicationScoped
public class ClientConfigurationProducer {

    @ConfigProperty(name = "hono.command.kafka.commonClientConfigPath", defaultValue = "/opt/hono/config/kafka-common.properties")
    String commonClientConfigPath;

    @ConfigProperty(name = "hono.command.kafka.consumerConfigPath", defaultValue = "/opt/hono/config/kafka-consumer.properties")
    String consumerConfigPath;

    @ConfigProperty(name = "hono.command.kafka.producerConfigPath", defaultValue = "/opt/hono/config/kafka-producer.properties")
    String producerConfigPath;

    @Produces
    KafkaConsumerConfigProperties consumerProperties() {
        final var props = new KafkaConsumerConfigProperties();
        props.setCommonClientConfigPath(commonClientConfigPath);
        props.setConsumerConfigPath(consumerConfigPath);
        return props;
    }

    @Produces
    KafkaProducerConfigProperties producerProperties() {
        final var props = new KafkaProducerConfigProperties();
        props.setCommonClientConfigPath(commonClientConfigPath);
        props.setProducerConfigPath(consumerConfigPath);
        return props;
    }
}
