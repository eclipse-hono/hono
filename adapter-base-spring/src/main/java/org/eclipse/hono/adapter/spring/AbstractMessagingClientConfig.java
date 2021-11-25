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

import org.eclipse.hono.adapter.MessagingClientProviders;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.amqp.ProtonBasedCommandResponseSender;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandResponseSender;
import org.eclipse.hono.client.kafka.CommonKafkaClientConfigProperties;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.client.telemetry.kafka.KafkaBasedEventSender;
import org.eclipse.hono.client.telemetry.kafka.KafkaBasedTelemetrySender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.ComponentNameProvider;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
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
public abstract class AbstractMessagingClientConfig implements ComponentNameProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Creates new messaging client providers according to the configuration in use.
     *
     * @param samplerFactory The sampler factory to use.
     * @param tracer The tracer instance.
     * @param vertx The Vert.x instance to use.
     * @param adapterProperties The adapter's configuration properties.
     * @param kafkaClientMetricsSupport The Kafka metrics support.
     * @return The created messaging clients.
     */
    protected MessagingClientProviders messagingClientProviders(
            final SendMessageSampler.Factory samplerFactory,
            final Tracer tracer,
            final Vertx vertx,
            final ProtocolAdapterProperties adapterProperties,
            final KafkaClientMetricsSupport kafkaClientMetricsSupport) {

        final MessagingClientProvider<TelemetrySender> telemetrySenderProvider = new MessagingClientProvider<>();
        final MessagingClientProvider<EventSender> eventSenderProvider = new MessagingClientProvider<>();
        final MessagingClientProvider<CommandResponseSender> commandResponseSenderProvider = new MessagingClientProvider<>();

        if (commonKafkaClientConfig().isConfigured()) {
            log.info("Kafka client configuration present, adding Kafka messaging clients");
            final KafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory.sharedFactory(vertx);
            factory.setMetricsSupport(kafkaClientMetricsSupport);

            telemetrySenderProvider.setClient(new KafkaBasedTelemetrySender(factory, kafkaTelemetryConfig(),
                    adapterProperties.isDefaultsEnabled(), tracer));
            eventSenderProvider.setClient(new KafkaBasedEventSender(factory, kafkaEventConfig(),
                    adapterProperties.isDefaultsEnabled(), tracer));
            commandResponseSenderProvider.setClient(new KafkaBasedCommandResponseSender(factory,
                    kafkaCommandResponseConfig(), tracer));
        }

        if (downstreamSenderConfig().isHostConfigured()) {
            log.info("AMQP 1.0 connection is configured, adding AMQP 1.0 messaging clients");
            telemetrySenderProvider.setClient(
                    new ProtonBasedDownstreamSender(
                            downstreamConnection(vertx),
                            samplerFactory,
                            adapterProperties.isDefaultsEnabled(),
                            adapterProperties.isJmsVendorPropsEnabled()));
            eventSenderProvider.setClient(
                    new ProtonBasedDownstreamSender(
                            downstreamConnection(vertx),
                            samplerFactory,
                            adapterProperties.isDefaultsEnabled(),
                            adapterProperties.isJmsVendorPropsEnabled()));
            commandResponseSenderProvider.setClient(
                    new ProtonBasedCommandResponseSender(
                            commandConsumerConnection(vertx),
                            samplerFactory,
                            adapterProperties.isJmsVendorPropsEnabled()));
        }

        return new MessagingClientProviders(telemetrySenderProvider, eventSenderProvider, commandResponseSenderProvider);
    }

    /**
     * Exposes common configuration properties for a clients accessing the Kafka cluster as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka")
    @Bean
    public CommonKafkaClientConfigProperties commonKafkaClientConfig() {
        return new CommonKafkaClientConfigProperties();
    }

    /**
     * Exposes configuration properties for the Kafka producer that publishes telemetry messages as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka.telemetry")
    @Bean
    public MessagingKafkaProducerConfigProperties kafkaTelemetryConfig() {
        final MessagingKafkaProducerConfigProperties configProperties = new MessagingKafkaProducerConfigProperties();
        configProperties.setCommonClientConfig(commonKafkaClientConfig());
        if (getComponentName() != null) {
            configProperties.setDefaultClientIdPrefix(getComponentName());
        }
        return configProperties;
    }

    /**
     * Exposes configuration properties for the Kafka producer that publishes events as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka.event")
    @Bean
    public MessagingKafkaProducerConfigProperties kafkaEventConfig() {
        final MessagingKafkaProducerConfigProperties configProperties = new MessagingKafkaProducerConfigProperties();
        configProperties.setCommonClientConfig(commonKafkaClientConfig());
        if (getComponentName() != null) {
            configProperties.setDefaultClientIdPrefix(getComponentName());
        }
        return configProperties;
    }

    /**
     * Exposes configuration properties for the Kafka producer that publishes command responses as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka.command-response")
    @Bean
    public MessagingKafkaProducerConfigProperties kafkaCommandResponseConfig() {
        final MessagingKafkaProducerConfigProperties configProperties = new MessagingKafkaProducerConfigProperties();
        configProperties.setCommonClientConfig(commonKafkaClientConfig());
        if (getComponentName() != null) {
            configProperties.setDefaultClientIdPrefix(getComponentName());
        }
        return configProperties;
    }

    /**
     * Exposes configuration properties for the Kafka consumer that receives commands as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka.command")
    @Bean
    public MessagingKafkaConsumerConfigProperties kafkaCommandConfig() {
        final MessagingKafkaConsumerConfigProperties configProperties = new MessagingKafkaConsumerConfigProperties();
        configProperties.setCommonClientConfig(commonKafkaClientConfig());
        if (getComponentName() != null) {
            configProperties.setDefaultClientIdPrefix(getComponentName());
        }
        return configProperties;
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
        config.setNameIfNotSet(getComponentName());
        return config;
    }

    /**
     * Exposes the connection to the <em>AMQP Messaging Network</em> as a Spring bean.
     * <p>
     * The connection is configured with the properties provided by {@link #downstreamSenderConfig()}
     * and is already trying to establish the connection to the configured peer.
     *
     * @param vertx The Vert.x instance to use.
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
     * Exposes configuration properties for accessing the AMQP Messaging Network for receiving upstream commands as a
     * Spring bean.
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
        config.setNameIfNotSet(getComponentName());
        return config;
    }

    /**
     * Exposes the connection to the AMQP Messaging Network used for receiving upstream commands as a Spring bean.
     *
     * @param vertx The Vert.x instance to use.
     * @return The connection.
     */
    @Bean
    @Scope("prototype")
    public HonoConnection commandConsumerConnection(final Vertx vertx) {
        return HonoConnection.newConnection(vertx, commandConsumerFactoryConfig());
    }
}
