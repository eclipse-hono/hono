/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.client.command;

import org.eclipse.hono.util.Lifecycle;

import io.vertx.core.Future;

/**
 * A client to send commands to the internal Command and Control API endpoint provided by protocol adapters.
 */
public interface InternalCommandSender extends Lifecycle {

    /**
     * Sends a command to the downstream peer, targeting the protocol adapter Command and Control API endpoint
     * identified by the given protocol adapter instance id.
     * <p>
     * Note that the result of the send operation is getting applied to the command context here.
     * That means implementations are required to invoke one of the given command context's
     * {@link CommandContext#accept()}, {@link CommandContext#release(Throwable)}, {@link CommandContext#modify(boolean, boolean)}
     * or {@link CommandContext#reject(String)} methods based on the outcome of sending the command to the
     * protocol adapter instance.
     *
     * @param commandContext The context of the command to send.
     * @param adapterInstanceId The protocol adapter instance id to send the command to.
     * @return A future that will be succeeded once the outcome of the send operation has been applied to
     *         the command context.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the message
     *         could not be sent or if no acknowledgement was received from the peer in time (if the
     *         transport protocol supports this).
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<Void> sendCommand(CommandContext commandContext, String adapterInstanceId);
}
