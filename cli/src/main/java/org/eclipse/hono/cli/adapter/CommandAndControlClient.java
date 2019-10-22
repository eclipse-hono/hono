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

package org.eclipse.hono.cli.adapter;

import java.net.HttpURLConnection;

import javax.annotation.PostConstruct;

import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A connection for accessing Hono's Command and Control APIs to
 * receive commands and sends command response messages via the AMQP org.eclipse.hono.cli.app.adapter.
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
            final boolean isOneWay = m.getReplyTo() == null;
            if (isOneWay) {
                writer.printf("received one-way command [name: %s]: %s%n", m.getSubject(), commandPayload);
                writer.flush();
            } else {
                writer.printf("received command [name: %s]: %s%n", m.getSubject(), commandPayload);
                writer.flush();

                final Message commandResponse = ProtonHelper.message(m.getReplyTo(), "OK: " + m.getSubject());
                commandResponse.setCorrelationId(m.getCorrelationId());
                MessageHelper.addProperty(commandResponse, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
                commandResponse.setContentType(m.getContentType());
                sender.send(commandResponse, delivery -> {
                    if (delivery.remotelySettled()) {
                        writer.printf("sent response to command [name: %s, outcome: %s]%n", m.getSubject(), delivery.getRemoteState().getType());
                    } else {
                        writer.println("application did not settle command response message");
                    }
                    writer.flush();
                });
            }
        })
        .otherwise(t -> {
            writer.printf("failed to create command receiver link: %s%n", t.getMessage()).flush();
            System.exit(1);
            return null;
        });
    }

    private Future<ProtonReceiver> startCommandReceiver(final ProtonMessageHandler msgHandler) {
        return connectToAdapter()
        .compose(con -> {
            log.info("connection to AMQP adapter established");
            adapterConnection = con;
            return createSender();
        }).compose(s -> {
            sender = s;
            return subscribeToCommands(msgHandler);
        });
    }

    /**
     * Opens a receiver link to receive commands from the org.eclipse.hono.cli.app.adapter.
     *
     * @param msgHandler The handler to invoke when a command message is received.
     * @return A succeeded future with the created receiver link or a failed future
     *         if the receiver link cannot be created.
     *
     */
    private Future<ProtonReceiver> subscribeToCommands(final ProtonMessageHandler msgHandler) {
        final Promise<ProtonReceiver> result = Promise.promise();
        final ProtonReceiver receiver = adapterConnection.createReceiver(CommandConstants.COMMAND_ENDPOINT);
        receiver.setQoS(ProtonQoS.AT_LEAST_ONCE);
        receiver.handler(msgHandler);
        receiver.openHandler(result);
        receiver.open();
        return result.future().map(recver -> {
            writer.println("Device is now ready to receive commands (Press Ctrl + c to terminate)");
            writer.flush();
            return recver;
        });
    }
}
