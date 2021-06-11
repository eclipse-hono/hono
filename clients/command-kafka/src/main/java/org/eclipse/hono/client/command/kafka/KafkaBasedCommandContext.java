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

package org.eclipse.hono.client.command.kafka;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponseSender;
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
    private final CommandResponseSender commandResponseSender;
    private String completedOutcome;

    /**
     * Creates a new command context.
     *
     * @param command The command to be processed.
     * @param span The OpenTracing span to use for tracking the processing of the command.
     * @param commandResponseSender The sender used to send a command response.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedCommandContext(final KafkaBasedCommand command, final Span span,
            final CommandResponseSender commandResponseSender) {
        super(span);
        this.command = Objects.requireNonNull(command);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
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
        if (!setCompleted("accepted")) {
            return;
        }
        final Span span = getTracingSpan();
        LOG.trace("accepted command message [{}]", getCommand());
        span.log("command for device handled with outcome 'accepted'");
        span.finish();
    }

    @Override
    public void release(final Throwable error) {
        Objects.requireNonNull(error);
        if (!setCompleted("released")) {
            return;
        }
        final Span span = getTracingSpan();
        TracingHelper.logError(span, "command for device handled with outcome 'released'");
        span.finish();
    }

    @Override
    public void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
        if (!setCompleted("modified")) {
            return;
        }
        final Span span = getTracingSpan();
        TracingHelper.logError(span, "command for device handled with outcome 'modified'"
                + (deliveryFailed ? "; delivery failed" : "")
                + (undeliverableHere ? "; undeliverable here" : ""));
        span.finish();
    }

    @Override
    public void reject(final String cause) {
        reject(HttpURLConnection.HTTP_BAD_REQUEST, cause);
    }

    @Override
    public void reject(final Throwable cause) {
        final int status = cause instanceof ClientErrorException
                ? ((ClientErrorException) cause).getErrorCode()
                : HttpURLConnection.HTTP_BAD_REQUEST;
        reject(status, cause.getMessage());
    }

    private void reject(final int status, final String cause) {
        if (!setCompleted("rejected")) {
            return;
        }
        final Span span = getTracingSpan();
        TracingHelper.logError(span, "command for device handled with outcome 'rejected'; error: " + cause);
        span.finish();
    }

    private boolean setCompleted(final String outcome) {
        if (completedOutcome != null) {
            LOG.warn("can't apply '{}' outcome, context already completed with '{}' outcome [{}]", outcome, completedOutcome, getCommand());
            return false;
        }
        completedOutcome = outcome;
        return true;
    }
}
