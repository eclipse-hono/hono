/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.AbstractCommandContext;
import org.eclipse.hono.client.command.CommandAlreadyProcessedException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.CommandToBeReprocessedException;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessagingType;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

/**
 * A context for passing around parameters relevant for processing a {@code Command} used in a Kafka based
 * client.
 */
public class KafkaBasedCommandContext extends AbstractCommandContext<KafkaBasedCommand> implements CommandContext {

    private static final String PROPERTY_NAME_DELIVERY_FAILURE_RESPONSES_DISABLED = "HONO_DISABLE_KAFKA_COMMAND_DELIVERY_FAILURE_RESPONSES";
    private static final boolean DELIVERY_FAILURE_RESPONSES_DISABLED = Boolean
            .parseBoolean(getProperty(PROPERTY_NAME_DELIVERY_FAILURE_RESPONSES_DISABLED));
    static {
        if (DELIVERY_FAILURE_RESPONSES_DISABLED) {
            LOG.info("sending of command delivery failure response messages is disabled");
        }
    }

    /**
     * Creates a new command context.
     *
     * @param command The command to be processed.
     * @param commandResponseSender The sender used to send a command response.
     * @param span The OpenTracing span to use for tracking the processing of the command.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedCommandContext(
            final KafkaBasedCommand command,
            final CommandResponseSender commandResponseSender,
            final Span span) {
        super(span, command, commandResponseSender);
    }

    @Override
    public final void release(final Throwable error) {
        Objects.requireNonNull(error);
        if (!setCompleted(OUTCOME_RELEASED)) {
            return;
        }
        final Span span = getTracingSpan();
        TracingHelper.logError(span, "command could not be delivered or processed", error);
        final ServiceInvocationException mappedError = StatusCodeMapper.toServerError(error);
        final int status = mappedError.getErrorCode();
        Tags.HTTP_STATUS.set(span, status);
        if (!DELIVERY_FAILURE_RESPONSES_DISABLED && isRequestResponseCommand()
                && !(error instanceof CommandAlreadyProcessedException)
                && !(error instanceof CommandToBeReprocessedException)) {
            final String errorMessage = Optional.ofNullable(ServiceInvocationException.getErrorMessageForExternalClient(mappedError))
                    .orElse("Temporarily unavailable");
            final String correlationId = getCorrelationId();
            sendDeliveryFailureCommandResponseMessage(status, errorMessage, span, error, correlationId, MessagingType.kafka)
                    .onComplete(v -> span.finish());
        } else {
            span.finish();
        }
    }

    @Override
    public final void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
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
        if (!DELIVERY_FAILURE_RESPONSES_DISABLED && isRequestResponseCommand()) {
            final String error = "command not processed"
                    + (deliveryFailed ? "; delivery failed" : "")
                    + (undeliverableHere ? "; undeliverable here" : "");
            final String correlationId = getCorrelationId();
            sendDeliveryFailureCommandResponseMessage(status, error, span, null, correlationId, MessagingType.kafka)
                    .onComplete(v -> span.finish());
        } else {
            span.finish();
        }
    }

    @Override
    public final void reject(final String error) {
        TracingHelper.logError(getTracingSpan(), "client error trying to deliver or process command: " + error);
        reject(HttpURLConnection.HTTP_BAD_REQUEST, error);
    }

    @Override
    public final void reject(final Throwable error) {
        final int status = error instanceof ClientErrorException
                ? ((ClientErrorException) error).getErrorCode()
                : HttpURLConnection.HTTP_BAD_REQUEST;
        TracingHelper.logError(getTracingSpan(), "client error trying to deliver or process command", error);
        reject(status, error.getMessage());
    }

    private void reject(final int status, final String cause) {
        if (!setCompleted(OUTCOME_REJECTED)) {
            return;
        }
        final Span span = getTracingSpan();
        Tags.HTTP_STATUS.set(span, status);
        if (!DELIVERY_FAILURE_RESPONSES_DISABLED && isRequestResponseCommand()) {
            final String nonNullCause = Optional.ofNullable(cause).orElse("Command message rejected");
            final String correlationId = getCorrelationId();
            sendDeliveryFailureCommandResponseMessage(status, nonNullCause, span, null, correlationId, MessagingType.kafka)
                    .onComplete(v -> span.finish());
        } else {
            span.finish();
        }
    }

    private String getCorrelationId() {
        // extract correlation id from headers; command could be invalid in which case
        // command.getCorrelationId() throws an exception
        return KafkaRecordHelper.getCorrelationId(getCommand().getRecord().headers()).orElse(null);
    }

    private static String getProperty(final String name) {
        return System.getProperty(name, System.getenv(name));
    }
}
