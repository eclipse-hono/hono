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

import io.vertx.core.Vertx;
import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.cli.ClientConfig;
import org.eclipse.hono.cli.adapter.CommandAndControl;
import org.eclipse.hono.cli.adapter.TelemetryAndEvent;
import org.eclipse.hono.cli.shell.InputReader;
import org.eclipse.hono.cli.shell.ShellHelper;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import static org.eclipse.hono.cli.ClientConfig.*;

@Component
public class ApplicationClient extends AbstractCliClient {
    @Value(value = "${tenant.id}")
    private String tenantId;
    @Value(value = "${device.id}")
    private String deviceId;
    @Value(value = "${message.type}")
    private String messageType;
    @Value(value = "${connection.retryInterval:1000}")
    private int connectionRetryInterval;
    @Value(value = "${command.timeoutInSeconds:60}")
    private int commandTimeoutInSeconds;
    @Value(value = "${message.address:127.0.0.1}")//The address to set on the message being sent.
    private String messageAddress;
    @Value(value = "${message.payload:}")//The payload to send.
    private String payload;
    /**
     * Class that contains all parameters for the connection to Hono and for the execution of the commands.
     * Handling it a part allow to clone it and maintain a default config during the whole execution.
     */
    private ClientConfig clientConfig;
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

    @Autowired
    public final void setClientConfigProperties(final ClientConfigProperties honoClientConfig) {
        clientConfig = new ClientConfig(tenantId, deviceId, messageType, connectionRetryInterval, commandTimeoutInSeconds, messageAddress, payload);
        clientConfig.honoClientConfig = Objects.requireNonNull(honoClientConfig);
    }

    @Autowired
    public final void setApplicationClientFactory(final ApplicationClientFactory factory) {
        this.clientFactory = Objects.requireNonNull(factory);
    }

//    @Autowired
//    public final void setAmqpAdapterClientFactory(final AmqpAdapterClientFactory factory) {
//        this.adapterFactory = Objects.requireNonNull(factory);
//    }

    //TODO set all parameters

    @Autowired
    ShellHelper shellHelper;

    @Autowired
    InputReader inputReader;



    /**
     * A base class for implementing command line clients that access Hono's
     * northbound APIs.
     *
     */
    @ShellComponent
    @ShellCommandGroup("Northbounds Commands (Application)")
    class Apllication{
        /**
         * Starts the reciver
         */
        //TODO help in the commands
        @ShellMethod("Start the receiver [-H --host] [-P --port] [-u --username] [-p --password] [-T --tenant] [-M --message]")
        void receiver(@ShellOption(value={"-H", "--host"}, defaultValue="") String host,
                      @ShellOption(value={"-P", "--port"}, defaultValue="0", help="") int port,
                      @ShellOption(value={"-u", "--username"}, defaultValue="", help="") String username,
                      @ShellOption(value={"-p", "--password"}, defaultValue="", help="") String password,
                      @ShellOption(value={"-T", "--tenant"}, defaultValue="DEFAULT_TENANT", help="") String tenant,
                      @ShellOption(value={"-M", "--message"}, defaultValue=TYPE_ALL, valueProvider= MessageTypeProvider.class, help ="Specify the message type '"+TYPE_ALL+"', '"+TYPE_EVENT+"', '"+TYPE_TELEMETRY+"'" ) String message) {
            try {
                ClientConfig fork = (ClientConfig) clientConfig.clone();
                if(!setupConfiguration(fork,host,port,username,password,tenant,message,messageAddress,payload)){
                    shellHelper.printError(message.concat("Unknown message type. Type help <command> to get the values available."));
                    return;
                }
                //create the connection
                try{
                    clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                    latch = new CountDownLatch(1);
                    Receiver receiverInstance = new Receiver(clientFactory,vertx,fork);
                    receiverInstance.start(latch);
                    //to handle forced interruption
                    Signal.handle(new Signal("INT"), new SignalHandler() {
                        // Signal handler method
                        public void handle(Signal signal) {
                            latch.countDown();
                        }
                    });
                    // if everything went according to plan, the next step will block forever
                    latch.await();
                }catch(Exception e){
                    log.error("Error occurred during initialization of connection: {}", e.getMessage());
                }
            } catch (CloneNotSupportedException e) {
                System.out.println("Unable to clone the configuration: "+e.getMessage());
            }
        }

