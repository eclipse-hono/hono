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
package org.eclipse.hono.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.hono.client.api.ConnectionConfig;
import org.eclipse.hono.client.api.ConnectorClient;
import org.eclipse.hono.client.api.model.Message;
import org.eclipse.hono.client.api.model.TopicAcl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class AmqpConnectorClient implements ConnectorClient
{
   private static final Logger LOGGER = LoggerFactory.getLogger(AmqpConnectorClient.class);
   public static final String OUT = "out";
   public static final String IN = "in";
   public static final String MESSAGE = "message";
   public static final String REGISTER_TOPIC = "registerTopic";
   public static final String TOPIC_HEADER = "topic";
   public static final String SUBJECT_HEADER = "subject";

   private final ConcurrentMap<String, Collection<Consumer<Message>>> consumers = new ConcurrentHashMap<>();
   private final String clientId;
   private final ConnectionConfig connectionConfig;
   private final AmqpConnectionManagerImpl connectionManager;
   private Channel channel;
   private String consumerTag;

   public AmqpConnectorClient(final String clientId, final ConnectionConfig connectionConfig) throws Exception
   {
      requireNonNull(connectionConfig);
      requireNonNull(clientId);
      this.clientId = clientId;
      this.connectionConfig = connectionConfig;
      connectionManager = new AmqpConnectionManagerImpl();
   }

   public void connect()
      throws IOException, TimeoutException, NoSuchAlgorithmException, KeyManagementException, URISyntaxException
   {
      connectionManager.connect(connectionConfig);
      LOGGER.info("connection established.");
      declareBindQueue();
   }

   private void declareBindQueue() throws IOException
   {
      channel = connectionManager.getChannel();
      channel.queueDeclare(clientId, true, false, false, emptyMap());
      LOGGER.info("declared a queue with subject " + clientId);
      channel.queueBind(clientId, OUT, clientId);
      LOGGER.info("bind queue to routingkey " + clientId);
      consumerTag = channel.basicConsume(clientId, new DefaultConsumer(channel)
      {
         @Override
         public void handleDelivery(final String consumerTag, final Envelope envelope,
            final AMQP.BasicProperties properties, final byte[] body) throws IOException
         {
            final String routingKey = envelope.getRoutingKey();
            getChannel().basicAck(envelope.getDeliveryTag(), false);
            final String topic = properties.getHeaders().get(TOPIC_HEADER).toString();
            LOGGER.debug("{} received message for topic '{}'", routingKey, topic);
            delegateToTopicConsumer(properties, body, topic);
         }
      });
   }

   private void delegateToTopicConsumer(final AMQP.BasicProperties properties, final byte[] body, final String topic)
      throws IOException
   {
      final Map<String, String> headers =
         properties.getHeaders().entrySet().stream().filter(e -> e.getValue() instanceof String)
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toString()));
      final AmqpMessage message = new AmqpMessage(topic, headers, ByteBuffer.wrap(body));
      ofNullable(consumers.get(topic)).ifPresent(consumerList -> consumerList.forEach(c -> c.accept(message)));
   }

   public void disconnect() throws IOException, TimeoutException
   {
      channel.basicCancel(consumerTag);
      channel.close();
      connectionManager.disconnect();
   }

   @Override
   public void registerTopic(final String clientId, final String topic, final TopicAcl topicAcl) throws IOException
   {
      final Map<String, Object> headers = new HashMap<>();
      headers.put(SUBJECT_HEADER, clientId);
      headers.put(TOPIC_HEADER, topic);
      final AMQP.BasicProperties props = new AMQP.BasicProperties().builder().headers(headers).build();
      channel.basicPublish(IN, REGISTER_TOPIC, true, props, topicAcl.toBytes());
   }

   @Override
   public void deregisterTopic(final String clientId)
   {
      // todo client to remove topic in backend.
   }

   public void sendMessage(final String topic, final Map<String, String> customHeaders, final ByteBuffer payload)
      throws IOException
   {
      final Map<String, Object> headers = new HashMap<>();
      headers.putAll(customHeaders);
      headers.put(TOPIC_HEADER, topic);
      headers.put(SUBJECT_HEADER, clientId);
      final AMQP.BasicProperties props = new AMQP.BasicProperties().builder().headers(headers).build();
      channel.basicPublish(IN, MESSAGE, props, payload.array());
      LOGGER.debug("Sent message for topic {}: {}", topic, UTF_8.decode(payload).toString());
   }

   @Override
   public void subscriptionForTopic(final String topic, final Consumer<Message> handler) throws IOException
   {
      requireNonNull(topic);
      this.consumers.computeIfAbsent(topic, t -> new ArrayList<>()).add(handler);
   }

   @Override
   public void desubscriptionForTopic(final String topic) throws IOException
   {
      //todo client to delete topic from backend
      this.consumers.remove(topic);
   }
}

