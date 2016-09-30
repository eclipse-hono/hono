/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.lwm2m.registration;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.eclipse.hono.adapter.lwm2m.AbstractHonoClientSupport;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.client.ClientRegistryListener;
import org.eclipse.leshan.server.client.ClientUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * A leshan {@code ClientRegistryListener} that publishes life-cycle events to Hono's Telemetry API.
 */
@Component
@Profile("hono")
public class LifecycleEventForwarder extends AbstractHonoClientSupport implements ClientRegistryListener {

    private static final Logger LOG = LoggerFactory.getLogger(LifecycleEventForwarder.class);

    private Map<String, String> endpointMap;

    /**
     * Creates a new forwarder for an endpoint map.
     * 
     * @param endpointMap The object to use for resolving LWM2M endpoint names to Hono identifiers.
     */
    @Autowired
    public LifecycleEventForwarder(@Qualifier("endpointMap") final Map<String, String> endpointMap) {
        this.endpointMap = endpointMap;
    }

    @PostConstruct
    public void startup() {
        connectToHono();
    }

    @Override
    public void registered(final Client client) {

        LOG.debug("LWM2M client [endpoint: {}] registered", client.getEndpoint());
        sendLifecycleEvent(client.getEndpoint(), "register");
    }

    @Override
    public void updated(final ClientUpdate update, final Client clientUpdated) {
        LOG.info("LWM2M client [ep: {}] updated", clientUpdated.getEndpoint());
        sendLifecycleEvent(clientUpdated.getEndpoint(), "update-registration");
    }

    @Override
    public void unregistered(final Client client) {
        LOG.info("LWM2M client [ep: {}] unregistered", client.getEndpoint());
        sendLifecycleEvent(client.getEndpoint(), "unregister");
    }

    private void sendLifecycleEvent(final String endpoint, final String type) {

        String honoId = endpointMap.get(endpoint);
        if (honoId == null) {
            LOG.warn("endpoint {} could not be resolved to Hono ID", endpoint);
        } else {
            JsonObject msg = new JsonObject()
                    .put("lifecycle", new JsonObject()
                        .put("endpoint", endpoint)
                        .put("type", type));

            hono.getOrCreateTelemetrySender(tenant, reg -> {
                if (reg.succeeded()) {
                    reg.result().send(
                            honoId,
                            msg.encode(),
                            "application/json;charset=utf-8");
                } else {
                    LOG.warn("no connection to Hono, discarding registration event");
                }
            });
        }
    }
}
