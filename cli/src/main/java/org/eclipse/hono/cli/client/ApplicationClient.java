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

package org.eclipse.hono.cli.client;

import io.vertx.core.Vertx;
import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.cli.adapter.CommandAndControl;
import org.eclipse.hono.cli.adapter.TelemetryAndEvent;
import org.eclipse.hono.cli.application.Commander;
import org.eclipse.hono.cli.application.Receiver;
import org.eclipse.hono.cli.shell.InputReader;
import org.eclipse.hono.cli.shell.ShellHelper;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import org.eclipse.hono.cli.client.ClientConfig.MessageTypeProvider;

/**
 * Being a Component will be injected by spring.
 */
@Component
public class ApplicationClient extends AbstractCliClient {
    /**
     * Spring Shell output helper.
     * <p>
     * it can be passed to the method class instead to use the log (it's more stylish).
     */
    @Autowired
    public ShellHelper shellHelper;
    /**
     * Spring Shell inputReader.
     */
    @Autowired
    public InputReader inputReader;
    /**
     * Class that contains all parameters for the connection to Hono and for the execution of the commands.
     * Handling it a part allow to clone it and maintain a default config during the whole execution.
     */
    protected ClientConfig clientConfig;
    /**
     * All parameters that can be setted with the spring configuration at startup.
     * There will be injected with values and the stored in the configuration class, to allow the overriding of the parameters.
     */
    @Value(value = "${tenant.id:DEFAULT_TENANT}")
    private String tenantId;
    @Value(value = "${device.id}")
    private String deviceId;
    @Value(value = "${message.type:all}")
    private String messageType;
    @Value(value = "${connection.retryInterval:1000}")
    private int connectionRetryInterval;
    @Value(value = "${command.timeoutInSeconds:60}")
    private int commandTimeoutInSeconds;
    @Value(value = "${message.address:127.0.0.1}")//The address to set on the message being sent.
    private String messageAddress;
    @Value(value = "${message.payload:}")//The payload to send.
    private String messagePayload;
    /**
     * To stop the executions of internal commands.
     */
    private CountDownLatch latch;

    /**
     * Sets the vert.x instance.
     *
     * @param vertx The vert.x instance.
     * @throws NullPointerException if vert.x is {@code null}.
     */
    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
        this.ctx = vertx.getOrCreateContext();
    }
    /**
     * Sets the default client configuration with the parameters got at startup.
     * @param honoClientConfig The Client Configuration class instance.
     */
    @Autowired
    public final void setClientConfigProperties(final ClientConfigProperties honoClientConfig) {
        clientConfig = new ClientConfig(tenantId, deviceId, messageType, connectionRetryInterval, commandTimeoutInSeconds, messageAddress, messagePayload);
        clientConfig.honoClientConfig = Objects.requireNonNull(honoClientConfig);
    }
    /**
     * Sets the Client Factory.
     * @param factory The ApplicationClientFactory instance.
     */
    @Autowired
    public final void setApplicationClientFactory(final ApplicationClientFactory factory) {
        this.clientFactory = Objects.requireNonNull(factory);
    }

