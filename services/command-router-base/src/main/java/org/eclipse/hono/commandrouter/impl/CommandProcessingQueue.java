/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl;

import java.util.function.Supplier;

import org.eclipse.hono.client.command.CommandContext;

import io.vertx.core.Future;

/**
 * Queue with the commands currently being processed.
 * <p>
 * The final step of processing a command, forwarding it to its target, is invoked here maintaining the
 * order of the incoming commands.
 *
 * @param <T> The type of command context of the commands added here.
 */
public interface CommandProcessingQueue<T extends CommandContext> {

    /**
     * Adds the command represented by the given command context.
     *
     * @param commandContext The context containing the command to add.
     * @throws NullPointerException if commandContext is {@code null}.
     */
    void add(T commandContext);

    /**
     * Removes the command represented by the given command context.
     * <p>
     * To be used for commands for which processing resulted in an error
     * and {@link #applySendCommandAction(CommandContext, Supplier)}
     * will not be invoked.
     *
     * @param commandContext The context containing the command to remove.
     * @return {@code true} if the command was removed.
     */
    boolean remove(T commandContext);

    /**
     * Either invokes the given sendAction directly if it is next-in-line or makes sure the action is
     * invoked at a later point in time according to the order in which commands were originally received.
     *
     * @param commandContext     The context of the command to apply the given sendAction for.
     * @param sendActionSupplier The Supplier for the action to send the given command to the internal Command and
     *                           Control API endpoint provided by protocol adapters.
     * @return A future indicating the outcome of sending the command message. If the given command is
     * not in the corresponding queue, a failed future will be returned and the command context is released.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<Void> applySendCommandAction(T commandContext, Supplier<Future<Void>> sendActionSupplier);

    /**
     * Clears the queue and releases all corresponding commands.
     */
    void clear();
}
