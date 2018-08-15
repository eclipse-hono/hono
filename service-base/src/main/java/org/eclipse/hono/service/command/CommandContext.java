/**
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
 */


package org.eclipse.hono.service.command;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;


/**
 * A context for passing around parameters relevant for processing a {@code Command}.
 *
 */
public final class CommandContext extends MapBasedExecutionContext {

    private static final Logger LOG = LoggerFactory.getLogger(CommandContext.class);

    private final Command command;
    private final ProtonDelivery delivery;
    private final ProtonReceiver receiver;
    private final Span currentSpan;

    private CommandContext(
            final Command command,
            final ProtonDelivery delivery,
            final ProtonReceiver receiver,
            final Span currentSpan) {

        this.command = command;
        this.delivery = delivery;
        this.receiver = receiver;
        this.currentSpan = currentSpan;
    }

    /**
     * Creates a context for a command.
     * 
     * @param command The command to be processed.
     * @param delivery The delivery corresponding to the message.
     * @param receiver The AMQP link over which the command has been received.
     * @param currentSpan The OpenTracing span to use for tracking the processing of the command.
     * @return The context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static CommandContext from(
            final Command command,
            final ProtonDelivery delivery,
            final ProtonReceiver receiver,
            final Span currentSpan) {

        Objects.requireNonNull(command);
        Objects.requireNonNull(delivery);
        Objects.requireNonNull(receiver);
        Objects.requireNonNull(currentSpan);
        return new CommandContext(command, delivery, receiver, currentSpan);
    }

    /**
     * Gets the command to process.
     * 
     * @return The command.
     */
    public Command getCommand() {
        return command;
    }

    /**
     * Gets the OpenTracing span to use for tracking the processing of the command.
     * 
     * @return The span.
     */
    public Span getCurrentSpan() {
        return currentSpan;
    }

    /**
     * Settles the command message with the <em>accepted</em> outcome.
     */
    public void accept() {

        LOG.trace("accepting command message [{}]", getCommand());
        ProtonHelper.accepted(delivery, true);
        currentSpan.log("accepted command for delivery to device");
    }

    /**
     * Settles the command message with the <em>released</em> outcome.
     */
    public void release() {
        ProtonHelper.released(delivery, true);
        TracingHelper.logError(currentSpan, "cannot process command");
    }

    /**
     * Settles the command message with the <em>rejected</em> outcome.
     * 
     * @param errorCondition The error condition to send in the disposition frame (may be {@code null}).
     */
    public void reject(final ErrorCondition errorCondition) {
        final Rejected rejected = new Rejected();
        final Map<String, String> items = new HashMap<>(2);
        items.put(Fields.EVENT, "cannot process command");
        if (errorCondition != null) {
            items.put(Fields.MESSAGE, errorCondition.getDescription());
            rejected.setError(errorCondition);
        }
        delivery.disposition(rejected, true);
        TracingHelper.logError(currentSpan, items);
    }

    /**
     * Issues credits to the peer that the has been received from.
     * 
     * @param credits The number of credits.
     * @throws IllegalArgumentException if credits is &lt; 1
     */
    public void flow(final int credits) {
        if (credits < 1) {
            throw new IllegalArgumentException("credits must be positve");
        }
        currentSpan.log(String.format("flowing %d credits to sender", credits));
        receiver.flow(credits);
    }

}
