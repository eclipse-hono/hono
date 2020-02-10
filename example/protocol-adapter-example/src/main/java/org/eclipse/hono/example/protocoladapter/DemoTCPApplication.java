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

package org.eclipse.hono.example.protocoladapter;

import io.vertx.core.Future;
import org.eclipse.hono.example.protocoladapter.controller.ProtocolAdapterExample;
import org.eclipse.hono.example.protocoladapter.interfaces.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Example Tcp server to send event and telemetry messages to Hono AMQP adapter and receives commands
 */
@SpringBootApplication
@ConfigurationProperties("app")
public class DemoTCPApplication {

    private static final Logger log = LoggerFactory.getLogger(DemoTCPApplication.class);
    private final ProtocolAdapterExample protocolAdapterExample;
    // TCP server properties
    private int serverPort;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    public DemoTCPApplication(ProtocolAdapterExample protocolAdapterExample) throws IOException {
        this.protocolAdapterExample = protocolAdapterExample;
    }

    public static void main(String[] args) throws IOException {
        SpringApplication.run(DemoTCPApplication.class, args);
        log.info("Start DemoTCPApplication");
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * Starts example tcp server listening to command to be relayed to the AMQP adapter
     */
    @PostConstruct
    public void startTcpServer() throws IOException {
        serverSocket = new ServerSocket(this.serverPort);

        while (true) {
            clientSocket = serverSocket.accept();
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String greeting;

            while (clientSocket.isConnected()) {
                greeting = in.readLine();
                if (greeting == null) {
                    close();
                    break;
                }

                switch (greeting) {
                    case "initConnection":
                        log.info("Command: initConnection");
                        out.println("host:");
                        String host = in.readLine();
                        out.println("port:");
                        int port = Integer.parseInt(in.readLine());
                        out.println("username (DEVICE_ID@TENANT_ID):");
                        String username = in.readLine();
                        out.println("password:");
                        String password = in.readLine();
                        initConnection(host, port, username, password);
                        out.println("OK");
                        break;
                    case "listenCommands":
                        log.info("Command: listenCommands");
                        listenCommands();
                        out.println("OK");
                        break;
                    case "sendAMQPMessage":
                        log.info("Command: sendAMQPMessage");
                        out.println("message address (\"telemetry\"/\"event\"):");
                        String messageAddress = in.readLine();
                        out.println("payload:");
                        String payload = in.readLine();
                        Future<String> amqpResponse = sendAMQPMessage(payload, messageAddress);
                        amqpResponse.setHandler(response -> {
                            if (response.succeeded()) {
                                out.println("OK");
                                log.info(String.format("sendAMQPMessage result: \"%s\"", response.result()));
                                out.println("response: " + response.result());
                            } else {
                                out.println("FAIL");
                            }
                        });
                        break;
                    default:
                        out.println("Unrecognized Command.\nCommands:\n - \"initConnection\"\n - \"listenCommands\"\n - \"sendAMQPMessage\"");
                        break;
                }
            }
        }
    }

    /**
     * Closes sockets and streams if client is disconnected
     */
    private void close() {
        try {
            in.close();
            out.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets connection properties, sets a commandHandler for incoming commands
     *
     * @param host     AMQP Hono adapter IP address
     * @param port     AMQP Hono adapter port
     * @param username username consists of DEVICE_ID@TENANT_ID
     * @param password device credentials
     */
    public void initConnection(String host, int port, String username, String password) {

        // Example command handler responds with time if incoming subject is "tellTime"
        CommandHandler commandHandler = (commandPayload, subject, contentType, isOneWay) -> {
            log.info(String.format("Got now command: \"%s\" for subject \"%s\"", commandPayload, subject));
            if (!isOneWay && subject.contains("tellTime")) {
                return String.format("myCurrentTime: %s",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())
                );
            }
            return "{}";
        };
        this.protocolAdapterExample.setAMQPClientProps(host, port, username, password, commandHandler);
    }

    /**
     * Starts listening to commands
     * <p>
     * Connection properties have to be set with {@link #initConnection(String, int, String, String) } beforehand
     */
    public void listenCommands() {
        this.protocolAdapterExample.listenCommands();
    }

    /**
     * Sends telemtry or event message to Hono AMQP adapter
     * <p>
     * Connection properties have to be set with {@link #initConnection(String, int, String, String) } beforehand
     *
     * @param payload        message payload
     * @param messageAddress address can be either "telemetry" or "event"
     * @return
     */
    public Future<String> sendAMQPMessage(String payload, String messageAddress) {
        return protocolAdapterExample.sendAMQPMessage(payload, messageAddress);
    }
}
