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
import java.util.Map;
import java.util.Objects;

import javax.annotation.PostConstruct;

import org.eclipse.hono.adapter.lwm2m.AbstractHonoClientSupport;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.observation.ObservationListener;
import org.eclipse.leshan.server.registration.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A <em>leshan</em> {@code ObservationListener} for publishing notifications received from LWM2M clients to Hono's
 * Telemetry API.
 */
@Component
@Profile("step2")
public class TelemetryForwarder extends AbstractHonoClientSupport implements ObservationListener {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryForwarder.class);
    private TelemetryPayloadFactory messageFactory;
    private LwM2mModelProvider modelProvider;
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
    public void newObservation(final Observation observation, final Registration registration) {
        LOG.info("New observation for resource [client: {}, path: {}] has been established", registration.getEndpoint(),
                observation.getPath());
    }

    @Override
    public void cancelled(final Observation observation) {
        LOG.info("Observation [id: {}] for resource [path: {}] has been cancelled", observation.getRegistrationId(),
                observation.getPath());
    }

    @Override
    public void onResponse(final Observation observation, final Registration registration,
            final ObserveResponse response) {
        newValue(registration, observation, response.getContent());
    }

    @Override
    public void onError(final Observation observation, final Registration registration, final Exception error) {
        LOG.warn("Error while receiving notification [client: {}, path: {}]", registration.getEndpoint(),
                observation.getPath(), error);
    }

    private void newValue(final Registration registration, final Observation observation,
            final LwM2mNode mostRecentValue) {
        final String deviceId = endpointMap.get(registration.getEndpoint());
        if (deviceId == null) {
            LOG.warn("endpoint {} cannot be resolved to Hono ID, has device been registered with Hono?",
                    registration.getEndpoint());
        } else {
            hono.getOrCreateTelemetrySender(tenant, creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    final MessageSender sender = creationAttempt.result();
                    sendMostRecentValue(sender, registration, deviceId, observation.getPath(), mostRecentValue);
                } else {
                    LOG.error("cannot create telemetry sender for tenant {}, discarding notification", tenant,
                            creationAttempt.cause());
                }
            });
        }
    }

    private void sendMostRecentValue(final MessageSender sender, final Registration registration, final String deviceId,
            final LwM2mPath resourcePath, final LwM2mNode mostRecentValue) {

        synchronized (sender) {
            if (sender.sendQueueFull()) {
                LOG.info("discarding telemetry data reported by {}, no downstream credit for tenant {}",
                        deviceId, tenant);
            } else if (mostRecentValue != null) {
                final LwM2mModel definitions = modelProvider.getObjectModel(registration);
                final ObjectModel objectModel = definitions.getObjectModel(resourcePath.getObjectId());
                if (objectModel == null) {
                    LOG.error("cannot resolve ObjectModel for Object [id: {}], discarding notification",
                            resourcePath.getObjectId());
                } else {
                    final byte[] msg = messageFactory.getPayload(resourcePath, objectModel, mostRecentValue);
                    final String contentType = messageFactory.getContentType();
                    final Map<String, Object> props = new HashMap<>();
                    props.put("content_type", contentType);
                    props.put("object", objectModel.name);
                    sender.send(deviceId, props, msg, contentType);
                }
            }
        }
    }
}
