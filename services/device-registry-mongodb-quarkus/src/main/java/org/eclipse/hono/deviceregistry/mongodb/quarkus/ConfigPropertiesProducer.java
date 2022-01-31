/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.mongodb.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.eclipse.hono.client.kafka.producer.KafkaProducerOptions;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.notification.kafka.NotificationKafkaProducerConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.quarkus.ClientOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisionerConfigOptions;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisionerConfigProperties;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientConfigProperties;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;

/**
 * A producer of registry service configuration properties.
 *
 */
@ApplicationScoped
public class ConfigPropertiesProducer {

    @Produces
    @Singleton
    AuthenticationServerClientConfigProperties authenticationServerClientProperties(
            @ConfigMapping(prefix = "hono.auth")
            final AuthenticationServerClientOptions options) {
        final var props = new AuthenticationServerClientConfigProperties(options);
        props.setServerRoleIfUnknown("Authentication Server");
        return props;
    }

    @Produces
    @Singleton
    @Named("amqp-messaging-network")
    ClientConfigProperties downstreamSenderProperties(
            @ConfigMapping(prefix = "hono.messaging", namingStrategy = NamingStrategy.VERBATIM)
            final ClientOptions downstreamSenderOptions) {
        final var result = new ClientConfigProperties(downstreamSenderOptions);
        result.setServerRoleIfUnknown("AMQP Messaging Network");
        result.setNameIfNotSet("Hono MongoDB Device Registry");
        return result;
    }

    /**
     * Creates Tenant service configuration properties from existing options.
     *
     * @param options The options.
     * @return The properties.
     */
    @Produces
    @Singleton
    public MongoDbBasedTenantsConfigProperties tenantServiceProperties(
            final MongoDbBasedTenantsConfigOptions options) {
        return new MongoDbBasedTenantsConfigProperties(options);
    }

    /**
     * Creates Device Registration service configuration properties from existing options.
     *
     * @param options The options.
     * @return The properties.
     */
    @Produces
    @Singleton
    public MongoDbBasedRegistrationConfigProperties registrationServiceProperties(
            final MongoDbBasedRegistrationConfigOptions options) {
        return new MongoDbBasedRegistrationConfigProperties(options);
    }

    /**
     * Creates Device Registration service configuration properties from existing options.
     *
     * @param options The options.
     * @return The properties.
     */
    @Produces
    @Singleton
    public MongoDbBasedCredentialsConfigProperties credentialsServiceProperties(
            final MongoDbBasedCredentialsConfigOptions options) {
        return new MongoDbBasedCredentialsConfigProperties(options);
    }

    @Produces
    @Singleton
    MessagingKafkaProducerConfigProperties eventKafkaProducerClientOptions(
            @ConfigMapping(prefix = "hono.kafka")
            final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.event")
            final KafkaProducerOptions eventProducerOptions) {

        return new MessagingKafkaProducerConfigProperties(
                commonOptions,
                eventProducerOptions);
    }

    @Produces
    @Singleton
    NotificationKafkaProducerConfigProperties notificationKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka")
            final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.notification")
            final KafkaProducerOptions notificationOptions) {

        return new NotificationKafkaProducerConfigProperties(
                commonOptions,
                notificationOptions);
    }

    @Produces
    @Singleton
    AutoProvisionerConfigProperties autoProvisionerOptions(final AutoProvisionerConfigOptions options) {
        final var result = new AutoProvisionerConfigProperties();
        result.setRetryEventSendingDelay(options.retryEventSendingDelay());
        return result;
    }
}
