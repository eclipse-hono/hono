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
package org.eclipse.hono.service.amqp;

import static java.net.HttpURLConnection.HTTP_OK;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Base class for Hono endpoints.
 * 
 * @param <T> The type of configuration properties this endpoint understands.
 */
public abstract class BaseEndpoint<T extends ServiceConfigProperties> implements Endpoint {

    protected final Vertx                       vertx;
    protected final Logger                      logger               = LoggerFactory.getLogger(getClass());
    protected T                                 config               = (T) new ServiceConfigProperties();
    private static final String                 STATUS_OK            = String.valueOf(HTTP_OK);
    private Map<String, UpstreamReceiverImpl>   activeClients        = new HashMap<>();

    /**
     * Creates an endpoint for a Vertx instance.
     * 
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    protected BaseEndpoint(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Sets configuration properties.
     * 
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Autowired(required = false)
    public final void setConfiguration(final T props) {
        this.config = Objects.requireNonNull(props);
    }

    @Override
    public final void start(final Future<Void> startFuture) {
        if (vertx == null) {
            startFuture.fail("Vert.x instance must be set");
        } else {
            doStart(startFuture);
        }
    }

    /**
     * Subclasses should override this method to create required resources
     * during startup.
     * <p>
     * This implementation always completes the start future.
     * 
     * @param startFuture Completes if startup succeeded.
     */
    protected void doStart(final Future<Void> startFuture) {
        startFuture.complete();
    }

    @Override
    public final void stop(final Future<Void> stopFuture) {
        doStop(stopFuture);
    }

    /**
     * Subclasses should override this method to release resources
     * during shutdown.
     * <p>
     * This implementation always completes the stop future.
     * 
     * @param stopFuture Completes if shutdown succeeded.
     */
    protected void doStop(final Future<Void> stopFuture) {
        stopFuture.complete();
    }

    /**
     * Closes the link to an upstream client and removes all state kept for it.
     * 
     * @param client The client to detach.
     */
    protected final void onLinkDetach(final UpstreamReceiver client) {
        onLinkDetach(client, null);
    }

    /**
     * Closes the link to an upstream client and removes all state kept for it.
     * 
     * @param client The client to detach.
     * @param error The error condition to convey to the client when closing the link.
     */
    protected final void onLinkDetach(final UpstreamReceiver client, final ErrorCondition error) {
        if (error == null) {
            logger.debug("closing receiver for client [{}]", client.getLinkId());
        } else {
            logger.debug("closing receiver for client [{}]: {}", client.getLinkId(), error.getDescription());
        }
        client.close(error);
        removeClientLink(client.getLinkId());
    }

    /**
     * Registers a link with an upstream client.
     * 
     * @param link The link to register.
     */
    protected final void registerClientLink(final UpstreamReceiverImpl link) {
        activeClients.put(link.getLinkId(), link);
    }

    /**
     * Looks up a link with an upstream client based on its identifier.
     * 
     * @param linkId The identifier of the client.
     * @return The link object representing the client or {@code null} if no link with the given identifier exists.
     * @throws NullPointerException if the link id is {@code null}.
     */
    protected final UpstreamReceiver getClientLink(final String linkId) {
        return activeClients.get(Objects.requireNonNull(linkId));
    }

    /**
     * Deregisters a link with an upstream client.
     * 
     * @param linkId The identifier of the link to deregister.
     */
    protected final void removeClientLink(final String linkId) {
        activeClients.remove(linkId);
    }

    @Override
    public void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
        logger.info("Endpoint [{}] does not support data upload, closing link.", getName());
        receiver.setCondition(ProtonHelper.condition(AmqpError.NOT_IMPLEMENTED, "resource cannot be written to"));
        receiver.close();
    }

    @Override
    public void onLinkAttach(final ProtonSender sender, final ResourceIdentifier targetResource) {
        logger.info("Endpoint [{}] does not support data retrieval, closing link.", getName());
        sender.setCondition(ProtonHelper.condition(AmqpError.NOT_IMPLEMENTED, "resource cannot be read from"));
        sender.close();
    }
}
