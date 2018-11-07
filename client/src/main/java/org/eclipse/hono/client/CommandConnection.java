/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.client;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.eclipse.hono.client.impl.CommandConnectionImpl;
import org.eclipse.hono.config.ClientConfigProperties;

/**
 * A bidirectional connection between a <em>Protocol Adapter</em> and the
 * <em>AMQP 1.0 Messaging Network</em> to receive commands and send
 * responses.
 */
public interface CommandConnection extends HonoClient {

    /**
     * Creates a command consumer for a device.
     * <p>
     * For each device only one command consumer may be active at any given time.
     * It is the responsibility of the calling code to properly close a consumer
     * once it is no longer needed. The preferred way of doing so is to invoke the
     * instance's {@link CommandConsumer#close(Handler)} method. Alternatively, if
     * no reference to the instance is held, the {@link #closeCommandConsumer(String, String)}
     * method can be used instead.
     * 
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @param commandHandler The handler to invoke with every command received.
     * @param remoteCloseHandler A handler to be invoked after the link has been closed
     *                     at the peer's request or {@code null} if no handler should
     *                     be invoked.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be completed with the newly created consumer once the link
     *         has been established.
     *         <p>
     *         The future will be failed with
     *         <ul>
     *         <li>a {@link ResourceConflictException} if there already is
     *         a command consumer active for the given device</li>
     *         <li>a {@link ServiceInvocationException} with an error code indicating
     *         the cause of the failure</li>
     *         </ul>
     * @throws NullPointerException if any of tenant, device ID or command handler are {@code null}.
     */
    Future<MessageConsumer> createCommandConsumer(
            String tenantId,
            String deviceId,
            Handler<CommandContext> commandHandler,
            Handler<Void> remoteCloseHandler);

    /**
     * Creates a command consumer for a device.
     * <p>
     * For each device only one command consumer may be active at any given time.
     * It is the responsibility of the calling code to properly close a consumer
     * once it is no longer needed. The preferred way of doing so is to invoke the
     * instance's {@link CommandConsumer#close(Handler)} method. Alternatively, if
     * no reference to the instance is held, the {@link #closeCommandConsumer(String, String)}
     * method can be used instead.
     * <p>
     * The underlying link for receiving the commands will be checked periodically
     * after the given number of milliseconds. If the link is no longer active, e.g.
     * because the underlying connection to the peer has been lost or the peer has
     * closed the link, then this client will try to re-establish the link using the
     * given parameters.
     * 
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @param commandHandler The handler to invoke with every command received.
     * @param remoteCloseHandler A handler to be invoked after the link has been closed
     *                     at the peer's request.
     * @param livenessCheckInterval The number of milliseconds to wait between checking
     *                              liveness of the created link. If the check fails,
     *                              an attempt will be made to re-establish the link.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be completed with the newly created consumer once the link
     *         has been established.
     *         <p>
     *         The future will be failed with
     *         <ul>
     *         <li>a {@link ResourceConflictException} if there already is
     *         a command consumer active for the given device</li>
     *         <li>a {@link ServiceInvocationException} with an error code indicating
     *         the cause of the failure</li>
     *         </ul>
     * @throws NullPointerException if tenant, device ID or command handler are {@code null}.
     * @throws IllegalArgumentException if the checkInterval is negative.
     */
    Future<MessageConsumer> createCommandConsumer(
            String tenantId,
            String deviceId,
            Handler<CommandContext> commandHandler,
            Handler<Void> remoteCloseHandler,
            long livenessCheckInterval);

    /**
     * Closes the command consumer for a given device.
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenantId or deviceId are {@code null}.
     */
    Future<Void> closeCommandConsumer(String tenantId, String deviceId);

    /**
     * Gets a sender for sending command responses to a business application.
     * <p>
     * It is the responsibility of the calling code to properly close the
     * link by invoking {@link CommandResponseSender#close(Handler)}
     * once the sender is no longer needed anymore.
     * 
     * @param tenantId The ID of the tenant to send the command responses for.
     * @param replyId The ID used to build the reply address as {@code control/tenantId/replyId}.
     * @return A future that will complete with the sender once the link has been established.
     *         The future will be failed with a {@link ServiceInvocationException} if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<CommandResponseSender> getCommandResponseSender(String tenantId, String replyId);

    /**
     * Creates a new client for a set of configuration properties.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param clientConfigProperties The configuration properties to use.
     *
     * @return CommandConnection The client that was created.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    static CommandConnection newConnection(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        return new CommandConnectionImpl(vertx, clientConfigProperties);
    }
}
