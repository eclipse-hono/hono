/**
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
 */
package org.eclipse.hono.adapter.lora.quarkus;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.eclipse.hono.adapter.http.HttpAdapterMetrics;
import org.eclipse.hono.adapter.lora.LoraProtocolAdapter;
import org.eclipse.hono.adapter.lora.LoraProtocolAdapterProperties;
import org.eclipse.hono.adapter.lora.providers.LoraProvider;
import org.eclipse.hono.adapter.quarkus.AbstractProtocolAdapterApplication;

/**
 * The Hono Lora adapter main application class.
 */
@ApplicationScoped
public class Application extends AbstractProtocolAdapterApplication<LoraProtocolAdapterProperties> {

    private static final String CONTAINER_ID = "Hono Lora Adapter";

    @Inject
    HttpAdapterMetrics metrics;

    @Inject
    Instance<LoraProvider> loraProviders;

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
    protected LoraProtocolAdapter adapter() {

        final List<LoraProvider> providers = new ArrayList<>();
        loraProviders.forEach(handler -> providers.add(handler));

        final LoraProtocolAdapter adapter = new LoraProtocolAdapter();
        adapter.setConfig(protocolAdapterProperties);
        adapter.setLoraProviders(providers);
        adapter.setMetrics(metrics);
        setCollaborators(adapter);
        return adapter;
    }
}
