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
 *
 */

package org.eclipse.hono.event.impl;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.event.EventConstants;
import org.eclipse.hono.event.EventMessageFilter;
import org.eclipse.hono.server.DownstreamAdapter;
import org.eclipse.hono.server.MessageForwardingEndpoint;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
@ConfigurationProperties(prefix = "hono.event")
public final class EventEndpoint extends MessageForwardingEndpoint {

    @Autowired
    public EventEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

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
        return EventMessageFilter.verify(targetAddress, message);
    }

    @Override
    protected ProtonQoS getEndpointQos() {
        return ProtonQoS.AT_LEAST_ONCE;
    }
}
