/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.cli.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

import org.eclipse.hono.application.client.ApplicationClient;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.cli.util.CommandUtils;
import org.eclipse.hono.cli.util.PropertiesVersionProvider;
import org.eclipse.hono.util.Constants;

import io.quarkus.runtime.Quarkus;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import picocli.CommandLine;
import picocli.CommandLine.ScopeType;

/**
 * Command for consuming messages from Hono's north bound Telemetry and/or Event APIs.
 *
 */
@Singleton
@CommandLine.Command(
        name = "consume",
        description = { "Consume telemetry and/or event messages from devices." },
        mixinStandardHelpOptions = true,
        versionProvider = PropertiesVersionProvider.class,
        sortOptions = false)
public class TelemetryAndEvent implements Callable<Integer> {

    private static final String MESSAGE_TYPE_TELEMETRY = "telemetry";
    private static final String MESSAGE_TYPE_EVENT = "event";

    @CommandLine.ParentCommand
    NorthBoundApis appCommand;

    @CommandLine.Option(
            names = {"-t", "--tenant"},
            description = { "The tenant to consume messages for (default: ${DEFAULT-VALUE})." },
            defaultValue = Constants.DEFAULT_TENANT,
            order = 15,
            scope = ScopeType.INHERIT)
    String tenantId;

    @CommandLine.Option(
            names = { "--telemetry" },
            description = {
                    "Consume telemetry messages.",
                    "If not specified, both telemetry and event messages will be consumed.",
                    "Messages are printed to standard out one message per line using the following format:",
                    "t 4711 text/plain This is the message payload {key1=value1,key2=value2,...}"
            },
            order = 20)
    boolean consumeTelemetry;

    @CommandLine.Option(
            names = { "--event" },
            description = {
                    "Consume event messages.",
                    "If not specified, both telemetry and event messages will be consumed.",
                    "Messages are printed to standard out one message per line using the following format:",
                    "e 4711 text/plain This is the message payload {key1=value1,key2=value2,...}"
            },
            order = 21)
    boolean consumeEvent;

    @Inject
    Vertx vertx;

    private final Set<String> supportedMessageTypes = new HashSet<>();

    private Future<Void> createConsumers(final ApplicationClient<? extends MessageContext> client) {

        final Handler<Throwable> closeHandler = cause -> {
            System.err.println("peer has closed message consumer(s) unexpectedly, trying to reopen ...");
            vertx.setTimer(1000L, reconnect -> {
                createConsumers(client);
            });
        };

        final List<Future<MessageConsumer>> consumerFutures = new ArrayList<>();
        if (supportedMessageTypes.contains(MESSAGE_TYPE_EVENT)) {
            consumerFutures.add(
                    client.createEventConsumer(
                            tenantId,
                            msg -> printMessage(MESSAGE_TYPE_EVENT, msg),
                            closeHandler));
        }

        if (supportedMessageTypes.contains(MESSAGE_TYPE_TELEMETRY)) {
            consumerFutures.add(
                    client.createTelemetryConsumer(
                            tenantId,
                            msg -> printMessage(MESSAGE_TYPE_TELEMETRY, msg),
                            closeHandler));
        }

        return Future.all(consumerFutures)
                .mapEmpty();
    }

    private void printMessage(final String endpoint, final DownstreamMessage<? extends MessageContext> message) {

        System.out.println("%s %s %s %s %s".formatted(
                endpoint.charAt(0),
                message.getDeviceId(),
                Optional.ofNullable(message.getContentType()).orElse("-"),
                Optional.ofNullable(message.getPayload())
                    .map(Buffer::toString)
                    .orElse("-"),
                message.getProperties().getPropertiesMap()));
    }

    @Override
    public Integer call() {

        if (consumeEvent) {
            supportedMessageTypes.add(MESSAGE_TYPE_EVENT);
        }
        if (consumeTelemetry) {
            supportedMessageTypes.add(MESSAGE_TYPE_TELEMETRY);
        }
        if (supportedMessageTypes.isEmpty()) {
            supportedMessageTypes.add(MESSAGE_TYPE_EVENT);
            supportedMessageTypes.add(MESSAGE_TYPE_TELEMETRY);
        }

        try {
            appCommand.getApplicationClient()
                .compose(this::createConsumers)
                .onSuccess(ok -> System.err.println("""
                        Consuming messages for tenant [%s], ctrl-c to exit.
                        """.formatted(tenantId)))
                .toCompletionStage()
                .toCompletableFuture()
                .join();
            Quarkus.waitForExit();
            return CommandLine.ExitCode.OK;
        } catch (final CompletionException e) {
            CommandUtils.printError(e.getCause());
            System.err.println("failed to create message consumer(s): %s".formatted(e.getMessage()));
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
