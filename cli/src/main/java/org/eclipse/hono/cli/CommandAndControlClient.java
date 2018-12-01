/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli;

import java.net.HttpURLConnection;

import javax.annotation.PostConstruct;

import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A client for accessing Hono's Command and Control APIs to
 * receive commands and sends command response messages via the AMQP adapter.
 */
@Component
@Profile("amqp-command")
public class CommandAndControlClient extends AmqpCliClient {

    private ProtonSender sender;

    @PostConstruct
    void start() {
        startCommandReceiver((d, m) -> {

            String commandPayload = null;
            if (m.getBody() instanceof Data) {
                final byte[] body = (((Data) m.getBody()).getValue()).getArray();
                commandPayload = new String(body);
            }
            writer.printf("Received Command Message : [Command name: %s, Command payload: %s] %n", m.getSubject(),
                    commandPayload).flush();

            final Message commandResponse = ProtonHelper.message(m.getReplyTo(), "OK: " + m.getSubject());
            commandResponse.setCorrelationId(m.getCorrelationId());
            MessageHelper.addProperty(commandResponse, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
            commandResponse.setContentType(m.getContentType());
            sender.send(commandResponse, delivery -> {
                if (delivery.remotelySettled()) {
                    writer.printf("Command response sent [outcome: %s] %n", delivery.getRemoteState());
                } else {
                    writer.println("Application did not settle command response message");
                }
                writer.flush();
            });
        })
        .otherwise(t -> {
            writer.printf("Fail to create command receiver link [reason: %s] %n", t.getMessage()).flush();
            System.exit(1);
            return null;
        });
    }

    private Future<ProtonReceiver> startCommandReceiver(final ProtonMessageHandler msgHandler) {
        return connectToAdapter()
        .compose(con -> {
            adapterConnection = con;
            return createSender();
        }).compose(s -> {
            sender = s;
            return subscribeToCommands(msgHandler);
        });
    }

    /**
     * Opens a receiver link to receive commands from the adapter.
     *
     * @param msgHandler The handler to invoke when a command message is received.
     * @return A succeeded future with the created receiver link or a failed future
     *         if the receiver link cannot be created.
     *
     */
    private Future<ProtonReceiver> subscribeToCommands(final ProtonMessageHandler msgHandler) {
        final Future<ProtonReceiver> result = Future.future();
        final ProtonReceiver receiver = adapterConnection.createReceiver(CommandConstants.COMMAND_ENDPOINT);
        receiver.setQoS(ProtonQoS.AT_LEAST_ONCE);
        receiver.handler(msgHandler);
        receiver.openHandler(result);
        receiver.open();
        return result.map(recver -> {
            writer.println("Device is now ready to receive commands (Press Ctrl + c to terminate)");
            writer.flush();
            return recver;
        });
    }
}
