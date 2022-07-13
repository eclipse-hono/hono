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
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.CommandAlreadyProcessedException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.CommandToBeReprocessedException;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A context for passing around parameters relevant for processing a {@code Command} used in a Kafka based
 * client.
 */
public class KafkaBasedCommandContext extends MapBasedExecutionContext implements CommandContext {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedCommandContext.class);

    private static final String PROPERTY_NAME_DELIVERY_FAILURE_RESPONSES_DISABLED = "HONO_DISABLE_KAFKA_COMMAND_DELIVERY_FAILURE_RESPONSES";
    private static final boolean DELIVERY_FAILURE_RESPONSES_DISABLED = Boolean
            .parseBoolean(getProperty(PROPERTY_NAME_DELIVERY_FAILURE_RESPONSES_DISABLED));
    static {
        if (DELIVERY_FAILURE_RESPONSES_DISABLED) {
            LOG.info("sending of command delivery failure response messages is disabled");
        }
    }

    private final KafkaBasedCommand command;
    private final CommandResponseSender commandResponseSender;
    private String completedOutcome;

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
        super(span);
        this.command = Objects.requireNonNull(command);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
    }

    /**
     * Checks if the context has already been completed.
     *
     * @return {@code true} if the context has already been completed.
     */
    public final boolean isCompleted() {
        return completedOutcome != null;
    }

    @Override
    public final void logCommandToSpan(final Span span) {
        command.logToSpan(span);
    }

    @Override
    public final KafkaBasedCommand getCommand() {
        return command;
    }

    @Override
    public final void accept() {
        if (!setCompleted("accepted")) {
            return;
        }
        final Span span = getTracingSpan();
        LOG.trace("accepted command message [{}]", getCommand());
        Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_ACCEPTED);
        span.log("command for device handled with outcome 'accepted'");
        span.finish();
    }

    @Override
    public final void release(final Throwable error) {
        Objects.requireNonNull(error);
        if (!setCompleted("released")) {
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
            sendDeliveryFailureCommandResponseMessage(status, errorMessage, span, error)
                    .onComplete(v -> span.finish());
        } else {
            span.finish();
        }
    }

    @Override
    public final void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
        if (!setCompleted("modified")) {
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
            sendDeliveryFailureCommandResponseMessage(status, error, span, null)
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
        if (!setCompleted("rejected")) {
            return;
        }
        final Span span = getTracingSpan();
        Tags.HTTP_STATUS.set(span, status);
        if (!DELIVERY_FAILURE_RESPONSES_DISABLED && isRequestResponseCommand()) {
            final String nonNullCause = Optional.ofNullable(cause).orElse("Command message rejected");
            sendDeliveryFailureCommandResponseMessage(status, nonNullCause, span, null)
                    .onComplete(v -> span.finish());
        } else {
            span.finish();
        }
    }

    private boolean isRequestResponseCommand() {
        return !command.isOneWay();
    }

    private Future<Void> sendDeliveryFailureCommandResponseMessage(
            final int status,
            final String error,
            final Span span,
            final Throwable cause) {

        final JsonObject payloadJson = new JsonObject();
        payloadJson.put("error", error != null ? error : "");
        final String correlationId = getCorrelationId();
        if (correlationId == null) {
            TracingHelper.logError(span, "can't send command response message - no correlation id set");
            return Future.failedFuture("missing correlation id");
        }
        final CommandResponse commandResponse = new CommandResponse(
                command.getTenant(),
                command.getDeviceId(),
                payloadJson.toBuffer(),
                CommandConstants.CONTENT_TYPE_DELIVERY_FAILURE_NOTIFICATION,
                status,
                correlationId,
                "",
                MessagingType.kafka);
        commandResponse.setAdditionalProperties(Collections.unmodifiableMap(command.getDeliveryFailureNotificationProperties()));

        return commandResponseSender.sendCommandResponse(
                // try to retrieve tenant configuration from context
                Optional.ofNullable(get(KEY_TENANT_CONFIG))
                    .filter(TenantObject.class::isInstance)
                    .map(TenantObject.class::cast)
                    // and fall back to default configuration
                    .orElseGet(() -> TenantObject.from(command.getTenant())),
                new RegistrationAssertion(command.getDeviceId()),
                commandResponse,
                span.context())
            .onFailure(thr -> {
                LOG.debug("failed to publish command response [{}]", commandResponse, thr);
                TracingHelper.logError(span, "failed to publish command response message", thr);
            })
            .onSuccess(v -> {
                LOG.debug("published error command response [{}, cause: {}]", commandResponse,
                        cause != null ? cause.getMessage() : error);
                span.log("published error command response");
            });
    }

    private String getCorrelationId() {
        // extract correlation id from headers; command could be invalid in which case
        // command.getCorrelationId() throws an exception
        return KafkaRecordHelper.getCorrelationId(command.getRecord().headers()).orElse(null);
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

    private static String getProperty(final String name) {
        return System.getProperty(name, System.getenv(name));
    }
}
