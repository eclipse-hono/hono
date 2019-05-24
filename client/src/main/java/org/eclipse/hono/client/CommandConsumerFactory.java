/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.impl.CommandConsumer;
import org.eclipse.hono.client.impl.CommandConsumerFactoryImpl;

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A factory for creating clients for the <em>AMQP 1.0 Messaging Network</em> to
 * receive commands and send responses.
 */
public interface CommandConsumerFactory extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to the AMQP network.
     * @param gatewayMapper The component mapping a command device id to the corresponding gateway device id.
     * @return The factory.
     * @throws NullPointerException if connection or gatewayMapper is {@code null}.
     */
    static CommandConsumerFactory create(final HonoConnection connection, final GatewayMapper gatewayMapper) {
        return new CommandConsumerFactoryImpl(connection, gatewayMapper);
    }

    /**
     * Creates a command consumer for a device.
     * <p>
     * For each device only one command consumer may be active at any given time.
     * It is the responsibility of the calling code to properly close a consumer
     * once it is no longer needed by invoking its {@link CommandConsumer#close(Handler)}
     * method.
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
     * once it is no longer needed by invoking its {@link CommandConsumer#close(Handler)}
     * method.
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
     * Gets a sender for sending command responses to a business application.
     * <p>
     * It is the responsibility of the calling code to properly close the
     * link by invoking {@link CommandResponseSender#close(Handler)}
     * once the sender is no longer needed anymore.
     * 
     * @param tenantId The ID of the tenant to send the command responses for.
     * @param replyId The ID used to build the reply address as {@code command_response/tenantId/replyId}.
     * @return A future that will complete with the sender once the link has been established.
     *         The future will be failed with a {@link ServiceInvocationException} if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<CommandResponseSender> getCommandResponseSender(String tenantId, String replyId);

    /**
     * Gets a sender for sending command responses to a business application.
     * <p>
     * This implementation uses the legacy command response address pattern with the <em>control</em> prefix.
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
     * @deprecated Use {@link #getCommandResponseSender(String, String)} instead.
     */
    @Deprecated
    Future<CommandResponseSender> getLegacyCommandResponseSender(String tenantId, String replyId);
}
