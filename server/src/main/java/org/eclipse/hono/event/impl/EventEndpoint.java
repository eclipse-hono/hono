/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.event.impl;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.event.EventConstants;
import org.eclipse.hono.server.DownstreamAdapter;
import org.eclipse.hono.server.HonoServerConfigProperties;
import org.eclipse.hono.server.HonoServerMessageFilter;
import org.eclipse.hono.server.MessageForwardingEndpoint;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonQoS;

/**
 * A Hono {@code Endpoint} for uploading event messages.
 *
 */
@Component
@Scope("prototype")
@Qualifier("event")
public final class EventEndpoint extends MessageForwardingEndpoint<HonoServerConfigProperties> {

    private static final ProtonQoS[] SUPPORTED_DELIVERY_MODES = new ProtonQoS[]{ ProtonQoS.AT_LEAST_ONCE };

    /**
     * Creates a new endpoint.
     * 
     * @param vertx The Vert.x instance to run on.
     */
    @Autowired
    public EventEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    /**
     * Sets the adapter for sending events to the downstream AMQP 1.0 messaging network.
     * 
     * @param adapter The adapter.
     * @throws NullPointerException if the adapter is {@code null}.
     */
    @Autowired
    @Qualifier("event")
    public final void setEventAdapter(final DownstreamAdapter adapter) {
        setDownstreamAdapter(adapter);
    }

    @Override
    public String getName() {
        return EventConstants.EVENT_ENDPOINT;
    }

    @Override
    protected boolean passesFormalVerification(ResourceIdentifier targetAddress, Message message) {
        return HonoServerMessageFilter.verify(targetAddress, message);
    }

    /**
     * Gets the delivery mode supported by the Event API.
     * 
     * @return <em>AT_LEAST_ONCE</em> delivery mode.
     */
    @Override
    protected ProtonQoS[] getEndpointQos() {
        return SUPPORTED_DELIVERY_MODES;
    }
}
