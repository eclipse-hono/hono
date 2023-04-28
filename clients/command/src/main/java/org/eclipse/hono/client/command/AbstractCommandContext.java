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
package org.eclipse.hono.client.command;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

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
 * A base class providing utility methods for passing around parameters relevant for processing a {@code Command} used
 * in a Pub/Sub and Kafka based command context.
 * @param <T> The type of Command.
 */
public abstract class AbstractCommandContext<T extends Command> extends MapBasedExecutionContext implements CommandContext {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractCommandContext.class);

    protected final T command;

    private final CommandResponseSender commandResponseSender;

    private String completedOutcome;

    /**
     * Creates a new command context instance.
     *
     * @param span The OpenTracing span to use for tracking the processing of the command.
     * @param command The command to be processed.
     * @param commandResponseSender The sender used to send a command response.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public AbstractCommandContext(
            final Span span,
            final T command,
            final CommandResponseSender commandResponseSender) {
        super(span);
        this.command = Objects.requireNonNull(command);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
    }

    @Override
    public final boolean isCompleted() {
        return completedOutcome != null;
    }

    @Override
    public void logCommandToSpan(final Span span) {
        command.logToSpan(span);
    }

    @Override
    public T getCommand() {
        return command;
    }

    @Override
    public void accept() {
        if (!setCompleted(OUTCOME_ACCEPTED)) {
            return;
        }
        final Span span = getTracingSpan();
        LOG.debug("accepted command message [{}]", getCommand());
        Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_ACCEPTED);
        span.log("command for device handled with outcome 'accepted'");
        span.finish();
    }

    /**
     * Sets the outcome of a command context if it is not already completed with another outcome.
     *
     * @param outcome The outcome of the command context.
     * @return True if the outcome is set successfully, false if it has been already completed.
     */
    protected boolean setCompleted(final String outcome) {
        if (completedOutcome != null) {
            LOG.warn("can't apply '{}' outcome, context already completed with '{}' outcome [{}]",
                    outcome, completedOutcome, getCommand());
            return false;
        }
        completedOutcome = outcome;
        return true;
    }

    /**
     * Checks if the command is a request-response command.
     *
     * @return True if it is a request-response command, false otherwise.
     */
    protected boolean isRequestResponseCommand() {
        return !command.isOneWay();
    }

    /**
     * Sends a command response if the command response message represents an error message.
     *
     * @param status The HTTP status code indicating the outcome of processing the command.
     * @param error The error message describing the cause for the command message delivery failure.
     * @param span The active OpenTracing span to use for tracking this operation.
     * @param cause The delivery error.
     * @param correlationId The correlation ID of the command that this is the response for.
     * @param messagingType The type of the messaging system via which the command message was received.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the command response has been sent.
     *         <p>
     *         The future will be failed if the command response could not be sent.
     */
    protected Future<Void> sendDeliveryFailureCommandResponseMessage(
            final int status,
            final String error,
            final Span span,
            final Throwable cause,
            final String correlationId,
            final MessagingType messagingType) {
        if (correlationId == null) {
            TracingHelper.logError(span, "can't send command response message - no correlation id set");
            return Future.failedFuture("missing correlation id");
        }

        final JsonObject payloadJson = new JsonObject();
        payloadJson.put("error", error != null ? error : "");

        final CommandResponse commandResponse = new CommandResponse(
                command.getTenant(),
                command.getDeviceId(),
                payloadJson.toBuffer(),
                CommandConstants.CONTENT_TYPE_DELIVERY_FAILURE_NOTIFICATION,
                status,
                correlationId,
                "",
                messagingType);
        commandResponse.setAdditionalProperties(
                Collections.unmodifiableMap(command.getDeliveryFailureNotificationProperties()));

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
}
