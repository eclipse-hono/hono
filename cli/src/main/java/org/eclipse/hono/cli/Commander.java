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

import static java.lang.String.*;

/**
 * Command application that connects to the Hono, get commands from the user using System.in, send those commands to devices
 * and logs command responses if any.
 */
@Component
@Profile("command")
public class Commander extends AbstractClient {

    private final Scanner scanner = new Scanner(System.in);
    @Value(value = "${command.timeoutInSeconds}")
    protected int requestTimeoutInSecs;
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
        LOG.info("Command sent to device... [Command request will timeout in {} seconds]", requestTimeoutInSecs);
        return client.getOrCreateCommandClient(tenantId, deviceId)
                .map(this::setRequestTimeOut)
                .compose(commandClient -> commandClient
                        .sendCommand(command.getCommand(), Buffer.buffer(command.getPayload()))
                        .map(this::printResponse)
                        .map(x -> closeCommandClient(commandClient))
                        .otherwise(error -> {
                            LOG.error("Error: {}", error.getMessage());
                            return closeCommandClient(commandClient);
                        }));
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
            System.out.print(
                    format(">>>>>>>>> Enter command for device [%s:%s] <then press Enter, hit ctrl-c to exit >: ",
                            tenantId, deviceId));
            final String honoCmd = scanner.nextLine();
            System.out.print(
                    format(">>>>>>>>> Enter command payload for device [%s:%s] <then press Enter, hit ctrl-c to exit>: ",
                            tenantId, deviceId));
            final String honoPayload = scanner.nextLine();
            userInputFuture.complete(new Command(honoCmd, honoPayload));
        }, result -> {
            if (result.succeeded()) {
                commandFuture.complete((Command) result.result());
            } else {
                commandFuture.fail(result.cause());
            }
        });
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

        private final String command;
        private final String payload;

        Command(final String command, final String payload) {
            this.command = command;
            this.payload = payload;
        }

        private String getCommand() {
            return command;
        }

        private String getPayload() {
            return payload;
        }

    }
}
