/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.command;

import org.eclipse.hono.util.ExecutionContext;

import io.opentracing.Span;

/**
 * A context for processing a command that is targeted at a device.
 *
 */
public interface CommandContext extends ExecutionContext {

    /**
     * The key under which the current CommandContext is stored in an ExecutionContext container.
     */
    String KEY_COMMAND_CONTEXT = "command-context";

    /**
     * Logs information about the command.
     *
     * @param span The span to log to.
     * @throws NullPointerException if span is {@code null}.
     */
    void logCommandToSpan(Span span);

    /**
     * Gets the command to process.
     *
     * @return The command.
     */
    Command getCommand();

    /**
     * Indicates to the sender that the command message has been delivered to its target.
     */
    void accept();

    /**
     * Indicates to the sender that the command message could not be delivered to its target due to
     * reasons that are not the responsibility of the sender of the command.
     */
    void release();

    /**
     * Indicates to the sender that the command message could not be delivered to its target due to
     * reasons that are not the responsibility of the sender of the command.
     *
     * @param deliveryFailed {@code true} if the attempt to send the command to the target device
     *                       has failed.
     * @param undeliverableHere {@code true} if the component processing the context
     *                          has no access to the command's target device.
     */
    void modify(boolean deliveryFailed, boolean undeliverableHere);

    /**
     * Indicates to the sender that the command message cannot be delivered to its target due to
     * reasons that are the responsibility of the sender of the command.
     * <p>
     * The reason for a command being rejected often is that the command is invalid, e.g. lacking a
     * subject or having a malformed address.
     *
     * @param cause The error that caused he command to be rejected or {@code null} if the cause is unknown.
     */
    void reject(String cause);
}
