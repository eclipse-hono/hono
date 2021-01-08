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

package org.eclipse.hono.adapter.client.command.amqp;

import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.adapter.client.command.CommandContext;
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
    public Command getCommand() {
        return command;
    }

    @Override
    public void accept() {
        final Span span = getTracingSpan();
        LOG.trace("accepting command message [{}]", getCommand());
        ProtonHelper.accepted(delivery, true);
        span.log("accepted command for device");
        span.finish();
    }

    @Override
    public void release() {
        final Span span = getTracingSpan();
        ProtonHelper.released(delivery, true);
        TracingHelper.logError(span, "released command for device");
        span.finish();
    }

    @Override
    public void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
        final Span span = getTracingSpan();
        ProtonHelper.modified(delivery, true, deliveryFailed, undeliverableHere);
        TracingHelper.logError(span, "modified command for device"
                + (deliveryFailed ? "; delivery failed" : "")
                + (undeliverableHere ? "; undeliverable here" : ""));
        span.finish();
    }

    @Override
    public void reject(final String cause) {
        final ErrorCondition errorCondition = ProtonHelper.condition(Constants.AMQP_BAD_REQUEST, cause);
        final Span span = getTracingSpan();
        final Rejected rejected = new Rejected();
        rejected.setError(errorCondition);
        delivery.disposition(rejected, true);
        TracingHelper.logError(span, "rejected command for device"
                + (errorCondition.getDescription() != null ? "; error: " + errorCondition.getDescription() : ""));
        span.finish();
    }

}
