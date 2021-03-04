/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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

/**
 * A context for passing around parameters relevant for processing a {@code Command}.
 *
 * @deprecated Use {@code org.eclipse.hono.adapter.client.command.CommandContext} instead.
 */
@Deprecated
public class CommandContext extends MapBasedExecutionContext {

    /**
     * The key under which the current CommandContext is stored.
     */
    public static final String KEY_COMMAND_CONTEXT = "command-context";

    private static final Logger LOG = LoggerFactory.getLogger(CommandContext.class);

    private final Command command;
    private final ProtonDelivery delivery;

    private CommandContext(
            final Command command,
            final ProtonDelivery delivery,
            final Span span) {
        super(span);
        this.command = command;
        this.delivery = delivery;
    }

    /**
     * Creates a context for a command.
     *
     * @param command The command to be processed.
     * @param delivery The delivery corresponding to the message.
     * @param span The OpenTracing span to use for tracking the processing of the command.
     * @return The context.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static CommandContext from(
            final Command command,
            final ProtonDelivery delivery,
            final Span span) {

        Objects.requireNonNull(command);
        Objects.requireNonNull(delivery);
        Objects.requireNonNull(span);
        return new CommandContext(command, delivery, span);
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
     * Settles the command message with the <em>accepted</em> outcome.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getTracingSpan()}.
     */
    public void accept() {
        disposition(Accepted.getInstance());
    }

    /**
     * Settles the command message with the <em>released</em> outcome.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getTracingSpan()}.
     */
    public void release() {
        disposition(Released.getInstance());
    }

    /**
     * Settles the command message with the <em>modified</em> outcome.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getTracingSpan()}.
     *
     * @param deliveryFailed Whether the delivery should be treated as failed.
     * @param undeliverableHere Whether the delivery is considered undeliverable.
     */
    public void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
        final Modified modified = new Modified();
        modified.setDeliveryFailed(deliveryFailed);
        modified.setUndeliverableHere(undeliverableHere);
        disposition(modified);
    }

    /**
     * Settles the command message with the <em>rejected</em> outcome.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getTracingSpan()}.
     *
     * @param errorCondition The error condition to send in the disposition frame (may be {@code null}).
     */
    public void reject(final ErrorCondition errorCondition) {
        final Rejected rejected = new Rejected();
        if (errorCondition != null) {
            rejected.setError(errorCondition);
        }
        disposition(rejected);
    }

    /**
     * Settles the command message with the given {@code DeliveryState} outcome.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getTracingSpan()}.
     *
     * @param deliveryState The deliveryState to set in the disposition frame.
     * @throws NullPointerException if deliveryState is {@code null}.
     */
    public void disposition(final DeliveryState deliveryState) {

        Objects.requireNonNull(deliveryState);
        final Span span = getTracingSpan();
        if (delivery.isSettled()) {
            final String msg = String.format("cannot complete incoming delivery of command message with outcome '%s' - delivery already settled locally; local state: %s",
                    deliveryState, delivery.getLocalState());
            TracingHelper.logError(getTracingSpan(), msg);
            LOG.info("{} [{}]", msg, getCommand());
        } else {
            final boolean wasAlreadyRemotelySettled = delivery.remotelySettled();
            // if delivery is already settled remotely call "disposition" anyway to update local state and settle locally
            delivery.disposition(deliveryState, true);
            if (wasAlreadyRemotelySettled) {
                final String msg = String.format("cannot complete incoming delivery of command message with outcome '%s' - delivery already settled remotely; remote state: %s",
                        deliveryState, delivery.getRemoteState());
                TracingHelper.logError(getTracingSpan(), msg);
                LOG.info("{} [{}]", msg, getCommand());
            } else if (Accepted.class.isInstance(deliveryState)) {
                LOG.trace("accepted command message [{}]", getCommand());
                span.log("accepted command for device");

            } else if (Released.class.isInstance(deliveryState)) {
                LOG.debug("released command message [{}]", getCommand());
                TracingHelper.logError(span, "released command for device");

            } else if (Modified.class.isInstance(deliveryState)) {
                final Modified modified = (Modified) deliveryState;
                LOG.debug("modified command message [{}]", getCommand());
                TracingHelper.logError(span, "modified command for device"
                        + (Boolean.TRUE.equals(modified.getDeliveryFailed()) ? "; delivery failed" : "")
                        + (Boolean.TRUE.equals(modified.getUndeliverableHere()) ? "; undeliverable here" : ""));

            } else if (Rejected.class.isInstance(deliveryState)) {
                final ErrorCondition errorCondition = ((Rejected) deliveryState).getError();
                LOG.debug("rejected command message [error: {}, command: {}]", errorCondition, getCommand());
                TracingHelper.logError(span, "rejected command for device"
                        + ((errorCondition != null && errorCondition.getDescription() != null)
                                ? "; error: " + errorCondition.getDescription()
                                : ""));
            } else {
                LOG.warn("unexpected delivery state [{}] when settling command message [{}]", deliveryState, getCommand());
                TracingHelper.logError(span, "unexpected delivery state: " + deliveryState);
            }
        }
        span.finish();
    }
}
