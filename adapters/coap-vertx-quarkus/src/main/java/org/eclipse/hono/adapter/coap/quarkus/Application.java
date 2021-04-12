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
package org.eclipse.hono.adapter.coap.quarkus;

import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.hono.adapter.coap.CoapAdapterMetrics;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.CommandResponseResource;
import org.eclipse.hono.adapter.coap.EventResource;
import org.eclipse.hono.adapter.coap.TelemetryResource;
import org.eclipse.hono.adapter.coap.impl.VertxBasedCoapAdapter;
import org.eclipse.hono.adapter.coap.lwm2m.LeshanBasedLwM2MRegistrationStore;
import org.eclipse.hono.adapter.coap.lwm2m.LwM2MResourceDirectory;
import org.eclipse.hono.adapter.quarkus.AbstractProtocolAdapterApplication;
import org.eclipse.leshan.server.californium.registration.InMemoryRegistrationStore;

/**
 * The Hono CoAP adapter main application class.
 */
@ApplicationScoped
public class Application extends AbstractProtocolAdapterApplication<CoapAdapterProperties> {

    private static final String CONTAINER_ID = "Hono CoAP Adapter";

    @Inject
    CoapAdapterMetrics metrics;

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
    protected VertxBasedCoapAdapter adapter() {

        final var adapter = new VertxBasedCoapAdapter();
        adapter.setConfig(protocolAdapterProperties);
        adapter.setMetrics(metrics);
        setCollaborators(adapter);
        adapter.addResources(Set.of(
                new TelemetryResource(adapter, tracer, vertx),
                new EventResource(adapter, tracer, vertx),
                new CommandResponseResource(adapter, tracer, vertx)));
        if (protocolAdapterProperties.isLwm2mEnabled()) {
            final var observationStore = new InMemoryRegistrationStore();
            adapter.setObservationStore(observationStore);
            final var store = new LeshanBasedLwM2MRegistrationStore(
                    observationStore,
                    adapter,
                    tracer);
            adapter.addResources(Set.of(new LwM2MResourceDirectory(adapter, store, tracer)));
        }
        return adapter;
    }
}
