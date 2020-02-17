
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
import io.vertx.core.WorkerExecutor;
import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.cli.ClientConfig;
import org.eclipse.hono.cli.shell.InputReader;
import org.eclipse.hono.cli.shell.ShellHelper;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import static org.eclipse.hono.cli.ClientConfig.*;

/**
 * A base class for implementing command line clients that access Hono's
 * northbound APIs.
 *
 */
@ShellComponent
@ShellCommandGroup("Hono Commands")
public class ApplicationClient extends AbstractCliClient {
    private ClientConfig clientConfig = new ClientConfig();
    /**
     * To stop the internal executions.
     */
    private CountDownLatch latch;

    /**
     * Sets the Spring environment.
     *
     * @param env The environment.
     * @throws NullPointerException if environment is {@code null}.
     */
    @Autowired
    public final void setActiveProfiles(final Environment env) {
        Objects.requireNonNull(env);
        activeProfiles = Arrays.asList(env.getActiveProfiles());
    }

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
        clientConfig.honoClientConfig = Objects.requireNonNull(honoClientConfig);
    }
    @Autowired
    public final void setApplicationClientFactory(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Autowired
    ShellHelper shellHelper;

    @Autowired
    InputReader inputReader;

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
            if(!setupConfiguration(fork.honoClientConfig,host,port,username,password,tenant,message)) return;
            //create the connection
            try{
                clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                latch = new CountDownLatch(1);
                Receiver receiverInstance = new Receiver(clientFactory,vertx,clientConfig);
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

    @ShellMethod("Start the commander [-H --host] [-P --port] [-u --username] [-p --password] [-T --tenant] [-M --message]")
    void commander(@ShellOption(value={"-H", "--host"}, defaultValue="") String host,
                   @ShellOption(value={"-P", "--port"}, defaultValue="0", help="") int port,
                   @ShellOption(value={"-u", "--username"}, defaultValue="", help="") String username,
                   @ShellOption(value={"-p", "--password"}, defaultValue="", help="") String password,
                   @ShellOption(value={"-T", "--tenant"}, defaultValue="DEFAULT_TENANT", help="") String tenant,
                   @ShellOption(value={"-M", "--message"}, defaultValue=TYPE_ALL, valueProvider= MessageTypeProvider.class, help ="Specify the message type '"+TYPE_ALL+"', '"+TYPE_EVENT+"', '"+TYPE_TELEMETRY+"'" ) String message) {
        try {
            ClientConfig fork = (ClientConfig) clientConfig.clone();
            if(!setupConfiguration(fork.honoClientConfig,host,port,username,password,tenant,message)) return;
            //create the connection
            try{
                clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, fork.honoClientConfig));
                latch = new CountDownLatch(1);
                Commander commanderInstance = new Commander(clientFactory,vertx,clientConfig,inputReader);
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

    public boolean setupConfiguration(ClientConfigProperties honoClientConfig, String host,int port,String username,String password,String tenant,String message){
        //setup config with parameters
        if(!host.isEmpty()) honoClientConfig.setHost(host);
        if(port!=0) honoClientConfig.setPort(port);
        if(!username.isEmpty()) honoClientConfig.setUsername(username);
        if(!password.isEmpty()) honoClientConfig.setPassword(password);
        if(!tenant.isEmpty() && !tenant.equals(clientConfig.tenantId)) clientConfig.tenantId=tenant;
        if(!message.isEmpty() && !message.equals(clientConfig.messageType))
            if(message.equals(TYPE_ALL) || message.equals(TYPE_EVENT) || message.equals(TYPE_TELEMETRY) ) clientConfig.messageType=message;
            else{ shellHelper.printError(message.concat("Unknown message type. Type help <command> to get the values available.")); return false;}
            return true;
    }

}