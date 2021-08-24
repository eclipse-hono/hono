/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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
import java.time.Duration;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.amqp.AmqpApplicationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.MessageHelper;
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
    private int commandTimeOutInSeconds;
    @Value(value = "${device.id}")
    private String deviceId;
    private WorkerExecutor workerExecutor;

    /**
     * Starts this component.
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

        final Future<Void> sendResult;
        if (command.isOneWay()) {
            System.out.println("Command sent to device.");
            sendResult = client.sendOneWayCommand(tenantId, deviceId, command.getName(), command.getContentType(),
                    Buffer.buffer(command.getPayload()), null, null);
        } else {
            System.out.printf("Command sent to device... [waiting for response for max. %d seconds]", commandTimeOutInSeconds);
            System.out.println();
            sendResult = client.sendCommand(tenantId, deviceId, command.getName(), command.getContentType(),
                    Buffer.buffer(command.getPayload()), UUID.randomUUID().toString(), null,
                    Duration.ofSeconds(commandTimeOutInSeconds), null)
                    .map(this::printResponse);
        }

        return sendResult.otherwise(error -> {
            if (ServiceInvocationException.extractStatusCode(error) == HttpURLConnection.HTTP_UNAVAILABLE) {
                System.out.printf("Error sending command (error code 503). Is the device really waiting for a command? (device [%s] in tenant [%s])",
                        deviceId, tenantId);
            } else {
                System.out.printf("Error sending command: %s", error.getMessage());
            }
            System.out.println();
            return null;
        });
    }

    private Void printResponse(final DownstreamMessage<?> result) {
        System.out.printf("Received Command response: %s",
                Optional.ofNullable(result.getPayload()).orElseGet(Buffer::buffer));
        System.out.println();
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
            final String suggestedContentType = honoPayload.trim().startsWith("{") && honoPayload.trim().endsWith("}")
                    ? MessageHelper.CONTENT_TYPE_APPLICATION_JSON
                    : "text/plain";
            System.out.println(">>>>>>>>> Enter content type (leave empty for '" + suggestedContentType + "' default):");
            final String honoContentType = scanner.nextLine();
            System.out.println();
            final String contentTypeToUse = honoContentType.isBlank() ? suggestedContentType : honoContentType;
            userInputFuture.complete(new Command(honoCmd, honoPayload, contentTypeToUse));
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
