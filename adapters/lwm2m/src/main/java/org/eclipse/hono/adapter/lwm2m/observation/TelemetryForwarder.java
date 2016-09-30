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

package org.eclipse.hono.adapter.lwm2m.observation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.PostConstruct;

import org.eclipse.hono.adapter.lwm2m.AbstractHonoClientSupport;
import org.eclipse.hono.client.TelemetrySender;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.TimestampedLwM2mNode;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.client.ClientRegistry;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.observation.ObservationRegistryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A <em>leshan</em> {@code ObservationRegistryListener} for publishing notifications received from LWM2M clients to Hono's
 * Telemetry API.
 */
@Component
@Profile("hono")
public class TelemetryForwarder extends AbstractHonoClientSupport implements ObservationRegistryListener {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryForwarder.class);
    private TelemetryPayloadFactory messageFactory;
    private LwM2mModelProvider modelProvider;
    private ClientRegistry clientRegistry;
    private Map<String, String> endpointMap;

    /**
     * Sets the factory to use for creating JSON payload for a notification.
     * 
     * @param payloadFactory The factory to use.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Autowired
    public void setTelemetryPayloadFactory(final TelemetryPayloadFactory payloadFactory) {
        this.messageFactory = Objects.requireNonNull(payloadFactory);
    }

    /**
     * Sets the provider to use for looking up Object models for observed resources.
     * 
     * @param modelProvider The model provider to use.
     * @throws NullPointerException if the provider is {@code null}.
     */
    @Autowired
    public void setModelProvider(final LwM2mModelProvider modelProvider) {
        this.modelProvider = Objects.requireNonNull(modelProvider);
    }

    /**
     * Sets the registry to use for looking up LWM2M clients corresponding to observed resources.
     * 
     * @param clientRegistry The registry to use.
     * @throws NullPointerException if the registry is {@code null}.
     */
    @Autowired
    public void setClientRegistry(final ClientRegistry clientRegistry) {
        this.clientRegistry = Objects.requireNonNull(clientRegistry);
    }

    /**
     * Sets the map containing LWM2M endpoint name to Hono identifier mappings.
     * 
     * @param endpointMap The endpoint map.
     * @throws NullPointerException if the map is {@code null}.
     */
    @Autowired
    @Qualifier("endpointMap")
    public void setEndpointMap(final Map<String, String> endpointMap) {
        this.endpointMap = Objects.requireNonNull(endpointMap);
    }

    @PostConstruct
    public void startup() throws IllegalStateException {

        connectToHono();
    }

    @Override
    public void newObservation(final Observation observation) {

        Client client = clientRegistry.findByRegistrationId(observation.getRegistrationId());
        LOG.info("New observation for resource [client: {}, path: {}] has been established", client.getEndpoint(),
                observation.getPath());
    }

    @Override
    public void cancelled(final Observation observation) {
        LOG.info("Observation [id: {}] for resource [path: {}] has been cancelled", observation.getRegistrationId(),
                observation.getPath());
    }

    @Override
    public void newValue(final Observation observation, final LwM2mNode mostRecentValue,
            final List<TimestampedLwM2mNode> timestampedValues) {

        Client client = clientRegistry.findByRegistrationId(observation.getRegistrationId());
        if (client == null) {
            LOG.info("discarding notification from unknown device");
        } else {
            newValue(client, observation, mostRecentValue, timestampedValues);
        }
    }

    private void newValue(final Client client, final Observation observation, final LwM2mNode mostRecentValue,
            final List<TimestampedLwM2mNode> timestampedValues) {

        final String deviceId = endpointMap.get(client.getEndpoint());
        if (deviceId == null) {
            LOG.warn("endpoint {} cannot be resolved to Hono ID, has device been registered with Hono?", client.getEndpoint());
        } else {
            hono.getOrCreateTelemetrySender(tenant, creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    TelemetrySender sender = creationAttempt.result();
                    sendMostRecentValue(sender, client, deviceId, observation.getPath(), mostRecentValue);
                } else {
                    LOG.error("cannot create telemetry sender for tenant {}, discarding notification", tenant, creationAttempt.cause());
                }
            });
        }
    }

    private void sendMostRecentValue(final TelemetrySender sender, final Client client, final String deviceId, final LwM2mPath resourcePath, final LwM2mNode mostRecentValue) {

        synchronized (sender) {
            if (sender.sendQueueFull()) {
                LOG.info("discarding telemetry data reported by {}, no downstream credit for tenant {}",
                        deviceId, tenant);
            } else if (mostRecentValue != null) {
                LwM2mModel definitions = modelProvider.getObjectModel(client);
                ObjectModel objectModel = definitions.getObjectModel(resourcePath.getObjectId());
                if (objectModel == null) {
                    LOG.error("cannot resolve ObjectModel for Object [id: {}], discarding notification", resourcePath.getObjectId());
                } else {
                    byte[] msg = messageFactory.getPayload(resourcePath, objectModel, mostRecentValue);
                    String contentType = messageFactory.getContentType();
                    Map<String, Object> props = new HashMap<>();
                    props.put("content_type", contentType);
                    props.put("object", objectModel.name);
                    sender.send(deviceId, props, msg, contentType);
                }
            }
        }
    }
}
