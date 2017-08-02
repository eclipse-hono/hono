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
package org.eclipse.hono.service.amqp;

import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A message endpoint providing an API that clients can interact with by means of AMQP 1.0 based message exchanges.
 *
 */
public interface Endpoint {

    /**
     * Gets the name of this endpoint.
     * <p>
     * The Hono server uses this name to determine the {@code Endpoint} implementation that
     * is responsible for handling requests to establish a link with a target address starting with this name.
     * </p>
     *  
     * @return the name.
     */
    String getName();

    /**
     * Handles a client's request to establish a link with Hono for sending messages to a given target address.
     * 
     * @param connection The AMQP connection that the link is part of.
     * @param receiver The link to be established.
     * @param targetAddress The (remote) target address from the client's AMQP <em>ATTACH</em> message.
     */
    void onLinkAttach(ProtonConnection connection, ProtonReceiver receiver, ResourceIdentifier targetAddress);

    /**
     * Handles a client's request to establish a link with Hono for receiving messages from a given address.
     *
     * @param connection The AMQP connection that the link is part of.
     * @param sender The link to be established.
     * @param sourceAddress The (remote) source address from the client's AMQP <em>ATTACH</em> message.
     */
    void onLinkAttach(ProtonConnection connection, ProtonSender sender, ResourceIdentifier sourceAddress);

    /**
     * Starts this endpoint.
     * <p>
     * This method should be used to allocate any required resources.
     * However, no long running tasks should be executed.
     * 
     * @param startFuture Completes if this endpoint has started successfully.
     */
    void start(Future<Void> startFuture);

    /**
     * Stops this endpoint.
     * <p>
     * This method should be used to release any allocated resources.
     * However, no long running tasks should be executed.
     * 
     * @param stopFuture Completes if this endpoint has stopped successfully.
     */
    void stop(Future<Void> stopFuture);

    /**
     * Registers checks to perform in order to determine whether this endpoint is ready to serve requests.
     * <p>
     * An external systems management component can get the result of running these checks by means
     * of doing a HTTP GET /readiness on the service component this endpoint belongs to.
     * 
     * @param handler The handler to register the checks with.
     */
    void registerReadinessChecks(final HealthCheckHandler handler);

    /**
     * Registers checks to perform in order to determine whether this endpoint is alive.
     * <p>
     * An external systems management component can get the result of running these checks by means
     * of doing a HTTP GET /liveness on the service component this endpoint belongs to.
     * 
     * @param handler The handler to register the checks with.
     */
    void registerLivenessChecks(final HealthCheckHandler handler);
}