        @ShellMethod("Send a command [-H --host] [-P --port] [-u --username] [-p --password] [-T --tenant] [-M --message]")
        void command(@ShellOption(value={"-H", "--host"}, defaultValue="") String host,
                     @ShellOption(value={"-P", "--port"}, defaultValue="0", help="") int port,
                     @ShellOption(value={"-u", "--username"}, defaultValue="", help="") String username,
                     @ShellOption(value={"-p", "--password"}, defaultValue="", help="") String password,
                     @ShellOption(value={"-T", "--tenant"}, defaultValue="DEFAULT_TENANT", help="") String tenant,
                     @ShellOption(value={"-M", "--message"}, defaultValue=TYPE_ALL, valueProvider= MessageTypeProvider.class, help ="Specify the message type '"+TYPE_ALL+"', '"+TYPE_EVENT+"', '"+TYPE_TELEMETRY+"'" ) String message) {
            try {
                ClientConfig fork = (ClientConfig) clientConfig.clone();
                if(!setupConfiguration(fork,host,port,username,password,tenant,message,messageAddress,payload)){
                    shellHelper.printError(message.concat("Unknown message type. Type help <command> to get the values available."));
                    return;
                }
                //create the connection
                try{
                    clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                    latch = new CountDownLatch(1);
                    Commander commanderInstance = new Commander(clientFactory,vertx,fork,inputReader);
                    commanderInstance.start(latch);
                    //to handle forced interruption
                    Signal.handle(new Signal("INT"), new SignalHandler() {
                        // Signal handler method
                        public void handle(Signal signal) {
                            latch.countDown();
                        }
                    });
                    // if everything went according to plan, the next step will block forever
                    latch.await();
                }catch(Exception e){
                    log.error("Error occurred during initialization of connection: {}", e.getMessage());
                }
            } catch (CloneNotSupportedException e) {
                System.out.println("Unable to clone the configuration: "+e.getMessage());
            }
        }

    }

    @ShellComponent
    @ShellCommandGroup("Southbounds Commands (Device)")
    class Adapter{
        @ShellMethod("Start a device for sending telemetry data or events to Hono through AMQP adapter. [-H --host] [-P --port] [-u --username] [-p --password] [-T --tenant] [-M --message]")
        void devicesend(@ShellOption(value={"-H", "--host"}, defaultValue="") String host,
                        @ShellOption(value={"-P", "--port"}, defaultValue="0", help="") int port,
                        @ShellOption(value={"-u", "--username"}, defaultValue="", help="") String username,
                        @ShellOption(value={"-p", "--password"}, defaultValue="", help="") String password,
                        @ShellOption(value={"-T", "--tenant"}, defaultValue="DEFAULT_TENANT", help="") String tenant,
                        @ShellOption(value={"-M", "--message"}, defaultValue=TYPE_ALL, valueProvider= ClientConfig.MessageTypeProvider.class, help ="Specify the message type '"+TYPE_ALL+"', '"+TYPE_EVENT+"', '"+TYPE_TELEMETRY+"'" ) String message) {
            try {
                ClientConfig fork = (ClientConfig) clientConfig.clone();
                if(!setupConfiguration(fork,host,port,username,password,tenant,message,messageAddress,payload)){
                    shellHelper.printError(message.concat("Unknown message type. Type help <command> to get the values available."));
                    return;
                }
                //create the connection
                try{
                    clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                    latch = new CountDownLatch(1);
                    TelemetryAndEvent TaEAPIInstance = new TelemetryAndEvent(vertx,fork);
                    TaEAPIInstance.start(latch);
                    //to handle forced interruption
                    Signal.handle(new Signal("INT"), new SignalHandler() {
                        // Signal handler method
                        public void handle(Signal signal) {
                            latch.countDown();
                        }
                    });
                    // if everything went according to plan, the next step will block forever
                    latch.await();
                }catch(Exception e){
                    log.error("Error occurred during initialization of connection: {}", e.getMessage());
                }
            } catch (CloneNotSupportedException e) {
                System.out.println("Unable to clone the configuration: "+e.getMessage());
            }
        }

