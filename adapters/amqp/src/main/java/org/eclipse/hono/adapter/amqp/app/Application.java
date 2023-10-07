/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp.app;

import org.eclipse.hono.adapter.AbstractProtocolAdapterApplication;
import org.eclipse.hono.adapter.amqp.AmqpAdapterMetrics;
import org.eclipse.hono.adapter.amqp.AmqpAdapterProperties;
import org.eclipse.hono.adapter.amqp.VertxBasedAmqpProtocolAdapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Hono AMQP adapter main application class.
 */
@ApplicationScoped
public class Application extends AbstractProtocolAdapterApplication<AmqpAdapterProperties> {

    private static final String CONTAINER_ID = "Hono AMQP Adapter";

    @Inject
    AmqpAdapterMetrics metrics;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getComponentName() {
        return CONTAINER_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected VertxBasedAmqpProtocolAdapter adapter() {

        final VertxBasedAmqpProtocolAdapter adapter = new VertxBasedAmqpProtocolAdapter();
        adapter.setConfig(protocolAdapterProperties);
        adapter.setMetrics(metrics);
        setCollaborators(adapter);
        return adapter;
    }
}
