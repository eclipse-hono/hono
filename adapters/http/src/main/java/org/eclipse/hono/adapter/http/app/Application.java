/**
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.http.app;

import org.eclipse.hono.adapter.AbstractProtocolAdapterApplication;
import org.eclipse.hono.adapter.http.HttpAdapterMetrics;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.adapter.http.impl.VertxBasedHttpProtocolAdapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Hono HTTP adapter main application class.
 */
@ApplicationScoped
public class Application extends AbstractProtocolAdapterApplication<HttpProtocolAdapterProperties> {

    private static final String CONTAINER_ID = "Hono HTTP Adapter";

    @Inject
    HttpAdapterMetrics metrics;

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
    protected VertxBasedHttpProtocolAdapter adapter() {

        final VertxBasedHttpProtocolAdapter adapter = new VertxBasedHttpProtocolAdapter();
        adapter.setConfig(protocolAdapterProperties);
        adapter.setMetrics(metrics);
        setCollaborators(adapter);
        return adapter;
    }
}
