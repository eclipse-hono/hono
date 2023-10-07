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
package org.eclipse.hono.adapter.sigfox.app;

import org.eclipse.hono.adapter.AbstractProtocolAdapterApplication;
import org.eclipse.hono.adapter.http.HttpAdapterMetrics;
import org.eclipse.hono.adapter.sigfox.impl.SigfoxProtocolAdapter;
import org.eclipse.hono.adapter.sigfox.impl.SigfoxProtocolAdapterProperties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Hono Sigfox adapter main application class.
 */
@ApplicationScoped
public class Application extends AbstractProtocolAdapterApplication<SigfoxProtocolAdapterProperties> {

    private static final String CONTAINER_ID = "Hono Sigfox Adapter";

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
    protected SigfoxProtocolAdapter adapter() {

        final var adapter = new SigfoxProtocolAdapter();
        adapter.setConfig(protocolAdapterProperties);
        adapter.setMetrics(metrics);
        setCollaborators(adapter);
        return adapter;
    }
}
