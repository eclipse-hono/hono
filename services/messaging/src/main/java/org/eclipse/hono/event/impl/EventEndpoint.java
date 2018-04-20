/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 *
 */

package org.eclipse.hono.event.impl;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.messaging.DownstreamAdapter;
import org.eclipse.hono.messaging.HonoMessagingConfigProperties;
import org.eclipse.hono.messaging.HonoMessagingMessageFilter;
import org.eclipse.hono.messaging.MessageForwardingEndpoint;
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
public final class EventEndpoint extends MessageForwardingEndpoint<HonoMessagingConfigProperties> {

    private static final Set<ProtonQoS> SUPPORTED_DELIVERY_MODES = Collections
            .unmodifiableSet(EnumSet.of(ProtonQoS.AT_LEAST_ONCE));

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
    public void setEventAdapter(final DownstreamAdapter adapter) {
        setDownstreamAdapter(adapter);
    }

    @Override
    public String getName() {
        return EventConstants.EVENT_ENDPOINT;
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier targetAddress, final Message message) {
        return HonoMessagingMessageFilter.verify(targetAddress, message);
    }

    /**
     * Gets the delivery mode supported by the Event API.
     * 
     * @return <em>AT_LEAST_ONCE</em> delivery mode.
     */
    @Override
    protected Set<ProtonQoS> getEndpointQos() {
        return SUPPORTED_DELIVERY_MODES;
    }
}