        @ShellMethod("Start a device receiving and responding to command request messages through AMQP adapter. [-H --host] [-P --port] [-u --username] [-p --password] [-T --tenant] [-M --message]")
        void devicecommand(@ShellOption(value={"-H", "--host"}, defaultValue="") String host,
                           @ShellOption(value={"-P", "--port"}, defaultValue="0", help="") int port,
                           @ShellOption(value={"-u", "--username"}, defaultValue="", help="") String username,
                           @ShellOption(value={"-p", "--password"}, defaultValue="", help="") String password,
                           @ShellOption(value={"-T", "--tenant"}, defaultValue="DEFAULT_TENANT", help="") String tenant,
                           @ShellOption(value={"-M", "--message"}, defaultValue=TYPE_ALL, valueProvider= MessageTypeProvider.class, help ="Specify the message type '"+TYPE_ALL+"', '"+TYPE_EVENT+"', '"+TYPE_TELEMETRY+"'" ) String message) {
            try {
                ClientConfig fork = (ClientConfig) clientConfig.clone();
                if(!setupConfiguration(fork,host,port,username,password,tenant,message,messageAddress,payload)){
                    shellHelper.printError(message.concat("Unknown message type. Type help <command> to get the values available."));
                    return;
                }
                //create the connection
                try{
                    clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                    latch = new CountDownLatch(1);
                    CommandAndControl CaCAPIInstance = new CommandAndControl(vertx,fork);
                    CaCAPIInstance.start(latch);
                    //to handle forced interruption
                    Signal.handle(new Signal("INT"), new SignalHandler() {
                        // Signal handler method
                        public void handle(Signal signal) {
                            latch.countDown();
                        }
                    });
                    // if everything went according to plan, the next step will block forever
                    latch.await();
                }catch(Exception e){
                    log.error("Error occurred during initialization of connection: {}", e.getMessage());
                }
            } catch (CloneNotSupportedException e) {
                System.out.println("Unable to clone the configuration: "+e.getMessage());
            }
        }
    }

    public boolean setupConfiguration(ClientConfig clientConfig, String host,int port,String username,String password,String tenant,String message, String messageAddress, String payload){
        //setup config with parameters
        if(!host.isEmpty()) clientConfig.honoClientConfig.setHost(host);
        if(port!=0) clientConfig.honoClientConfig.setPort(port);
        if(!username.isEmpty()) clientConfig.honoClientConfig.setUsername(username);
        if(!password.isEmpty()) clientConfig.honoClientConfig.setPassword(password);
        if(!tenant.isEmpty() && !tenant.equals(clientConfig.tenantId)) clientConfig.tenantId=tenant;
        if(!messageAddress.isEmpty() && !tenant.equals(clientConfig.messageAddress)) clientConfig.messageAddress=messageAddress;
        if(!payload.isEmpty() && !tenant.equals(clientConfig.payload)) clientConfig.payload=payload;
        if(!message.isEmpty() && !message.equals(clientConfig.messageType))
            if(message.equals(TYPE_ALL) || message.equals(TYPE_EVENT) || message.equals(TYPE_TELEMETRY) ) clientConfig.messageType=message;
            else{ return false; }
        return true;
    }

}
