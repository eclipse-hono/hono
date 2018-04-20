/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */
package org.eclipse.hono.telemetry.impl;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.messaging.DownstreamAdapter;
import org.eclipse.hono.messaging.HonoMessagingConfigProperties;
import org.eclipse.hono.messaging.HonoMessagingMessageFilter;
import org.eclipse.hono.messaging.MessageForwardingEndpoint;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonQoS;

/**
 * A Hono {@code Endpoint} for uploading telemetry data.
 *
 */
@Component
@Scope("prototype")
@Qualifier("telemetry")
public final class TelemetryEndpoint extends MessageForwardingEndpoint<HonoMessagingConfigProperties> {

    private static final Set<ProtonQoS> SUPPORTED_DELIVERY_MODES = Collections
            .unmodifiableSet(EnumSet.of(ProtonQoS.AT_LEAST_ONCE, ProtonQoS.AT_MOST_ONCE));

    /**
     * Creates a new endpoint.
     * 
     * @param vertx The Vert.x instance to run on.
     */
    @Autowired
    public TelemetryEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    /**
     * Sets the adapter for sending telemetry messages to the downstream AMQP 1.0 messaging network.
     * 
     * @param adapter The adapter.
     * @throws NullPointerException if the adapter is {@code null}.
     */
    @Autowired
    @Qualifier("telemetry")
    public void setTelemetryAdapter(final DownstreamAdapter adapter) {
        setDownstreamAdapter(adapter);
    }

    @Override
    public String getName() {
        return TelemetryConstants.TELEMETRY_ENDPOINT;
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier targetAddress, final Message message) {

        return HonoMessagingMessageFilter.verify(targetAddress, message);
    }

    /**
     * Gets the delivery modes supported by the Telemetry API.
     * 
     * @return <em>AT_MOST_ONCE</em> and <em>AT_LEAST_ONCE</em>.
     */
    @Override
    protected Set<ProtonQoS> getEndpointQos() {
        return SUPPORTED_DELIVERY_MODES;
    }
}
