/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli.application;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.cli.AbstractCommand;
import org.eclipse.hono.cli.shell.InputReader;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.BufferResult;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.buffer.Buffer;

/**
 * A command line client for sending commands to devices connected to one of Hono's protocol adapters.
 * <p>
 * The commands to send are read from stdin.
 */
public class Commander extends AbstractCommand {
    /**
     * Spring Shell inputReader.
     */
    private final InputReader inputReader;
    /**
     * To execute commands in another thread.
     */
    private WorkerExecutor workerExecutor;

    /**
     * Methods factory instantiated with the connection parameters.
     */
    private final ApplicationClientFactory clientFactory;

    private final String tenantId;
    private final String deviceId;
    private final int requestTimeoutInSecs;

    /**
     * Constructor to create the config environment for the execution of the command.
     *
     * @param clientFactory The factory with client's methods.
     * @param vertx The instance of vert.x connection.
     * @param inputReader The shell's inputReader.
     * @param tenantId The tenant which the user want to connect.
     * @param deviceId The device which the user want to connect.
     * @param requestTimeoutInSecs The timeout interval of the request expressed in seconds.
     */
    public Commander(final ApplicationClientFactory clientFactory, final Vertx vertx, final InputReader inputReader, final String tenantId, final String deviceId, final int requestTimeoutInSecs) {
        this.clientFactory = clientFactory;
        this.vertx = vertx;
        this.inputReader = inputReader;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.requestTimeoutInSecs = requestTimeoutInSecs;
    }

    /**
     * Entrypoint to start the command.
     *
     * @param latch The handle to signal the ended execution and return to the shell.
     * @param con The Hono connection instance, already connected.
     */
    public void start(final CountDownLatch latch, final HonoConnection con) {
        this.latch = latch;
        workerExecutor = vertx.createSharedWorkerExecutor("user-input-pool", 3, TimeUnit.HOURS.toNanos(1));
        clientFactory.isConnected().onComplete(connectionStatus -> {
            if (connectionStatus.succeeded()) {
                clientFactory.addReconnectListener(this::startCommandClient);
                startCommandClient(con);
            } else {
                close(connectionStatus.cause());
            }
        });
    }

    private void startCommandClient(final HonoConnection connection) {
        getCommandFromUser()
        .compose(this::processCommand)
        .setHandler(sendAttempt -> startCommandClient(connection));
    }

    private Future<Void> processCommand(final Command command) {

        final Future<CommandClient> commandClient = clientFactory.getOrCreateCommandClient(tenantId);
        return commandClient
                .map(this::setRequestTimeOut)
                .compose(c -> {
                    if (command.isOneWay()) {
                        log.info("Command sent to device");
                        return c.sendOneWayCommand(deviceId, command.getName(), command.getContentType(),
                                Buffer.buffer(command.getPayload()), null)
                                .map(ok -> c);
                    } else {
                        log.info("Command sent to device... [waiting for response for max. {} seconds]",
                                requestTimeoutInSecs);
                        return c.sendCommand(deviceId, command.getName(), command.getContentType(),
                                Buffer.buffer(command.getPayload()), null)
                                .map(this::printResponse)
                                .map(ok -> c);
                    }
                })
                .map(this::closeCommandClient)
                .otherwise(error -> {
                    if (ServerErrorException.extractStatusCode(error) == HttpURLConnection.HTTP_UNAVAILABLE) {
                        log.error(
                                "Error sending command (error code 503). Is the device really waiting for a command? (device [{}] in tenant [{}])",
                                deviceId, tenantId);
                    } else {
                        log.error("Error sending command: {}", error.getMessage());
                    }
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
        log.trace("Close command connection to device [{}:{}]", tenantId, deviceId);
        commandClient.close(closeHandler -> {
        });
        return null;
    }

    private Void printResponse(final BufferResult result) {
        log.info("Received Command response : {}",
                Optional.ofNullable(result.getPayload()).orElse(Buffer.buffer()));
        return null;
    }

    private Future<Command> getCommandFromUser() {
        final Promise<Command> result = Promise.promise();
        workerExecutor.executeBlocking(userInputFuture -> {
            System.out.println();
            System.out.println();
            final String honoCmd = inputReader.prompt(">>>>>>>>> Enter name of command for device [" + deviceId + "] in tenant [" + tenantId + "] (prefix with 'ow:' to send one-way command):");
            final String honoPayload = inputReader.prompt(">>>>>>>>> Enter command payload:");
            final String honoContentType = inputReader.prompt(">>>>>>>>> Enter content type:");
            userInputFuture.complete(new Command(honoCmd, honoPayload, honoContentType));
        }, result);
        return result.future();
    }

    private void close(final Throwable t) {
        workerExecutor.close();
        log.error("Connection not established: {}", t.getMessage());
        latch.countDown();
    }

    /**
     * Command class that encapsulates Hono command and payload.
     */
    private static class Command {

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
