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
 */
package org.eclipse.hono.messaging;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.*;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.event.EventConstants;
import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.service.amqp.Endpoint;
import org.eclipse.hono.service.auth.AuthenticationConstants;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;

import java.util.Objects;

/**
 * Hono Messaging is an AMQP 1.0 container that provides nodes for uploading <em>Telemetry</em> and
 * <em>Event</em> messages.
 */
@Component
@Scope("prototype")
public final class HonoMessaging extends AmqpServiceBase<HonoMessagingConfigProperties> {
    protected ConnectionFactory authenticationService;

    /**
     * Sets the factory to use for creating an AMQP 1.0 connection to
     * the Authentication service.
     *
     * @param factory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    @Autowired
    @Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION)
    public void setAuthenticationServiceConnectionFactory(final ConnectionFactory factory) {
        authenticationService = Objects.requireNonNull(factory);
    }

    @Override
    protected String getServiceName() {
        return "Hono";
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
     * Registers this service's endpoints' readiness checks.
     * <p>
     * This invokes {@link Endpoint#registerReadinessChecks(HealthCheckHandler)} for all registered endpoints
     * and it checks if the <em>Authentication Service</em> is connected.
     *
     * @param handler The health check handler to register the checks with.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        for (Endpoint ep : endpoints()) {
            ep.registerReadinessChecks(handler);
        }
        handler.register("authentication-service-connection", status -> {
            if (authenticationService == null) {
                status.complete(Status.KO(new JsonObject().put("error", "no connection factory set for Authentication service")));
            } else {
                LOG.debug("checking connection to Authentication service");
                authenticationService.connect(null, null, null, s -> {
                    if (s.succeeded()) {
                        s.result().close();
                        status.complete(Status.OK());
                    } else {
                        status.complete(Status.KO(new JsonObject().put("error", "cannot connect to Authentication service")));
                    }
                });
            }
        });
    }

}
