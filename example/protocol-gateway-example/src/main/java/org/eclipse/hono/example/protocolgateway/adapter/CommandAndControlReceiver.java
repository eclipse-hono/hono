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

package org.eclipse.hono.example.protocolgateway.adapter;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cli.adapter.AmqpCliClient;
import org.eclipse.hono.cli.client.ApplicationClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.example.protocolgateway.interfaces.CommandHandler;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.vertx.core.Promise;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Command and receiver listener using methods and properties from {@link AmqpCliClient} to simplify handling.
 * <p>
 * based loosely on {@link org.eclipse.hono.cli.adapter.CommandAndControl}
 *
 * @see org.eclipse.hono.cli.adapter.CommandAndControl
 */
@Component
public class CommandAndControlReceiver extends ApplicationClient {

    private final AmqpCliClient client = new AmqpCliClient(vertx, ctx, clientConfig);
    private static final Logger log = LoggerFactory.getLogger(CommandAndControlReceiver.class);
    private ProtonSender sender;
    private CommandHandler commandHandler;

    /**
     * Listen for incoming commands.
     */
    public void listenCommands() {
        final ProtonMessageHandler messageHandler = (d, m) -> {
            String commandPayload = null;
            if (m.getBody() instanceof Data) {
                final byte[] body = (((Data) m.getBody()).getValue()).getArray();
                commandPayload = new String(body);
            }
            final boolean isOneWay = m.getReplyTo() == null;
            if (isOneWay) {
                log.info(String.format("received one-way command [name: %s]: %s%n", m.getSubject(), commandPayload));
                this.commandHandler.handleCommand(commandPayload, m.getSubject(), m.getContentType(), isOneWay);
            } else {
                log.info(String.format("received command [name: %s]: %s%n", m.getSubject(), commandPayload));
                final String responseMessagePayload = this.commandHandler.handleCommand(commandPayload, m.getSubject(), m.getContentType(), isOneWay);

                final Message commandResponse = ProtonHelper.message(m.getReplyTo(), responseMessagePayload);
                commandResponse.setCorrelationId(m.getCorrelationId());
                MessageHelper.addProperty(commandResponse, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
                commandResponse.setContentType(m.getContentType());
                this.sender.send(commandResponse, delivery -> {
                    if (delivery.remotelySettled()) {
                        log.info(String.format("sent response to command [name: %s, outcome: %s]%n", m.getSubject(), delivery.getRemoteState().getType()));
                    } else {
                        log.info("application did not settle command response message");
                    }
                });
            }
        };

        client.connectToAdapter()
                .compose(con -> {
                    client.adapterConnection = con;
                    return client.createSender();
                }).map(s -> {
            this.sender = s;
            final Promise<ProtonReceiver> result = Promise.promise();
            final ProtonReceiver receiver = client.adapterConnection.createReceiver(CommandConstants.COMMAND_ENDPOINT);
            receiver.setQoS(ProtonQoS.AT_LEAST_ONCE);
            receiver.handler(messageHandler);
            receiver.openHandler(result);
            receiver.open();
            return result.future().map(recver -> {
                log.info("Command receiver ready");
                return recver;
            });
        });

    }

    /**
     * Sets AMQP client connection properties and command handler {@link CommandHandler}.
     *
     * @param host           AMQP Hono adapter IP address
     * @param port           AMQP Hono adapter port
     * @param username       username consists of DEVICE_ID@TENANT_ID
     * @param password       device credentials
     * @param commandHandler handler for incoming commands
     */
    public void setAMQPClientProps(final String host, final int port, final String username, final String password, final CommandHandler commandHandler) {
        final ClientConfigProperties props = new ClientConfigProperties();
        props.setHost(host);
        props.setPort(port);
        props.setUsername(username);
        props.setPassword(password);
        setClientConfigProperties(props);
        this.commandHandler = commandHandler;
    }
}
