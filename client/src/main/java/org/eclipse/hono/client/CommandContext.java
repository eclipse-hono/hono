/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client;

import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;


/**
 * A context for passing around parameters relevant for processing a {@code Command}.
 *
 */
public class CommandContext extends MapBasedExecutionContext {

    /**
     * The key under which the current CommandContext is stored.
     */
    public static final String KEY_COMMAND_CONTEXT = "command-context";

    private static final Logger LOG = LoggerFactory.getLogger(CommandContext.class);

    private final Command command;
    private final ProtonDelivery delivery;
    private final Span currentSpan;

    private CommandContext(
            final Command command,
            final ProtonDelivery delivery,
            final Span currentSpan) {

        this.command = command;
        this.delivery = delivery;
        this.currentSpan = currentSpan;
        setTracingContext(currentSpan.context());
    }

    /**
     * Creates a context for a command.
     * 
     * @param command The command to be processed.
     * @param delivery The delivery corresponding to the message.
     * @param currentSpan The OpenTracing span to use for tracking the processing of the command.
     * @return The context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static CommandContext from(
            final Command command,
            final ProtonDelivery delivery,
            final Span currentSpan) {

        Objects.requireNonNull(command);
        Objects.requireNonNull(delivery);
        Objects.requireNonNull(currentSpan);
        return new CommandContext(command, delivery, currentSpan);
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
     * Gets the delivery corresponding to the command message.
     *
     * @return The delivery.
     */
    public ProtonDelivery getDelivery() {
        return delivery;
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
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getCurrentSpan()}.
     */
    public void accept() {
        LOG.trace("accepting command message [{}]", getCommand());
        ProtonHelper.accepted(delivery, true);
        currentSpan.log("accepted command for device");
        currentSpan.finish();
    }

    /**
     * Settles the command message with the <em>released</em> outcome.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getCurrentSpan()}.
     */
    public void release() {
        ProtonHelper.released(delivery, true);
        TracingHelper.logError(currentSpan, "released command for device");
        currentSpan.finish();
    }

    /**
     * Settles the command message with the <em>modified</em> outcome.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getCurrentSpan()}.
     *
     * @param deliveryFailed Whether the delivery should be treated as failed.
     * @param undeliverableHere Whether the delivery is considered undeliverable.
     */
    public void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
        ProtonHelper.modified(delivery, true, deliveryFailed, undeliverableHere);
        TracingHelper.logError(currentSpan, "modified command for device"
                + (deliveryFailed ? "; delivery failed" : "")
                + (undeliverableHere ? "; undeliverable here" : ""));
        currentSpan.finish();
    }

    /**
     * Settles the command message with the <em>rejected</em> outcome.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getCurrentSpan()}.
     * 
     * @param errorCondition The error condition to send in the disposition frame (may be {@code null}).
     */
    public void reject(final ErrorCondition errorCondition) {
        final Rejected rejected = new Rejected();
        if (errorCondition != null) {
            rejected.setError(errorCondition);
        }
        delivery.disposition(rejected, true);
        TracingHelper.logError(currentSpan, "rejected command for device"
                + ((errorCondition != null && errorCondition.getDescription() != null) ? "; error: " + errorCondition.getDescription() : ""));
        currentSpan.finish();
    }

    /**
     * Settles the command message with the given {@code DeliveryState} outcome.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getCurrentSpan()}.
     *
     * @param deliveryState The deliveryState to set in the disposition frame.
     * @throws NullPointerException if deliveryState is {@code null}.
     */
    public void disposition(final DeliveryState deliveryState) {

        Objects.requireNonNull(deliveryState);
        delivery.disposition(deliveryState, true);
        if (Accepted.class.isInstance(deliveryState)) {
            LOG.trace("accepted command message [{}]", getCommand());
            currentSpan.log("accepted command for device");

        } else if (Released.class.isInstance(deliveryState)) {
            LOG.debug("released command message [{}]", getCommand());
            TracingHelper.logError(currentSpan, "released command for device");

        } else if (Modified.class.isInstance(deliveryState)) {
            final Modified modified = (Modified) deliveryState;
            LOG.debug("modified command message [{}]", getCommand());
            TracingHelper.logError(currentSpan, "modified command for device"
                    + (Boolean.TRUE.equals(modified.getDeliveryFailed()) ? "; delivery failed" : "")
                    + (Boolean.TRUE.equals(modified.getUndeliverableHere()) ? "; undeliverable here" : ""));

        } else if (Rejected.class.isInstance(deliveryState)) {
            final ErrorCondition errorCondition = ((Rejected) deliveryState).getError();
            LOG.debug("rejected command message [error: {}, command: {}]", errorCondition, getCommand());
            TracingHelper.logError(currentSpan, "rejected command for device"
                    + ((errorCondition != null && errorCondition.getDescription() != null) ? "; error: " + errorCondition.getDescription() : ""));
        } else {
            LOG.warn("unexpected delivery state [{}] when settling command message [{}]", deliveryState, getCommand());
            TracingHelper.logError(currentSpan, "unexpected delivery state: " + deliveryState);
        }
        currentSpan.finish();
    }
}
