/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.app;

import java.util.Optional;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.notification.amqp.ProtonBasedNotificationSender;
import org.eclipse.hono.client.notification.kafka.KafkaBasedNotificationSender;
import org.eclipse.hono.client.notification.kafka.NotificationKafkaProducerConfigProperties;
import org.eclipse.hono.client.notification.pubsub.PubSubBasedNotificationSender;
import org.eclipse.hono.client.pubsub.PubSubConfigProperties;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.publisher.CachingPubSubPublisherFactory;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.notification.NotificationConstants;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.NotificationSender;
import org.eclipse.hono.service.ApplicationConfigProperties;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.util.ServiceClientAdapter;

import com.google.api.gax.core.CredentialsProvider;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Creates a client for publishing notifications using the configured messaging infrastructure.
 */
@ApplicationScoped
public class NotificationSenderProducer {

    @Inject
    Vertx vertx;

    @Inject
    Tracer tracer;

    @Inject
    HealthCheckServer healthCheckServer;

    @Inject
    ApplicationConfigProperties appConfig;

    @Produces
    @Singleton
    NotificationSender notificationSender(
            @Named("amqp-messaging-network") final ClientConfigProperties downstreamSenderConfig,
            final NotificationKafkaProducerConfigProperties kafkaProducerConfig,
            final KafkaClientMetricsSupport kafkaClientMetricsSupport,
            final PubSubConfigProperties pubSubConfigProperties) {

        final NotificationSender notificationSender;
        if (!appConfig.isKafkaMessagingDisabled() && kafkaProducerConfig.isConfigured()) {
            final KafkaProducerFactory<String, JsonObject> factory = CachingKafkaProducerFactory.sharedFactory(vertx);
            factory.setMetricsSupport(kafkaClientMetricsSupport);
            notificationSender = new KafkaBasedNotificationSender(factory, kafkaProducerConfig);
        } else if (!appConfig.isAmqpMessagingDisabled() && downstreamSenderConfig.isHostConfigured()) {
            notificationSender = new ProtonBasedNotificationSender(HonoConnection.newConnection(
                    vertx,
                    downstreamSenderConfig,
                    tracer));
        } else {
            final Optional<CredentialsProvider> credentialsProvider = PubSubMessageHelper.getCredentialsProvider();
            if (!appConfig.isPubSubMessagingDisabled() && pubSubConfigProperties.isProjectIdConfigured()
                    && credentialsProvider.isPresent()) {
                final PubSubPublisherFactory factory = new CachingPubSubPublisherFactory(
                        vertx,
                        pubSubConfigProperties.getProjectId(),
                        credentialsProvider.get());
                notificationSender = new PubSubBasedNotificationSender(
                        factory,
                        pubSubConfigProperties.getProjectId(),
                        tracer);
            } else {
                throw new IllegalStateException("at least one of Kafka, AMQP or Pub/Sub messaging must be configured");
            }
        }
        if (notificationSender instanceof ServiceClient serviceClient) {
            healthCheckServer.registerHealthCheckResources(ServiceClientAdapter.forClient(serviceClient));
        }
        NotificationConstants.DEVICE_REGISTRY_NOTIFICATION_TYPES.forEach(notificationType -> {
            NotificationEventBusSupport.registerConsumer(vertx, notificationType, notificationSender::publish);
        });
        return notificationSender;
    }
}
