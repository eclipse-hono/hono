/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.adapter.spring;

import java.util.Optional;

import org.eclipse.hono.adapter.AdapterConfigurationSupport;
import org.eclipse.hono.adapter.MessagingClientSet;
import org.eclipse.hono.adapter.MessagingClients;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandResponseSender;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommandResponseSender;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.adapter.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.adapter.client.telemetry.kafka.KafkaBasedEventSender;
import org.eclipse.hono.adapter.client.telemetry.kafka.KafkaBasedTelemetrySender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessagingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;


/**
 * A base class that provides helper methods for configuring messaging clients.
 */
@Configuration
public abstract class AbstractMessagingClientConfig extends AdapterConfigurationSupport {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Creates new messaging clients according to the configuration in use.
     *
     * @param samplerFactory The sampler factory to use.
     * @param tracer The tracer instance.
     * @param vertx The Vert.x instance to use.
     * @param adapterProperties The adapter's configuration properties.
     *
     * @return The created messaging clients.
     */
    protected MessagingClients messagingClients(final SendMessageSampler.Factory samplerFactory,
            final Tracer tracer,
            final Vertx vertx,
            final ProtocolAdapterProperties adapterProperties) {

        final MessagingClients messagingClients = new MessagingClients();

        if (kafkaProducerConfig().isConfigured()) {
            log.info("Kafka Producer is configured. Adding Kafka messaging client.");
            messagingClients.addClientSet(kafkaMessagingClientSet(adapterProperties, vertx, tracer));
        }

        if (downstreamSenderConfig() != null) { // TODO proper check if AMQP network configured
            log.info("AMQP 1.0 Downstream Sender is configured. Adding AMQP 1.0 messaging client.");
            messagingClients.addClientSet(amqpMessagingClientSet(vertx, samplerFactory, adapterProperties));
        }

        return messagingClients;
    }

    /**
     * Exposes configuration properties for a producer accessing the Kafka cluster as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka")
    @Bean
    public KafkaProducerConfigProperties kafkaProducerConfig() {
        final KafkaProducerConfigProperties configProperties = new KafkaProducerConfigProperties();
        if (getAdapterName() != null) {
            configProperties.setDefaultClientIdPrefix(getAdapterName());
        }
        return configProperties;
    }

    /**
     * Exposes configuration properties for a consumer accessing the Kafka cluster as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka")
    @Bean
    public KafkaConsumerConfigProperties kafkaConsumerConfig() {
        final KafkaConsumerConfigProperties configProperties = new KafkaConsumerConfigProperties();
        if (getAdapterName() != null) {
            configProperties.setDefaultClientIdPrefix(getAdapterName());
        }
        return configProperties;
    }

    /**
     * Exposes a messaging client set for <em>Kafka</em> as a Spring bean.
     *
     * @param adapterConfig The protocol adapter's configuration properties.
     * @param vertx The Vert.x instance to use.
     * @param tracer The tracer instance.
     *
     * @return The client set.
     */
    @Bean
    @Scope("prototype")
    public MessagingClientSet kafkaMessagingClientSet(final ProtocolAdapterProperties adapterConfig,
            final Vertx vertx,
            final Tracer tracer) {

        final KafkaProducerConfigProperties producerConfig = kafkaProducerConfig();
        final KafkaProducerFactory<String, Buffer> factory = KafkaProducerFactory.sharedProducerFactory(vertx);

        return new MessagingClientSet(MessagingType.kafka,
                new KafkaBasedEventSender(factory, producerConfig, adapterConfig, tracer),
                new KafkaBasedTelemetrySender(factory, producerConfig, adapterConfig, tracer),
                new KafkaBasedCommandResponseSender(factory, producerConfig, tracer));
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #downstreamSenderConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected ClientConfigProperties getDownstreamSenderConfigDefaults() {
        return new ClientConfigProperties();
    }

    /**
     * Exposes configuration properties for accessing the AMQP Messaging Network as a Spring bean.
     * <p>
     * A default set of properties, on top of which the configured properties will by loaded, can be set in subclasses
     * by means of overriding the {@link #getDownstreamSenderConfigDefaults()} method.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @ConfigurationProperties(prefix = "hono.messaging")
    @Bean
    public ClientConfigProperties downstreamSenderConfig() {
        final ClientConfigProperties config = Optional.ofNullable(getDownstreamSenderConfigDefaults())
                .orElseGet(ClientConfigProperties::new);
        config.setServerRoleIfUnknown("AMQP Messaging Network");
        config.setNameIfNotSet(getAdapterName());
        return config;
    }

    /**
     * Exposes the connection to the <em>AMQP Messaging Network</em> as a Spring bean.
     * <p>
     * The connection is configured with the properties provided by {@link #downstreamSenderConfig()}
     * and is already trying to establish the connection to the configured peer.
     *
     * @param vertx The Vert.x instance to use.
     *
     * @return The connection.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @Bean
    @Scope("prototype")
    public HonoConnection downstreamConnection(final Vertx vertx) {
        return HonoConnection.newConnection(vertx, downstreamSenderConfig());
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #commandConsumerFactoryConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected ClientConfigProperties getCommandConsumerFactoryConfigDefaults() {
        return new ClientConfigProperties();
    }

    /**
     * Exposes configuration properties for Command and Control.
     *
     * @return The Properties.
     */
    @Qualifier(CommandConstants.COMMAND_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.command")
    @Bean
    public ClientConfigProperties commandConsumerFactoryConfig() {
        final ClientConfigProperties config = Optional.ofNullable(getCommandConsumerFactoryConfigDefaults())
                .orElseGet(ClientConfigProperties::new);
        config.setServerRoleIfUnknown("Command & Control");
        config.setNameIfNotSet(getAdapterName());
        return config;
    }

    /**
     * Exposes the connection used for receiving upstream commands as a Spring bean.
     *
     * @param vertx The Vert.x instance to use.
     *
     * @return The connection.
     */
    @Bean
    @Scope("prototype")
    public HonoConnection commandConsumerConnection(final Vertx vertx) {
        return HonoConnection.newConnection(vertx, commandConsumerFactoryConfig());
    }

    /**
     * Exposes a messaging client set for <em>AMQP</em> as a Spring bean.
     * <p>
     * The clients are initialized with the connections provided by {@link #downstreamConnection(Vertx)}.
     *
     * @param vertx The Vert.x instance to use.
     * @param samplerFactory The sampler factory to use. Can re-use adapter metrics, based on
     *            {@link org.eclipse.hono.adapter.metric.MicrometerBasedMetrics} out of the box.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @return The client set.
     */
    @Bean
    @Scope("prototype")
    public MessagingClientSet amqpMessagingClientSet(
            final Vertx vertx,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {

        final EventSender eventSender = new ProtonBasedDownstreamSender(downstreamConnection(vertx), samplerFactory,
                adapterConfig.isDefaultsEnabled(), adapterConfig.isJmsVendorPropsEnabled())    ;
        final TelemetrySender telemetrySender = new ProtonBasedDownstreamSender(downstreamConnection(vertx), samplerFactory,
                adapterConfig.isDefaultsEnabled(), adapterConfig.isJmsVendorPropsEnabled());
        final CommandResponseSender commandResponseSender = new ProtonBasedCommandResponseSender(
                commandConsumerConnection(vertx), samplerFactory, adapterConfig);

        return new MessagingClientSet(MessagingType.amqp, eventSender, telemetrySender, commandResponseSender);
    }
}