//    This will be used with the #1765.
//    @Autowired
//    public final void setAmqpAdapterClientFactory(final AmqpAdapterClientFactory factory) {
//        this.adapterFactory = Objects.requireNonNull(factory);
//    }


    /**
     * A base class for implementing command line client's methods that access Hono's
     * northbound APIs.
     *
     */
    @ShellComponent
    @ShellCommandGroup("Northbounds Commands (Application)")
    class Application {
        /**
         * Starts the receiver.
         */
        @ShellMethod("Start the receiver\n" +
                "\t\t\t\t[-H --host] [-P --port] [-u --username] [-p --password] [-T --tenant] [-M --message]")
        void receive(@ShellOption(value={"-H", "--host"}, defaultValue="", help="The address that the AMQP_NETWORK is running on") final String host,
                     @ShellOption(value={"-P", "--port"}, defaultValue="0", help="The port that the AMQP_NETWORK is listening") final int port,
                     @ShellOption(value={"-u", "--username"}, defaultValue="", help="Credentials to use to connect to the upperbound endpoint") final String username,
                     @ShellOption(value={"-p", "--password"}, defaultValue="", help="Credentials to use to connect to the upperbound endpoint") final String password,
                     @ShellOption(value={"-T", "--tenant"}, defaultValue="", help="The tenant you want to connect to") final String tenant,
                     @ShellOption(value={"-M", "--message"}, defaultValue="", valueProvider= MessageTypeProvider.class, help ="Specify the message type: '"+ClientConfig.TYPE_ALL+"', '"+ClientConfig.TYPE_EVENT+"', '"+ClientConfig.TYPE_TELEMETRY+"'" ) final String message) {
            try {
                final ClientConfig fork = (ClientConfig) clientConfig.clone();
                if (!setupConfiguration(fork, host, port, username, password, tenant, message)) {
                    return;
                }
                //create the connection
                try{
                    clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                    latch = new CountDownLatch(1);
                    final Receiver receiverInstance = new Receiver(clientFactory, vertx, fork);
                    receiverInstance.start(latch);
                    //to handle forced interruption
                    Signal.handle(new Signal("INT"), new SignalHandler() {
                        // Signal handler method
                        public void handle(final Signal signal) {
                            latch.countDown();
                        }
                    });
                    // if everything went according to plan, the next step will block forever
                    latch.await();
                } catch(final Exception e) {
                    log.error("Error occurred during initialization of connection: {}", e.getMessage());
                }
            } catch (final CloneNotSupportedException e) {
                System.out.println("Unable to clone the configuration: "+e.getMessage());
            }
        }

        @ShellMethod("Send a command\n" +
                "\t\t\t\t[-H --host] [-P --port] [-u --username] [-p --password] [-T --tenant]")
        void sendCommand(@ShellOption(value={"-H", "--host"}, defaultValue="", help="The address that the AMQP_NETWORK is running on") final String host,
                     @ShellOption(value={"-P", "--port"}, defaultValue="0", help="The port that the AMQP_NETWORK is listening") final int port,
                     @ShellOption(value={"-u", "--username"}, defaultValue="", help="Credentials to use to connect to the upperbound endpoint") final String username,
                     @ShellOption(value={"-p", "--password"}, defaultValue="", help="Credentials to use to connect to the upperbound endpoint") final String password,
                     @ShellOption(value={"-T", "--tenant"}, defaultValue="", help="The tenant you want to connect to") final String tenant){
            try {
                final ClientConfig fork = (ClientConfig) clientConfig.clone();
                if (!setupConfiguration(fork, host, port, username, password, tenant, "")) {
                    return;
                }
                //create the connection
                try {
                    clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                    latch = new CountDownLatch(1);
                    final Commander commanderInstance = new Commander(clientFactory, vertx, fork, inputReader);
                    commanderInstance.start(latch);
                    //to handle forced interruption
                    Signal.handle(new Signal("INT"), new SignalHandler() {
                        // Signal handler method
                        public void handle(final Signal signal) {
                            latch.countDown();
                        }
                    });
                    // if everything went according to plan, the next step will block forever
                    latch.await();
                } catch(final Exception e) {
                    log.error("Error occurred during initialization of connection: {}", e.getMessage());
                }
            } catch (final CloneNotSupportedException e) {
                System.out.println("Unable to clone the configuration: "+e.getMessage());
            }
        }

    }

    @ShellComponent
    @ShellCommandGroup("Southbounds Commands (Device)")
    class Adapter{
        @ShellMethod("Send telemetry data or events to Hono through AMQP adapter.\n" +
                "\t\t\tThe default configuration will search for credentials to proceed as an authenticated device.\n" +
                "\t\t\tIf no credentials were provided the connection will proceed as unauthenticated so be sure to set rightly the address.\n" +
                "\t\t\t\t[-H --host] [-P --port] [-u --username] [-p --password] [-c --certPath] [-k --keyPath] [-t --trustStorePath] [-A --address] [-M --message]")
        void send(@ShellOption(value={"-H", "--host"}, defaultValue="", help="The host name that the AMQP adapter is running on") final String host,
                     @ShellOption(value={"-P", "--port"}, defaultValue="0", help="The port that the adapter is listening for incoming connections") final int port,
                     @ShellOption(value={"-u", "--username"}, defaultValue="", help="Credentials used to register the device. If provided will have priority over certificates") final String username,
                     @ShellOption(value={"-p", "--password"}, defaultValue="", help="Credentials used to register the device. If provided will have priority over certificates") final String password,
                     @ShellOption(value={"-c", "--certPath"}, defaultValue="", help="Example - relative path: config/hono-demo-certs-jar/device-cert.pem") final String certPath,
                     @ShellOption(value={"-k", "--keyPath"}, defaultValue="", help="Example - relative path: config/hono-demo-certs-jar/device-key.pem") final String keyPath,
                     @ShellOption(value={"-t", "--trustStorePath"}, defaultValue="", help="Example - relative path: config/hono-demo-certs-jar/trusted-certs.pem") final String trustStorePath,
                     @ShellOption(value={"-A", "--address"}, defaultValue="", help="The AMQP 1.0 message address. Example - authenticated: telemetry. Unauthenticated must be verbose: telemetry/tenant-id/device-id") final String address,
                     @ShellOption(value={"-M", "--message"}, defaultValue="", help ="The payload of the message to send" ) final String payload) {
            try {
                final ClientConfig fork = (ClientConfig) clientConfig.clone();
                setupConfigurationForAdapter(fork, host, port, username, password, certPath, keyPath, trustStorePath, address, payload);
                //create the connection
                try {
                    clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                    latch = new CountDownLatch(1);
                    final TelemetryAndEvent TaEAPIInstance = new TelemetryAndEvent(vertx, ctx, fork);
                    TaEAPIInstance.start(latch);
                    //to handle forced interruption
                    Signal.handle(new Signal("INT"), new SignalHandler() {
                        // Signal handler method
                        public void handle(final Signal signal) {
                            latch.countDown();
                        }
                    });
                    // if everything went according to plan, the next step will block forever
                    latch.await();
                } catch(final Exception e) {
                    log.error("Error occurred during initialization of connection: {}", e.getMessage());
                }
            } catch (final CloneNotSupportedException e) {
                System.out.println("Unable to clone the configuration: "+e.getMessage());
            }
        }

        @ShellMethod("Simulate a device receiving and responding to command request messages through AMQP adapter.\n" +
                "\t\t\t\t[-H --host] [-P --port] [-u --username] [-p --password] [-c --certPath] [-k --keyPath] [-t --trustStorePath]")
        void receiveCommand(@ShellOption(value={"-H", "--host"}, defaultValue="", help="The host name that the AMQP adapter is running on") final String host,
                            @ShellOption(value={"-P", "--port"}, defaultValue="0", help="The port that the adapter is listening for incoming connections") final int port,
                            @ShellOption(value={"-u", "--username"}, defaultValue="", help="Credentials used to register the device. If provided will have priority over certificates") final String username,
                            @ShellOption(value={"-p", "--password"}, defaultValue="", help="Credentials used to register the device. If provided will have priority over certificates") final String password,
                            @ShellOption(value={"-c", "--certPath"}, defaultValue="", help="Example - relative path: config/hono-demo-certs-jar/device-cert.pem") final String certPath,
                            @ShellOption(value={"-k", "--keyPath"}, defaultValue="", help="Example - relative path: config/hono-demo-certs-jar/device-key.pem") final String keyPath,
                            @ShellOption(value={"-t", "--trustStorePath"}, defaultValue="", help="Example - relative path: config/hono-demo-certs-jar/trusted-certs.pem") final String trustStorePath) {
            try {
                final ClientConfig fork = (ClientConfig) clientConfig.clone();
                setupConfigurationForAdapter(fork, host, port, username, password, certPath, keyPath, trustStorePath, "", "");
                //create the connection
                try {
                    clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                    latch = new CountDownLatch(1);
                    final CommandAndControl CaCAPIInstance = new CommandAndControl(vertx, ctx, fork);
                    CaCAPIInstance.start(latch);
                    //to handle forced interruption
                    Signal.handle(new Signal("INT"), new SignalHandler() {
                        // Signal handler method
                        public void handle(final Signal signal) {
                            latch.countDown();
                        }
                    });
                    // if everything went according to plan, the next step will block forever
                    latch.await();
                } catch(final Exception e) {
                    log.error("Error occurred during initialization of connection: {}", e.getMessage());
                }
            } catch (final CloneNotSupportedException e) {
                System.out.println("Unable to clone the configuration: "+e.getMessage());
            }
        }
    }

    private boolean setupConfiguration(final ClientConfig clientConfig, final String host, final int port, final String username, final String password, final String tenant, final String messageType){
        //setup config with parameters
        if (!host.isEmpty()) {
            clientConfig.honoClientConfig.setHost(host);
        }
        if (port!=0) {
            clientConfig.honoClientConfig.setPort(port);
        }
        if (!username.isEmpty()) {
            clientConfig.honoClientConfig.setUsername(username);
        }
        if (!password.isEmpty()) {
            clientConfig.honoClientConfig.setPassword(password);
        }
        if (!tenant.isEmpty() && !tenant.equals(clientConfig.tenantId)) {
            clientConfig.tenantId=tenant;
        }
        if (!messageType.isEmpty() && !messageType.equals(clientConfig.messageType)) {
            if (messageType.equals(ClientConfig.TYPE_ALL) || messageType.equals(ClientConfig.TYPE_EVENT) || messageType.equals(ClientConfig.TYPE_TELEMETRY)) {
                clientConfig.messageType=messageType;
            } else {
                shellHelper.printError(messageType.concat("Unknown message type. Type help <command> to get the values available."));
                return false;
            }
        }
        return true;
    }

    private void setupConfigurationForAdapter(final ClientConfig clientConfig, final String host, final int port, final String username, final String password, final String certPath, final String keyPath, final String trustStorePath, final String address, final String payload){
        setupConfiguration(clientConfig, host, port, "", "", "", "");
        if (!username.isEmpty()||!password.isEmpty()) {
            shellHelper.printInfo("Authentication with username and password will be used.");
            if (!username.isEmpty()) {
                clientConfig.honoClientConfig.setUsername(username);
            }
            if (!password.isEmpty()) {
                clientConfig.honoClientConfig.setPassword(password);
            }
        } else if (!certPath.isEmpty()||!keyPath.isEmpty()||!trustStorePath.isEmpty()) {
            if (!Strings.isNullOrEmpty(clientConfig.honoClientConfig.getUsername()) && !Strings.isNullOrEmpty(clientConfig.honoClientConfig.getPassword())) {
                shellHelper.printInfo("Authentication with certificates will be used. Hostname verification required: disabled.");
            }
            if (!certPath.isEmpty()) {
                clientConfig.honoClientConfig.setCertPath(certPath);
            }
            if (!keyPath.isEmpty()) {
                clientConfig.honoClientConfig.setKeyPath(keyPath);
            }
            if (!trustStorePath.isEmpty()) {
                clientConfig.honoClientConfig.setTrustStorePath(trustStorePath);
            }
            clientConfig.honoClientConfig.setHostnameVerificationRequired(false);
        } else {
            shellHelper.printInfo("The connection will proceed as an unauthenticated device. Be sure the adapter is configured to accept the connection and to provide the right address.");
        }
        if (!address.isEmpty() && !address.equals(clientConfig.messageAddress)) {
            clientConfig.messageAddress=address;
        }
        if (!payload.isEmpty() && !payload.equals(clientConfig.payload)) {
            clientConfig.payload=payload;
        }
    }

}
