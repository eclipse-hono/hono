/*
 * ******************************************************************************
 *  * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *  *
 *  * See the NOTICE file(s) distributed with this work for additional
 *  * information regarding copyright ownership.
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Eclipse Public License 2.0 which is available at
 *  * http://www.eclipse.org/legal/epl-2.0
 *  *
 *  * SPDX-License-Identifier: EPL-2.0
 *  ******************************************************************************
 *
 */

package org.eclipse.hono.cli;

import javax.annotation.PostConstruct;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.buffer.Buffer;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.util.BufferResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * A command line client for sending <em>commands</em> to devices connected to Hono.
 * <p>
 * The client connects to Hono and reads commands from System.in.
 */
@Component
@Profile("command")
public class Commander extends AbstractClient {

    private final Scanner scanner = new Scanner(System.in);
    @Value(value = "${command.timeoutInSeconds}")
    private int requestTimeoutInSecs;
    private WorkerExecutor workerExecutor;

    /**
     * Starts this component.
     *
     */
    @PostConstruct
    void start() {
        workerExecutor = vertx.createSharedWorkerExecutor("user-input-pool", 3, TimeUnit.HOURS.toNanos(1));
        startCommandClient(client.connect(x -> onDisconnect()));
    }

    private void startCommandClient(final Future<HonoClient> clientFuture) {
        clientFuture
                .setHandler(this::handleClientConnectionStatus)
                .compose(x -> getCommandFromUser())
                .compose(this::processCommand)
                .setHandler(x -> startCommandClient(clientFuture));
    }

    private Future<Void> processCommand(final Command command) {

        LOG.info("Command sent to device... [request will timeout in {} seconds]", requestTimeoutInSecs);

        final Future<CommandClient> commandClient = client.getOrCreateCommandClient(tenantId, deviceId);
        return commandClient
                .map(this::setRequestTimeOut)
                .compose(c -> {
                    if (command.isOneWay()) {
                        return c
                                .sendOneWayCommand(command.getName(), command.getContentType(), Buffer.buffer(command.getPayload()), null)
                                .map(ok -> c);
                    } else {
                        return c
                                .sendCommand(command.getName(), command.getContentType(), Buffer.buffer(command.getPayload()), null)
                                .map(this::printResponse)
                                .map(ok -> c);
                    }
                })
                .map(this::closeCommandClient)
                .otherwise(error -> {
                    LOG.error("Error sending command: {}", error.getMessage());
                    if (commandClient.succeeded()) {
                        return closeCommandClient(commandClient.result());
                    } else {
                        return null;
                    }
                });
    }

    private CommandClient setRequestTimeOut(final CommandClient commandClient) {
        commandClient.setRequestTimeout(TimeUnit.SECONDS.toMillis(requestTimeoutInSecs));
        return commandClient;
    }

    private Void closeCommandClient(final CommandClient commandClient) {
        LOG.trace("Close command client to device [{}:{}]", tenantId, deviceId);
        commandClient.close(closeHandler -> {
        });
        return null;
    }

    private Void printResponse(final BufferResult result) {
        LOG.info("Received Command response : {}",
                Optional.ofNullable(result.getPayload()).orElse(Buffer.buffer()).toString());
        return null;
    }

    private Future<Command> getCommandFromUser() {
        final Future<Command> commandFuture = Future.future();
        workerExecutor.executeBlocking(userInputFuture -> {
            System.out.println();
            System.out.println();
            System.out.printf(">>>>>>>>> Enter name of command for device [%s:%s] (prefix with 'ow:' to send one-way command):",
                    tenantId, deviceId);
            System.out.println();
            final String honoCmd = scanner.nextLine();
            System.out.println(">>>>>>>>> Enter command payload:");
            final String honoPayload = scanner.nextLine();
            System.out.println(">>>>>>>>> Enter content type:");
            final String honoContentType = scanner.nextLine();
            System.out.println();
            userInputFuture.complete(new Command(honoCmd, honoPayload, honoContentType));
        }, commandFuture);
        return commandFuture;
    }

    private void onDisconnect() {
        LOG.info("Connecting client...");
        vertx.setTimer(connectionRetryInterval, reconnect -> {
            LOG.info("attempting to re-connect to Hono ...");
            client.connect(con -> onDisconnect());
        });
    }

    private void handleClientConnectionStatus(final AsyncResult<HonoClient> result) {
        if (result.failed()) {
            workerExecutor.close();
            vertx.close();
            LOG.error("Error: {}", result.cause().getMessage());
            throw new RuntimeException("Error connecting hono client", result.cause());
        }
    }

    /**
     * Command class that encapsulates hono command and payload.
     */
    private class Command {

        private final String name;
        private final String payload;
        private final String contentType;
        private final boolean oneWay;

        Command(final String command, final String payload, final String contentType) {

            oneWay = command.startsWith("ow:");
            if (oneWay) {
                name = command.substring(3);
            } else {
                name = command;
            }
            this.payload = payload;
            this.contentType = contentType;
        }

        private boolean isOneWay() {
            return oneWay;
        }

        private String getName() {
            return name;
        }

        private String getPayload() {
            return payload;
        }

        private String getContentType() {
            return contentType;
        }
    }
}
