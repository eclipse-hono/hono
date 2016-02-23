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
import static org.eclipse.hono.example.Constants.CLIENT_ID_2;
import static org.eclipse.hono.example.Constants.TOPIC_1;
import static org.eclipse.hono.example.Constants.TOPIC_2;

import java.util.function.Consumer;

import org.eclipse.hono.client.AmqpConnectionConfig;
import org.eclipse.hono.client.AmqpConnectorClient;
import org.eclipse.hono.client.api.ConnectionConfig;
import org.eclipse.hono.client.api.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Client2_Receiver
{
   private static final Logger LOGGER    = LoggerFactory.getLogger(Client2_Receiver.class);

   public static void main(final String[] args) throws Exception {
        final ConnectionConfig connectionConfig = new AmqpConnectionConfig(Constants.AMQP_URI);
      final AmqpConnectorClient client = new AmqpConnectorClient(CLIENT_ID_2, connectionConfig);

      client.connect();
      client.subscriptionForTopic(TOPIC_1, getDemoConsumer());
      client.subscriptionForTopic(TOPIC_2, getDemoConsumer());

      LOGGER.info("Listening for messages on {} and {}...", TOPIC_1, TOPIC_2);

      System.in.read();
      LOGGER.info("Disconnecting client...");

      client.disconnect();
   }

   private static Consumer<Message> getDemoConsumer() {
      return m -> LOGGER.info("[{}] received message for topic [{}]: {} ",
         CLIENT_ID_2, m.getTopic(), UTF_8.decode(m.getPayload()).toString());
   }
}
