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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.hono.adapter.mqtt.MessageMapping;
import org.eclipse.hono.adapter.mqtt.MqttAdapterMetrics;
import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.adapter.mqtt.impl.HttpBasedMessageMapping;
import org.eclipse.hono.adapter.mqtt.impl.VertxBasedMqttProtocolAdapter;
import org.eclipse.hono.adapter.quarkus.AbstractProtocolAdapterApplication;

import io.vertx.ext.web.client.WebClient;

/**
 * The Hono MQTT adapter main application class.
 */
@ApplicationScoped
public class Application extends AbstractProtocolAdapterApplication<MqttProtocolAdapterProperties> {

    private static final String CONTAINER_ID = "Hono MQTT Adapter";

    @Inject
    MqttAdapterMetrics metrics;

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getAdapterName() {
        return CONTAINER_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected VertxBasedMqttProtocolAdapter adapter() {

        final VertxBasedMqttProtocolAdapter adapter = new VertxBasedMqttProtocolAdapter();
        adapter.setConfig(protocolAdapterProperties);
        adapter.setMetrics(metrics);
        adapter.setMessageMapping(messageMapping());
        setCollaborators(adapter);
        return adapter;
    }

    private MessageMapping<MqttContext> messageMapping() {
        final WebClient webClient = WebClient.create(vertx);
        return new HttpBasedMessageMapping(webClient, protocolAdapterProperties);
    }
}
