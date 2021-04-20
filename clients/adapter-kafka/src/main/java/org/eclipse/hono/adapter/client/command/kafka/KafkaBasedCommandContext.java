/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter.client.command.kafka;

import java.util.Objects;

import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;

/**
 * A context for passing around parameters relevant for processing a {@code Command} used in a Kafka based
 * client.
 */
public class KafkaBasedCommandContext extends MapBasedExecutionContext implements CommandContext {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedCommandContext.class);

    private final KafkaBasedCommand command;

    /**
     * Creates a new command context.
     *
     * @param command The command to be processed.
     * @param span The OpenTracing span to use for tracking the processing of the command.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedCommandContext(final KafkaBasedCommand command, final Span span) {
        super(span);
        this.command = Objects.requireNonNull(command);
    }

    @Override
    public void logCommandToSpan(final Span span) {
        command.logToSpan(span);
    }

    @Override
    public KafkaBasedCommand getCommand() {
        return command;
    }

    @Override
    public void accept() {
        final Span span = getTracingSpan();
        LOG.trace("accepted command message [{}]", getCommand());
        span.log("command for device handled with outcome 'accepted'");
        span.finish();
    }

    @Override
    public void release() {
        final Span span = getTracingSpan();
        TracingHelper.logError(span, "command for device handled with outcome 'released'");
        span.finish();
    }

    @Override
    public void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
        final Span span = getTracingSpan();
        TracingHelper.logError(span, "command for device handled with outcome 'modified'"
                + (deliveryFailed ? "; delivery failed" : "")
                + (undeliverableHere ? "; undeliverable here" : ""));
        span.finish();
    }

    @Override
    public void reject(final String cause) {
        final Span span = getTracingSpan();
        TracingHelper.logError(span, "command for device handled with outcome 'rejected'; error: " + cause);
        span.finish();
    }

}
