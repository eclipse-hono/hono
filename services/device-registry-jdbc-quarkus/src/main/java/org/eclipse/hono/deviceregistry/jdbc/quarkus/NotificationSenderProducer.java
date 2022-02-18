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

package org.eclipse.hono.deviceregistry.jdbc.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.notification.amqp.ProtonBasedNotificationSender;
import org.eclipse.hono.client.notification.kafka.KafkaBasedNotificationSender;
import org.eclipse.hono.client.notification.kafka.NotificationKafkaProducerConfigProperties;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.deviceregistry.util.ServiceClientAdapter;
import org.eclipse.hono.notification.NotificationConstants;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.NotificationSender;
import org.eclipse.hono.service.HealthCheckServer;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;

/**
 * Creates a client for publishing notifications using the configured messaging infrastructure.
 */
@ApplicationScoped
public class NotificationSenderProducer {

    @Produces
    @Singleton
    NotificationSender notificationSender(
            final Vertx vertx,
            final Tracer tracer,
            final HealthCheckServer healthCheckServer,
            @Named("amqp-messaging-network")
            final ClientConfigProperties downstreamSenderConfig,
            final NotificationKafkaProducerConfigProperties kafkaProducerConfig
            ) {

        final NotificationSender notificationSender;
        if (kafkaProducerConfig.isConfigured()) {
            notificationSender = new KafkaBasedNotificationSender(
                    CachingKafkaProducerFactory.sharedFactory(vertx),
                    kafkaProducerConfig);
        } else {
            notificationSender = new ProtonBasedNotificationSender(HonoConnection.newConnection(
                    vertx,
                    downstreamSenderConfig,
                    tracer));
        }
        if (notificationSender instanceof ServiceClient) {
            healthCheckServer.registerHealthCheckResources(ServiceClientAdapter.forClient((ServiceClient) notificationSender));
        }
        NotificationConstants.DEVICE_REGISTRY_NOTIFICATION_TYPES.forEach(notificationType -> {
            NotificationEventBusSupport.registerConsumer(vertx, notificationType, notificationSender::publish);
        });
        return notificationSender;
    }
}
