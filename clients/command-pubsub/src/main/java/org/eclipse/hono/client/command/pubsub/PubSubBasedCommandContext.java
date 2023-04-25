/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.command.pubsub;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.AbstractCommandContext;
import org.eclipse.hono.client.command.CommandAlreadyProcessedException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.CommandToBeReprocessedException;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessagingType;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

/**
 * A context for passing around parameters relevant for processing a {@code Command} used in a Pub/Sub based client.
 */
public class PubSubBasedCommandContext extends AbstractCommandContext<PubSubBasedCommand> implements CommandContext {

    /**
     * Creates a new command context.
     *
     * @param command The command to be processed.
     * @param commandResponseSender The sender used to send a command response.
     * @param span The OpenTracing span to use for tracking the processing of the command.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    public PubSubBasedCommandContext(
            final PubSubBasedCommand command,
            final CommandResponseSender commandResponseSender,
            final Span span) {
        super(span, command, commandResponseSender);
    }

    @Override
    public void release(final Throwable error) {
        Objects.requireNonNull(error);
        if (!setCompleted(OUTCOME_RELEASED)) {
            return;
        }
        final Span span = getTracingSpan();
        TracingHelper.logError(span, "command could not be delivered or processed", error);
        final ServiceInvocationException mappedError = StatusCodeMapper.toServerError(error);
        final int status = mappedError.getErrorCode();
        Tags.HTTP_STATUS.set(span, status);
        if (isRequestResponseCommand() && !(error instanceof CommandAlreadyProcessedException)
                && !(error instanceof CommandToBeReprocessedException)) {
            final String errorMessage = Optional
                    .ofNullable(ServiceInvocationException.getErrorMessageForExternalClient(mappedError))
                    .orElse("Temporarily unavailable");
            final String correlationId = getCorrelationId();
            sendDeliveryFailureCommandResponseMessage(status, errorMessage, span, error, correlationId,
                    MessagingType.pubsub)
                            .onComplete(v -> span.finish());
        } else {
            span.finish();
        }
    }

    @Override
    public void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
        if (!setCompleted(OUTCOME_MODIFIED)) {
            return;
        }

        final String deliveryFailedReason = deliveryFailed ? "; delivery failed" : "";
        final String undeliverableHereReason = undeliverableHere ? "; undeliverable here" : "";
        final int status = undeliverableHere ? HttpURLConnection.HTTP_NOT_FOUND : HttpURLConnection.HTTP_UNAVAILABLE;

        final Span span = getTracingSpan();
        TracingHelper.logError(span, String.format("command for device handled with outcome 'modified' %s %s",
                deliveryFailedReason, undeliverableHereReason));
        Tags.HTTP_STATUS.set(span, status);
        if (isRequestResponseCommand()) {
            final String error = String.format("command not processed %s %s", deliveryFailedReason,
                    undeliverableHereReason);
            final String correlationId = getCorrelationId();
            sendDeliveryFailureCommandResponseMessage(status, error, span, null, correlationId, MessagingType.pubsub)
                    .onComplete(v -> span.finish());
        } else {
            span.finish();
        }
    }

    @Override
    public void reject(final Throwable error) {
        if (!setCompleted(OUTCOME_REJECTED)) {
            return;
        }
        final int status = error instanceof ClientErrorException clientErrorException
                ? clientErrorException.getErrorCode()
                : HttpURLConnection.HTTP_BAD_REQUEST;
        TracingHelper.logError(getTracingSpan(), "client error trying to deliver or process command", error);
        final Span span = getTracingSpan();
        Tags.HTTP_STATUS.set(span, status);
        if (isRequestResponseCommand()) {
            final String nonNullCause = Optional.ofNullable(error.getMessage()).orElse("Command message rejected");
            final String correlationId = getCorrelationId();
            sendDeliveryFailureCommandResponseMessage(status, nonNullCause, span, null, correlationId,
                    MessagingType.pubsub)
                            .onComplete(v -> span.finish());
        } else {
            span.finish();
        }
    }

    private String getCorrelationId() {
        return PubSubMessageHelper
                .getCorrelationId(getCommand().getPubsubMessage().getAttributesMap())
                .orElse(null);
    }
}
