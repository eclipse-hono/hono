/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.mqtt.quarkus;

import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.hono.adapter.mqtt.MessageMapping;
import org.eclipse.hono.adapter.mqtt.MqttAdapterMetrics;
import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.adapter.mqtt.impl.HttpBasedMessageMapping;
import org.eclipse.hono.adapter.mqtt.impl.VertxBasedMqttProtocolAdapter;
import org.eclipse.hono.service.quarkus.AbstractProtocolAdapterApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;

/**
 * The Hono MQTT adapter main application class.
 */
@ApplicationScoped
public class Application extends AbstractProtocolAdapterApplication {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Inject
    MqttAdapterMetrics metrics;

    @Inject
    MqttProtocolAdapterProperties adapterProperties;

    void onStart(final @Observes StartupEvent ev) {
        LOG.info("deploying {} MQTT adapter instances ...", config.app.getMaxInstances());

        final CompletableFuture<Void> startup = new CompletableFuture<>();
        final Promise<String> deploymentTracker = Promise.promise();
        vertx.deployVerticle(
                () -> adapter(),
                new DeploymentOptions().setInstances(config.app.getMaxInstances()),
                deploymentTracker);
        deploymentTracker.future()
            .compose(s -> healthCheckServer.start())
            .onSuccess(ok -> startup.complete(null))
            .onFailure(t -> startup.completeExceptionally(t));
        startup.join();

    }

    void onStop(final @Observes ShutdownEvent ev) {
        LOG.info("shutting down HTTP adapter");
        final CompletableFuture<Void> shutdown = new CompletableFuture<>();
        healthCheckServer.stop()
            .onComplete(ok -> {
                vertx.close(attempt -> {
                    if (attempt.succeeded()) {
                        shutdown.complete(null);
                    } else {
                        shutdown.completeExceptionally(attempt.cause());
                    }
                });
            });
        shutdown.join();
    }

    private VertxBasedMqttProtocolAdapter adapter() {

        final VertxBasedMqttProtocolAdapter adapter = new VertxBasedMqttProtocolAdapter();
        adapter.setCommandConsumerFactory(commandConsumerFactory());
        adapter.setCommandTargetMapper(commandTargetMapper());
        adapter.setConfig(adapterProperties);
        adapter.setCredentialsClient(credentialsClient());
        adapter.setDeviceConnectionClient(deviceConnectionClient());
        adapter.setEventSender(downstreamSender());
        adapter.setHealthCheckServer(healthCheckServer);
        adapter.setMetrics(metrics);
        adapter.setRegistrationClient(registrationClient());
        adapter.setTelemetrySender(downstreamSender());
        adapter.setTenantClient(tenantClient());
        adapter.setTracer(tracer);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        adapter.setMessageMapping(messageMapping());
        return adapter;
    }

    private MessageMapping<MqttContext> messageMapping() {
        final WebClient webClient = WebClient.create(vertx);
        return new HttpBasedMessageMapping(webClient, adapterProperties);
    }
}
