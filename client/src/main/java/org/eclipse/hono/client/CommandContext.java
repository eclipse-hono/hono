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


package org.eclipse.hono.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;


/**
 * A context for passing around parameters relevant for processing a {@code Command}.
 *
 */
public final class CommandContext extends MapBasedExecutionContext {

    /**
     * The key under which the current CommandContext is stored.
     */
    public static final String KEY_COMMAND_CONTEXT = "command-context";

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
     * Gets the AMQP link over which the command has been received.
     *
     * @return The receiver.
     */
    public ProtonReceiver getReceiver() {
        return receiver;
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
     * This method simply invokes {@link CommandContext#accept(int)} with
     * 0 credits.
     */
    public void accept() {

        accept(0);
    }

    /**
     * Settles the command message with the <em>accepted</em> outcome
     * and flows credit to the peer.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getCurrentSpan()}.
     * 
     * @param credit The number of credits to flow to the peer.
     * @throws IllegalArgumentException if credit is negative.
     */
    public void accept(final int credit) {

        if (credit < 0) {
            throw new IllegalArgumentException("credit must be >= 0");
        }
        LOG.trace("accepting command message [{}]", getCommand());
        ProtonHelper.accepted(delivery, true);
        currentSpan.log("accepted command for device");
        if (credit > 0) {
            flow(credit);
        }
        currentSpan.finish();
    }

    /**
     * Settles the command message with the <em>released</em> outcome.
     * <p>
     * This method simply invokes {@link CommandContext#release(int)} with
     * 0 credits.
     */
    public void release() {
        release(0);
    }

    /**
     * Settles the command message with the <em>released</em> outcome
     * and flows credit to the peer.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getCurrentSpan()}.
     * 
     * @param credit The number of credits to flow to the peer.
     * @throws IllegalArgumentException if credit is negative.
     */
    public void release(final int credit) {

        if (credit < 0) {
            throw new IllegalArgumentException("credit must be >= 0");
        }
        ProtonHelper.released(delivery, true);
        currentSpan.log("released command for device");
        currentSpan.log(Tags.ERROR.getKey());
        if (credit > 0) {
            flow(credit);
        }
        currentSpan.finish();
    }

    /**
     * Settles the command message with the <em>modified</em> outcome
     * and flows credit to the peer.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getCurrentSpan()}.
     *
     * @param deliveryFailed Whether the delivery should be treated as failed.
     * @param undeliverableHere Whether the delivery is considered undeliverable.
     * @param credit The number of credits to flow to the peer.
     * @throws IllegalArgumentException if credit is negative.
     */
    public void modify(final boolean deliveryFailed, final boolean undeliverableHere, final int credit) {

        if (credit < 0) {
            throw new IllegalArgumentException("credit must be >= 0");
        }
        ProtonHelper.modified(delivery, true, deliveryFailed, undeliverableHere);
        currentSpan.log("modified command for device");
        currentSpan.log(Tags.ERROR.getKey());
        if (credit > 0) {
            flow(credit);
        }
        currentSpan.finish();
    }

    /**
     * Settles the command message with the <em>rejected</em> outcome.
     * <p>
     * This method simply invokes {@link CommandContext#reject(ErrorCondition, int)}
     * with 0 credits.
     * 
     * @param errorCondition The error condition to send in the disposition frame (may be {@code null}).
     */
    public void reject(final ErrorCondition errorCondition) {

        reject(errorCondition, 0);
    }

    /**
     * Settles the command message with the <em>rejected</em> outcome
     * and flows credit to the peer.
     * <p>
     * This method also finishes the OpenTracing span returned by
     * {@link #getCurrentSpan()}.
     * 
     * @param errorCondition The error condition to send in the disposition frame (may be {@code null}).
     * @param credit The number of credits to flow to the peer.
     * @throws IllegalArgumentException if credit is negative.
     */
    public void reject(final ErrorCondition errorCondition, final int credit) {

        final Rejected rejected = new Rejected();
        final Map<String, Object> items = new HashMap<>(2);
        items.put(Fields.EVENT, "rejected command for device");
        if (errorCondition != null) {
            rejected.setError(errorCondition);
            Optional.ofNullable(errorCondition.getDescription()).ifPresent(s -> items.put(Fields.MESSAGE, s));
        }
        TracingHelper.logError(currentSpan, items);
        delivery.disposition(rejected, true);
        if (credit > 0) {
            flow(credit);
        }
        currentSpan.finish();
    }

    /**
     * Issues credits to the peer that the command has been received from.
     * 
     * @param credits The number of credits.
     * @throws IllegalArgumentException if credits is &lt; 1
     */
    private void flow(final int credits) {
        if (credits < 1) {
            throw new IllegalArgumentException("credits must be positive");
        }
        currentSpan.log(String.format("flowing %d credits to sender", credits));
        receiver.flow(credits);
    }
}
