/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.registration;

import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonSession;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.service.amqp.Endpoint;
import org.eclipse.hono.service.auth.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Objects;
import java.util.UUID;


/**
 * A server that implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a> and
 * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a> as the default implementation of Hono's device registry.
 *
 * TODO: add metrics.
 * TODO: refactor this into a more generic server class (eliminate device registry specific parts).
 */
public class BaseDeviceRegistryServer<T extends ServiceConfigProperties> extends AmqpServiceBase<T> {
    private ConnectionFactory authenticationService;

    /**
     * Sets the factory to use for creating an AMQP 1.0 connection to
     * the Authentication service.
     *
     * @param factory The factory.
     */
    @Autowired
    @Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION)
    public void setAuthenticationServiceConnectionFactory(final ConnectionFactory factory) {
        authenticationService = Objects.requireNonNull(factory);
    }

    /**
     * Configures the AMQP connection for the secure port and provides a basic connection handling.
     * Subclasses may override this method to set custom handlers.
     *
     * @param connection The AMQP connection that the frame is supposed to establish.
     */
    @Override
    protected void onRemoteConnectionOpen(final ProtonConnection connection) {
        connection.setContainer(String.format("Hono-DeviceRegistry-%s:%d", getBindAddress(), getPort()));
        setRemoteConnectionOpenHandler(connection);
    }

    /**
     * Configures the AMQP connection for the insecure port and provides a basic connection handling.
     * Subclasses may override this method to set custom handlers.
     *
     * @param connection The AMQP connection that the frame is supposed to establish.
     * @throws NullPointerException if connection is {@code null}.
     */
    @Override
    protected void onRemoteConnectionOpenInsecurePort(final ProtonConnection connection) {
        connection.setContainer(String.format("Hono-DeviceRegistry-%s:%d", getInsecurePortBindAddress(), getInsecurePort()));
        setRemoteConnectionOpenHandler(connection);
    }

    private void setRemoteConnectionOpenHandler(final ProtonConnection connection) {
        connection.sessionOpenHandler(remoteOpenSession -> handleSessionOpen(connection, remoteOpenSession));
        connection.receiverOpenHandler(remoteOpenReceiver -> handleReceiverOpen(connection, remoteOpenReceiver));
        connection.senderOpenHandler(remoteOpenSender -> handleSenderOpen(connection, remoteOpenSender));
        connection.disconnectHandler(this::handleRemoteDisconnect);
        connection.closeHandler(remoteClose -> handleRemoteConnectionClose(connection, remoteClose));
        connection.openHandler(remoteOpen -> {
            LOG.info("client [container: {}, user: {}] connected", connection.getRemoteContainer(), Constants.getClientPrincipal(connection).getName());
            connection.open();
            // attach an ID so that we can later inform downstream components when connection is closed
            connection.attachments().set(Constants.KEY_CONNECTION_ID, String.class, UUID.randomUUID().toString());
        });
    }

    private void handleSessionOpen(final ProtonConnection con, final ProtonSession session) {
        LOG.info("opening new session with client [{}]", con.getRemoteContainer());
        session.closeHandler(sessionResult -> {
            if (sessionResult.succeeded()) {
                sessionResult.result().close();
            }
        }).open();
    }

    /**
     * Invoked when a client closes the connection with this server.
     *
     * @param con The connection to close.
     * @param res The client's close frame.
     */
    private void handleRemoteConnectionClose(final ProtonConnection con, final AsyncResult<ProtonConnection> res) {
        if (res.succeeded()) {
            LOG.info("client [{}] closed connection", con.getRemoteContainer());
        } else {
            LOG.info("client [{}] closed connection with error", con.getRemoteContainer(), res.cause());
        }
        con.close();
        con.disconnect();
    }

    private void handleRemoteDisconnect(final ProtonConnection connection) {
        LOG.info("client [{}] disconnected", connection.getRemoteContainer());
        connection.disconnect();
    }

    /**
     * Registers this service's endpoints' readiness checks.
     * <p>
     * This invokes {@link Endpoint#registerReadinessChecks(HealthCheckHandler)} for all registered endpoints
     * and it checks if the <em>Authentication Service</em> is connected.
     * <p>
     * Subclasses should override this method to register more specific checks.
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
