/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command.amqp;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * A context for passing around parameters relevant for processing a {@code Command} used in a vertx-proton based
 * client.
 */
public class ProtonBasedCommandContext extends MapBasedExecutionContext implements CommandContext {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedCommandContext.class);

    private final ProtonBasedCommand command;
    private final ProtonDelivery delivery;

    private String completedOutcome;

    /**
     * Creates a new command context.
     *
     * @param command The command to be processed.
     * @param delivery The delivery corresponding to the message.
     * @param span The OpenTracing span to use for tracking the processing of the command.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedCommandContext(final ProtonBasedCommand command, final ProtonDelivery delivery, final Span span) {
        super(span);
        this.command = Objects.requireNonNull(command);
        this.delivery = Objects.requireNonNull(delivery);
    }

    /**
     * Checks if the context has already been completed.
     *
     * @return {@code true} if the context has already been completed.
     */
    @Override
    public final boolean isCompleted() {
        return completedOutcome != null;
    }

    @Override
    public void logCommandToSpan(final Span span) {
        command.logToSpan(span);
    }

    @Override
    public ProtonBasedCommand getCommand() {
        return command;
    }

    @Override
    public void accept() {
        if (!setCompleted(OUTCOME_ACCEPTED)) {
            return;
        }
        Tags.HTTP_STATUS.set(getTracingSpan(), HttpURLConnection.HTTP_ACCEPTED);
        updateDelivery(Accepted.getInstance());
    }

    @Override
    public void release() {
        if (!setCompleted(OUTCOME_RELEASED)) {
            return;
        }
        TracingHelper.logError(getTracingSpan(), "command could not be delivered or processed");
        Tags.HTTP_STATUS.set(getTracingSpan(), HttpURLConnection.HTTP_UNAVAILABLE);
        updateDelivery(Released.getInstance());
    }

    @Override
    public void release(final Throwable error) {
        Objects.requireNonNull(error);
        if (!setCompleted(OUTCOME_RELEASED)) {
            return;
        }
        TracingHelper.logError(getTracingSpan(), "command could not be delivered or processed", error);
        final int status = ServiceInvocationException.extractStatusCode(error);
        Tags.HTTP_STATUS.set(getTracingSpan(), status);
        updateDelivery(Released.getInstance());
    }

    @Override
    public void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
        if (!setCompleted(OUTCOME_MODIFIED)) {
            return;
        }
        final Span span = getTracingSpan();
        TracingHelper.logError(span, "command for device handled with outcome 'modified'"
                + (deliveryFailed ? "; delivery failed" : "")
                + (undeliverableHere ? "; undeliverable here" : ""));
        final int status = undeliverableHere ? HttpURLConnection.HTTP_NOT_FOUND
                : HttpURLConnection.HTTP_UNAVAILABLE;
        Tags.HTTP_STATUS.set(span, status);
        final Modified modified = new Modified();
        modified.setDeliveryFailed(deliveryFailed);
        modified.setUndeliverableHere(undeliverableHere);
        updateDelivery(modified);
    }

    @Override
    public void reject(final String error) {
        if (!setCompleted(OUTCOME_REJECTED)) {
            return;
        }
        TracingHelper.logError(getTracingSpan(), "client error trying to deliver or process command: " + error);
        Tags.HTTP_STATUS.set(getTracingSpan(), HttpURLConnection.HTTP_BAD_REQUEST);
        final ErrorCondition errorCondition = ProtonHelper.condition(AmqpUtils.AMQP_BAD_REQUEST, error);
        final Rejected rejected = new Rejected();
        rejected.setError(errorCondition);
        updateDelivery(rejected);
    }

    @Override
    public void reject(final Throwable error) {
        if (!setCompleted(OUTCOME_REJECTED)) {
            return;
        }
        TracingHelper.logError(getTracingSpan(), "client error trying to deliver or process command", error);
        final int status = error instanceof ClientErrorException
                ? ((ClientErrorException) error).getErrorCode()
                : HttpURLConnection.HTTP_BAD_REQUEST;
        Tags.HTTP_STATUS.set(getTracingSpan(), status);
        reject(ServiceInvocationException.getErrorMessageForExternalClient(error));
    }

    private void updateDelivery(final DeliveryState deliveryState) {
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
            } else if (deliveryState instanceof Accepted) {
                LOG.trace("accepted command message [{}]", getCommand());
                span.log("accepted command for device");

            } else if (deliveryState instanceof Released) {
                LOG.debug("released command message [{}]", getCommand());
                TracingHelper.logError(span, "released command for device");

            } else if (deliveryState instanceof Modified modified) {
                LOG.debug("modified command message [{}]", getCommand());
                TracingHelper.logError(span, "modified command for device"
                        + (Boolean.TRUE.equals(modified.getDeliveryFailed()) ? "; delivery failed" : "")
                        + (Boolean.TRUE.equals(modified.getUndeliverableHere()) ? "; undeliverable here" : ""));

            } else if (deliveryState instanceof Rejected rejected) {
                final ErrorCondition errorCondition = rejected.getError();
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

    private boolean setCompleted(final String outcome) {
        if (completedOutcome != null) {
            LOG.warn("can't apply '{}' outcome, context already completed with '{}' outcome [{}]",
                    outcome, completedOutcome, getCommand());
            return false;
        }
        completedOutcome = outcome;
        return true;
    }
}
