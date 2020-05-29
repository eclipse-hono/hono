/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.springframework.core.MethodParameter;
import org.springframework.shell.Availability;
import org.springframework.shell.CompletionContext;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.standard.ValueProviderSupport;
import org.springframework.stereotype.Component;

/**
 * This class contains the methods that the user can execute acting as an application consumer.
 * To execute these commands the ClientConfig.profile must be valued as APPLICATION and the connection must be created
 * with the sub command connect of the shell.
 */
@ShellComponent
@ShellCommandGroup("Northbound Commands (Application)")
public class ApplicationProfile extends AbstractCliClient {

    /**
     * Message types.
     */
    public static final String TYPE_TELEMETRY = "telemetry";
    public static final String TYPE_EVENT = "event";
    public static final String TYPE_ALL = "all";
    private static final long INTERVAL_MILLIS = 10000;
    private ApplicationClientFactory clientFactory;

    /*
     * This command allow the creation of the ApplicationClientFactory and the establishment of the connection to the given endpoint.
     */
    @ShellMethod("Create the connection to a given endpoint\n" +
            "\t\t\t[-H --host] [-P --port] [-u --username] [-p --password]")
    void connectApplication(@ShellOption(value = {"-H", "--host"},  defaultValue = "", help = "The address of the endpoint which you want to establish a connection to") final String host,
                 @ShellOption(value = {"-P", "--port"}, defaultValue = "0", help = "The port used by the service on the connection endpoint") final int port,
                 @ShellOption(value = {"-u", "--username"}, defaultValue = "", help = "Credentials to use to connect to the endpoint") final String username,
                 @ShellOption(value = {"-p", "--password"}, defaultValue = "", help = "Credentials to use to connect to the endpoint") final String password) {
        if (clientFactory != null) {
            shellHelper.printInfo("The previously connection will be destroyed");
        }
        final ClientConfigProperties cfp = new ClientConfigProperties(this.honoClientConfig);
        cfp.setHost(host);
        cfp.setPort(port);
        cfp.setUsername(username);
        cfp.setPassword(password);
        //create the factory with connection info
        try {
            clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, cfp));
            shellHelper.printInfo("Connection created!");
            clientFactory.connect().setHandler(this::handleConnectionStatus);
        } catch (final Exception e) {
            clientFactory = null;
            shellHelper.printError("Error occurred during initialization of connection: " + e.getMessage());
        }
    }

    @ShellMethod("Receive data from device\n" +
            "\t\t\tParam: [-T --tenant] [-M --messageType] [-S --retryInterval]")
    @ShellMethodAvailability("connectionAvailability")
    void receive( @ShellOption(value = {"-T", "--tenant"}, defaultValue = "", help = "The tenant you want to connect to") final String tenant,
                  @ShellOption(value = {"-M", "--messageType"}, defaultValue = "", valueProvider = MessageTypeProvider.class, help = "Specify the message type: '" + TYPE_ALL + "', '" + TYPE_EVENT + "', '" + TYPE_TELEMETRY + "'" ) final String messageType,
                  @ShellOption(value = {"-S", "--retryInterval"}, defaultValue = "0", help = "The time in seconds to wait before retry to connect") final int connectionRetryInterval) {
        final Receiver receiverInstance = new Receiver(clientFactory, vertx, tenant, messageType, connectionRetryInterval);
        connLatch = new CountDownLatch(1);
        receiverInstance.start(connLatch, con);
        // if everything went according to plan, the next step will block forever
        try {
            connLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @ShellMethod("Collect statistics of received messages\n" +
            "\t\t\tParam: [-T --tenant] [-M --messageType] [-S --retryInterval] [-I --interval] [-I --autoReset]")
    @ShellMethodAvailability("connectionAvailability")
    void getStatistics( @ShellOption(value = {"-T", "--tenant"}, defaultValue = "", help = "The tenant you want to connect to") final String tenant,
                    @ShellOption(value = {"-M", "--messageType"}, defaultValue = "", valueProvider = MessageTypeProvider.class, help = "Specify the message type: '" + TYPE_ALL + "', '" + TYPE_EVENT + "', '" + TYPE_TELEMETRY + "'" ) final String messageType,
                    @ShellOption(value = {"-S", "--retryInterval"}, defaultValue = "0", help = "The time in seconds to wait before retry to connect") final int connectionRetryInterval,
                    @ShellOption(value = {"-I", "--interval"}, defaultValue = "10000", help = "The statistics interval in milliseconds") final int interval,
                    @ShellOption(value = {"-I", "--autoReset"}, defaultValue = "false", help = "Resets the overall statistics after one quiet interval") final boolean autoReset) {

        final Receiver receiverInstance = new Receiver(clientFactory, vertx, tenant, messageType, connectionRetryInterval);
        final ReceiverStatistics receiverStatInstance = new ReceiverStatistics(receiverInstance, vertx, interval, autoReset);

        connLatch = new CountDownLatch(1);
        receiverInstance.start(connLatch, con);
        receiverStatInstance.start(connLatch);
        // if everything went according to plan, the next step will block forever
        try {
            connLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @ShellMethod("Send a command to a device \n" +
            "\t\t\tParam: [-T --tenant]")
    @ShellMethodAvailability("connectionAvailability")
    void sendCommand(@ShellOption(value = {"-T", "--tenant"}, defaultValue = "", help = "The tenant you want to connect to") final String tenant,
                     @ShellOption(value = {"-D", "--device"}, defaultValue = "", help = "The device you want to connect to") final String device,
                     @ShellOption(value = {"-S", "--timeout"}, defaultValue = "0", help = "The timeout interval of the request expressed in seconds") final int timeout) {
        final Commander commanderInstance = new Commander(clientFactory, vertx, inputReader, tenant, device, timeout);
        connLatch = new CountDownLatch(1);
        commanderInstance.start(connLatch, con);
        // if everything went according to plan, the next step will block forever
        try {
            connLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @ShellMethod("Destroy the connection to the endpoint.")
    @ShellMethodAvailability("connectionAvailability")
    void disconnectApplication() {
        shellHelper.printInfo("The connection will be destroyed");
        clientFactory.disconnect();
        clientFactory = null;
    }

    /**
     * Check the availability of the factory.
     * If not present it doesn't allow the execution of commands.
     *
     * @return Availability status.
     */
    private Availability connectionAvailability() {
        if (clientFactory == null) {
            return Availability.unavailable("You are not connected");
        }
        final CompletableFuture<Void> result = new CompletableFuture<>();
        clientFactory.isConnected()
                .onSuccess(ok -> result.complete(null))
                .onFailure(t -> result.completeExceptionally(t));
        try {
            result.join();
        } catch (final CompletionException e) {
            return Availability.unavailable("You are not connected");
        }
        return Availability.available();
    }

    /**
     * MessageTypes class container.
     */
    @Component
    public static class MessageTypeProvider extends ValueProviderSupport {

        private final String[] VALUES = new String[] {
                TYPE_TELEMETRY,
                TYPE_EVENT,
                TYPE_ALL
        };

        @Override
        public List<CompletionProposal> complete(final MethodParameter parameter, final CompletionContext completionContext, final String[] hints) {
            return Arrays.stream(VALUES).map(CompletionProposal::new).collect(Collectors.toList());
        }

    }


}



