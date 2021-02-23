/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli;

import org.eclipse.hono.application.client.ApplicationClient;
import org.eclipse.hono.application.client.amqp.AmqpMessageContext;
import org.eclipse.hono.application.client.amqp.ProtonBasedApplicationClient;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.application.client.kafka.impl.KafkaApplicationClientImpl;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;

/**
 * Configuration for CLI application.
 */
@Configuration
public class AppConfiguration {

    /**
     * Exposes a Vert.x instance as a Spring bean.
     *
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx vertx() {
        final VertxOptions options = new VertxOptions()
                .setWarningExceptionTime(1500000000)
                .setAddressResolverOptions(addressResolverOptions());
        return Vertx.vertx(options);
    }

    /**
     * Exposes address resolver option properties as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "address.resolver")
    @Bean
    public AddressResolverOptions addressResolverOptions() {
        final AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
        return addressResolverOptions;
    }

    /**
     * Exposes connection configuration properties as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.client")
    @Bean
    public ClientConfigProperties honoClientConfig() {
        final ClientConfigProperties config = new ClientConfigProperties();
        return config;
    }

    /**
     * Exposes Kafka consumer configuration properties as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka")
    @Profile("kafka")
    @Bean
    public KafkaConsumerConfigProperties honoKafkaClientConfig() {
        return new KafkaConsumerConfigProperties();
    }

    /**
     * Exposes Kafka producer configuration properties as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka")
    @Profile("kafka")
    @Bean
    public KafkaProducerConfigProperties kafkaProducerConfig() {
        return new KafkaProducerConfigProperties();
    }

    /**
     * Exposes a factory for creating producers for sending messages via the Kafka cluster.
     *
     * @return The factory.
     */
    @Profile("kafka")
    @Bean
    public KafkaProducerFactory<String, Buffer> kafkaProducerFactory() {
        return KafkaProducerFactory.sharedProducerFactory(vertx());
    }

    /**
     * Exposes a factory for creating clients for Hono's northbound APIs as a Spring bean.
     *
     * @return The factory.
     */
    // TODO remove this client factory once org.eclipse.hono.application.client.ApplicationClient supports C&C
    @Bean
    public ApplicationClientFactory clientFactory() {
        return ApplicationClientFactory.create(HonoConnection.newConnection(vertx(), honoClientConfig()));
    }

    /**
     * Exposes a client for Hono's AMQP-based northbound APIs as a Spring bean.
     *
     * @return The factory.
     * @param vertx The vertx instance to be used.
     * @param clientConfig The client configuration properties.
     */
    @Profile("!kafka")
    @Bean
    public ApplicationClient<AmqpMessageContext> AmqpApplicationClient(
            final Vertx vertx, final ClientConfigProperties clientConfig) {
        return new ProtonBasedApplicationClient(HonoConnection.newConnection(vertx, clientConfig));
    }

    /**
     * Exposes a client for Hono's Kafka-based northbound APIs as a Spring bean.
     *
     * @param vertx The vertx instance to be used.
     * @param kafkaConsumerConfigProperties The consumer configuration properties.
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param kafkaProducerConfigProperties The producer configuration properties.
     * @return The factory.
     */
    @Profile("kafka")
    @Bean
    public ApplicationClient<KafkaMessageContext> kafkaApplicationClient(
            final Vertx vertx,
            final KafkaConsumerConfigProperties kafkaConsumerConfigProperties,
            final KafkaProducerFactory producerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfigProperties) {
        return new KafkaApplicationClientImpl(vertx, kafkaConsumerConfigProperties, producerFactory,
                kafkaProducerConfigProperties);
    }
}
