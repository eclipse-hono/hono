/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli.app;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.amqp.AmqpApplicationClient;
import org.eclipse.hono.client.ServerErrorException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.buffer.Buffer;

/**
 * A command line client for sending commands to devices connected
 * to one of Hono's protocol adapters.
 * <p>
 * The commands to send are read from stdin.
 */
@Component
@Profile("command")
public class Commander extends AbstractApplicationClient {

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
        client.start()
                .onSuccess(v -> {
                    if (client instanceof AmqpApplicationClient) {
                        ((AmqpApplicationClient) client).addReconnectListener(c -> startCommandClient());
                    }
                })
                .compose(v -> startCommandClient())
                .onFailure(this::close);
    }

    private Future<Void> startCommandClient() {
        return getCommandFromUser()
                .compose(this::processCommand)
                .onComplete(sendAttempt -> startCommandClient());
    }

    private Future<Void> processCommand(final Command command) {

        // TODO set request timeout
        final Future<Void> sendResult;
        if (command.isOneWay()) {
            log.info("Command sent to device");
            sendResult = client.sendOneWayCommand(tenantId, deviceId, command.getName(), command.getContentType(),
                    Buffer.buffer(command.getPayload()), null, null);
        } else {
            log.info("Command sent to device... [waiting for response for max. {} seconds]",
                    requestTimeoutInSecs);
            sendResult = client.sendCommand(tenantId, deviceId, command.getName(), command.getContentType(),
                    Buffer.buffer(command.getPayload()), UUID.randomUUID().toString(), null, null)
                    .map(this::printResponse);
        }

        return sendResult.otherwise(error -> {
            if (ServerErrorException.extractStatusCode(error) == HttpURLConnection.HTTP_UNAVAILABLE) {
                log.error(
                        "Error sending command (error code 503). Is the device really waiting for a command? (device [{}] in tenant [{}])",
                        deviceId, tenantId);
            } else {
                log.error("Error sending command: {}", error.getMessage());
            }
            return null;
        });
    }

    private Void printResponse(final DownstreamMessage<?> result) {
        log.info("Received Command response : {}",
                Optional.ofNullable(result.getPayload()).orElseGet(Buffer::buffer));
        return null;
    }

    private Future<Command> getCommandFromUser() {
        final Promise<Command> result = Promise.promise();
        workerExecutor.executeBlocking(userInputFuture -> {
            System.out.println();
            System.out.println();
            System.out.printf(
                    ">>>>>>>>> Enter name of command for device [%s] in tenant [%s] (prefix with 'ow:' to send one-way command):",
                    deviceId, tenantId);
            System.out.println();
            final String honoCmd = scanner.nextLine();
            System.out.println(">>>>>>>>> Enter command payload:");
            final String honoPayload = scanner.nextLine();
            System.out.println(">>>>>>>>> Enter content type:");
            final String honoContentType = scanner.nextLine();
            System.out.println();
            userInputFuture.complete(new Command(honoCmd, honoPayload, honoContentType));
        }, result);
        return result.future();
    }

    private void close(final Throwable t) {
        workerExecutor.close();
        vertx.close();
        client.stop();
        log.error("Error: {}", t.getMessage());
    }

    /**
     * Command class that encapsulates hono command and payload.
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
