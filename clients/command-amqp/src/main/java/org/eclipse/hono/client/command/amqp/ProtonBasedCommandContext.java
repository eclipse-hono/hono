/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
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
        updateDelivery(Accepted.getInstance());
    }

    @Override
    public void release() {
        updateDelivery(Released.getInstance());
    }

    @Override
    public void release(final Throwable error) {
        Objects.requireNonNull(error);
        updateDelivery(Released.getInstance());
    }

    @Override
    public void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
        final Modified modified = new Modified();
        modified.setDeliveryFailed(deliveryFailed);
        modified.setUndeliverableHere(undeliverableHere);
        updateDelivery(modified);
    }

    @Override
    public void reject(final String cause) {
        final ErrorCondition errorCondition = ProtonHelper.condition(Constants.AMQP_BAD_REQUEST, cause);
        final Rejected rejected = new Rejected();
        rejected.setError(errorCondition);
        updateDelivery(rejected);
    }

    @Override
    public void reject(final Throwable error) {
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
