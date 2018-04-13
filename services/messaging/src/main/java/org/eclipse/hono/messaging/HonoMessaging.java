/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.messaging;

import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonSession;

/**
 * Hono Messaging is an AMQP 1.0 container that provides nodes for uploading <em>Telemetry</em> and
 * <em>Event</em> messages.
 */
public final class HonoMessaging extends AmqpServiceBase<HonoMessagingConfigProperties> {

    @Override
    protected String getServiceName() {
        return "Hono-Messaging";
    }

    @Autowired
    @Override
    public void setConfig(final HonoMessagingConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    @Override
    protected Future<Void> preStartServers() {

        checkStandardEndpointsAreRegistered();
        logStartupMessage();
        return Future.succeededFuture();
    }

    private void checkStandardEndpointsAreRegistered() {
        if (getEndpoint(TelemetryConstants.TELEMETRY_ENDPOINT) == null) {
            LOG.warn("no Telemetry endpoint has been configured, Hono Messaging will not support Telemetry API");
        }
        if (getEndpoint(EventConstants.EVENT_ENDPOINT) == null) {
            LOG.warn("no Event endpoint has been configured, Hono Messaging will not support Event API");
        }
    }

    private void logStartupMessage() {
        if (LOG.isWarnEnabled()) {
            StringBuilder b = new StringBuilder()
                    .append("Hono Messaging does not yet support limiting the incoming message size ")
                    .append("via the maxPayloadSize property");
            LOG.warn(b.toString());
        }
    }

    @Override
    protected void publishConnectionClosedEvent(final ProtonConnection con) {

        String conId = con.attachments().get(Constants.KEY_CONNECTION_ID, String.class);
        if (conId != null) {
            vertx.eventBus().publish(
                    Constants.EVENT_BUS_ADDRESS_CONNECTION_CLOSED,
                    conId);
        }
    }

    /**
     * Sets the session's window size to the value of configuration parameter <em>maxSessionWindow</em>.
     */
    @Override
    protected void handleSessionOpen(final ProtonConnection con, final ProtonSession session) {
        LOG.info("opening new session with client [name: {}, session window size: {}]", con.getRemoteContainer(), getConfig().getMaxSessionWindow());
        session.setIncomingCapacity(getConfig().getMaxSessionWindow());
        session.closeHandler(sessionResult -> {
            session.close();
        });
        session.open();
    }
}
