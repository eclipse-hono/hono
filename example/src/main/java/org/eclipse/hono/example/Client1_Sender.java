/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.example;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.hono.client.api.model.Permission.ADMINISTRATE;
import static org.eclipse.hono.client.api.model.Permission.RECEIVE;
import static org.eclipse.hono.client.api.model.Permission.SEND;
import static org.eclipse.hono.example.Constants.CLIENT_ID_1;
import static org.eclipse.hono.example.Constants.CLIENT_ID_2;
import static org.eclipse.hono.example.Constants.TOPIC_1;
import static org.eclipse.hono.example.Constants.TOPIC_2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.function.Consumer;

import org.eclipse.hono.client.AmqpConnectionConfig;
import org.eclipse.hono.client.AmqpConnectorClient;
import org.eclipse.hono.client.api.ConnectionConfig;
import org.eclipse.hono.client.api.model.Message;
import org.eclipse.hono.client.api.model.Permissions;
import org.eclipse.hono.client.api.model.TopicAcl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client1_Sender using the IoT Connector client to send/receive messages.
 */
public final class Client1_Sender
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Client1_Sender.class);

    public static void main(final String[] args) throws Exception {
        final ConnectionConfig connectionConfig = new AmqpConnectionConfig(Constants.AMQP_URI);
        final TopicAcl topic1Acl1_Admin = new TopicAcl(Constants.CLIENT_ID_1,
                new Permissions(SEND, RECEIVE, ADMINISTRATE));
        final AmqpConnectorClient client = new AmqpConnectorClient(Constants.CLIENT_ID_1, connectionConfig);

        client.connect();

        //client1 get administration permission and send/receive for topic1 and topic2
        //client2 is only allowed to receive messages on topic1
        client.registerTopic(CLIENT_ID_1, TOPIC_1, topic1Acl1_Admin);
        client.registerTopic(CLIENT_ID_1, TOPIC_1, new TopicAcl(CLIENT_ID_2, new Permissions(RECEIVE)));
        client.registerTopic(CLIENT_ID_1, TOPIC_2, topic1Acl1_Admin);

        // handle messages for both topics
        client.subscriptionForTopic(TOPIC_1, getDemoConsumer());
        client.subscriptionForTopic(TOPIC_2, getDemoConsumer());

        // wait for some input to send message
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter some message to send (empty message to quit): ");
        String input;
        while ((input = reader.readLine()) != null && !input.isEmpty()) {
            client.sendMessage(TOPIC_1, Collections.<String, String> emptyMap(), getPayload(input));
            client.sendMessage(TOPIC_2, Collections.<String, String> emptyMap(), getPayload(input));
        }

        LOGGER.info("Disconnecting client...");
        client.disconnect();
    }

    private static ByteBuffer getPayload(final String input) {
        return ByteBuffer.wrap(("{\"message\":\"" + input + "\"}").getBytes(UTF_8));
    }

    private static Consumer<Message> getDemoConsumer() {
        return m -> LOGGER.info("[{}] received message for topic [{}]: {} ",
           CLIENT_ID_1, m.getTopic(), UTF_8.decode(m.getPayload()).toString());
    }
}
