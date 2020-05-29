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

package org.eclipse.hono.cli.device;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.cli.application.ApplicationProfile;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
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
import org.springframework.util.StringUtils;

/**
 * This class contains the methods that the user can execute acting as a device.
 * To execute these commands the ClientConfig.profile must be valued as DEVICE and the connection must be created
 * with the sub command connect of the shell.
 */
@ShellComponent
@ShellCommandGroup("Southbound Commands (Device)")
public class DeviceProfile extends AbstractCliClient {

    /**
     * Message types.
     */
    public static final String EVENT = "event";
    public static final String QOS1 = "qos1";
    public static final String QOS2 = "qos2";

    private AmqpAdapterClientFactory clientFactory;

    /**
     * To stop the executions of internal commands.
     */
    private CountDownLatch connLatch;

    /*
     * This command allow the creation of the AmqpAdapterClientFactory and the establishment of the connection to the given endpoint.
     */
    @ShellMethod("Create the connection to connect to an AMQP Adapter endpoint\n" +
            "\t\t\t[-H --host] [-P --port] [-u --username] [-p --password] [-c --certPath] [-k --keyPath] [-t --trustStorePath]")
    void connectDevice(@ShellOption(value = {"-H", "--host"},  defaultValue = "", help = "The address of the endpoint which you want to establish a connection to") final String host,
                       @ShellOption(value = {"-P", "--port"}, defaultValue = "0", help = "The port used by the service on the connection endpoint") final int port,
                       @ShellOption(value = {"-u", "--username"}, defaultValue = "", help = "Credentials to use to connect to the endpoint") final String username,
                       @ShellOption(value = {"-p", "--password"}, defaultValue = "", help = "Credentials to use to connect to the endpoint") final String password,
                       @ShellOption(value = {"-c", "--certPath"}, defaultValue = "", help = "Example - relative path: config/hono-demo-certs-jar/device-cert.pem") final String certPath,
                       @ShellOption(value = {"-k", "--keyPath"}, defaultValue = "", help = "Example - relative path: config/hono-demo-certs-jar/device-key.pem") final String keyPath,
                       @ShellOption(value = {"-t", "--trustStorePath"}, defaultValue = "", help = "Example - relative path: config/hono-demo-certs-jar/trusted-certs.pem") final String trustStorePath) {
        if (!username.contains("@") || StringUtils.isEmpty(username.split("@")[1])) {
            shellHelper.printError("The username provided must have the format username@TENANT");
            return;
        }
        if (clientFactory != null) {
            shellHelper.printInfo("The previously connection will be destroyed");
            clientFactory.disconnect();
        }
        final ClientConfigProperties cfp = new ClientConfigProperties(this.honoClientConfig);
        cfp.setHost(host);
        cfp.setPort(port);
        cfp.setUsername(username);
        cfp.setPassword(password);
        if (!certPath.equals("")) {
            cfp.setCertPath(certPath);
        }
        if (!keyPath.equals("")) {
            cfp.setKeyPath(keyPath);
        }
        if (!trustStorePath.equals("")) {
            cfp.setTrustStorePath(trustStorePath);
        }
        //create the factory with connection info
        try {
            clientFactory = AmqpAdapterClientFactory.create(HonoConnection.newConnection(vertx, cfp), cfp.getUsername().split("@")[1]);
            shellHelper.printInfo("Connection created!");
            clientFactory.connect().onComplete(this::handleConnectionStatus);
        } catch (final Exception e) {
            clientFactory = null;
            shellHelper.printError("Error occurred during initialization of connection: " + e.getMessage());
        }
    }

    @ShellMethod("Send events or telemetry data to Hono through AMQP adapter.\n" +
            "\t\tThe default configuration will proceed as an authenticated device.\n" +
            "\t\tIf no credentials were provided during connection phase, the execution will proceed as unauthenticated (in that case the adapter must be setted of consequence).\n" +
            "\t\t\tParam: deviceId [-A --address] [-P --payload]")
    @ShellMethodAvailability("connectionAvailability")
    void send(@ShellOption(help = "The identity of the device to use to connect to the AMQP Adapter") final String deviceId,
              @ShellOption(value = {"-M", "--messageType"}, defaultValue = "EVENT", valueProvider = ApplicationProfile.MessageTypeProvider.class, help = "Specify the message type you want to send: '" + EVENT + "', '" + QOS1 + "', '" + QOS2 + "'" ) final String messageType,
              @ShellOption(value = {"-P", "--payload"}, defaultValue = "'{\"temp\": 5}'", help = "Specify the body of the message")  final String payload) {
        final TelemetryAndEvent TaEInstance = new TelemetryAndEvent(clientFactory, vertx, deviceId, messageType, payload);
        connLatch = new CountDownLatch(1);
        TaEInstance.start(connLatch);
        // if everything went according to plan, the next step will block forever
        try {
            connLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @ShellMethod("Simulate a device receiving and responding to command request messages through AMQP adapter.\n" +
            "\t\t\tParam: deviceId")
    @ShellMethodAvailability("connectionAvailability")
    void receiveCommand(@ShellOption(help = "The identity of the device to use to connect to the AMQP Adapter") final String deviceId) {
        final CommandAndControl CaCInstance = new CommandAndControl(clientFactory, vertx, deviceId);
        connLatch = new CountDownLatch(1);
        CaCInstance.start(connLatch);
        // if everything went according to plan, the next step will block forever
        try {
            connLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @ShellMethod("Destroy the connection to the endpoint.")
    @ShellMethodAvailability("connectionAvailability")
    void disconnectDevice() {
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
                EVENT,
                QOS1,
                QOS2
        };

        @Override
        public List<CompletionProposal> complete(final MethodParameter parameter, final CompletionContext completionContext, final String[] hints) {
            return Arrays.stream(VALUES).map(CompletionProposal::new).collect(Collectors.toList());
        }

    }
}



