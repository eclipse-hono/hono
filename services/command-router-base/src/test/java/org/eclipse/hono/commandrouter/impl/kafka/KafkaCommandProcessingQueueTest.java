/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.commandrouter.impl.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommand;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Verifies behavior of {@link KafkaCommandProcessingQueue}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class KafkaCommandProcessingQueueTest {

    private final String topic = new HonoTopic(HonoTopic.Type.COMMAND, Constants.DEFAULT_TENANT).toString();

    /**
     * Verifies that the sendAction is invoked on commands in the queue in the expected order.
     *
     * @param vertx The vert.x instance to use.
     * @param ctx The vert.x test context.
     */
    @Test
    @SuppressWarnings("rawtypes")
    void testSendActionIsInvokedInOrder(final Vertx vertx, final VertxTestContext ctx) {
        final Context vertxContext = vertx.getOrCreateContext();
        vertxContext.runOnContext(v -> {
            final KafkaCommandProcessingQueue kafkaCommandProcessingQueue = new KafkaCommandProcessingQueue(vertxContext);
            // GIVEN a number of test commands
            final LinkedList<KafkaBasedCommandContext> commandContexts = IntStream.range(0, 5)
                    .mapToObj(this::getTestCommandContext)
                    .collect(Collectors.toCollection(LinkedList::new));
            // ... added to the queue in order
            commandContexts.forEach(kafkaCommandProcessingQueue::add);

            // WHEN applying the sendAction on these commands in the reverse order
            final LinkedList<KafkaBasedCommandContext> sendActionInvoked = new LinkedList<>();
            final LinkedList<KafkaBasedCommandContext> applySendActionSucceeded = new LinkedList<>();
            final List<Future> resultFutures = new LinkedList<>();
            commandContexts.descendingIterator().forEachRemaining(context -> {
                resultFutures.add(kafkaCommandProcessingQueue
                        .applySendCommandAction(context, () -> {
                            sendActionInvoked.add(context);
                            return Future.succeededFuture();
                        })
                        .onSuccess(v2 -> applySendActionSucceeded.add(context)));
            });

            CompositeFuture.all(resultFutures).onComplete(ctx.succeeding(ar -> {
                ctx.verify(() -> {
                    // THEN the commands got sent in the original order
                    assertThat(sendActionInvoked).isEqualTo(commandContexts);
                    assertThat(applySendActionSucceeded).isEqualTo(commandContexts);
                });
                ctx.completeNow();
            }));
        });
    }

    /**
     * Verifies that command entries in the queue get discarded, with the result of the sendAction getting failed, when
     * the topic partition assignment of the entries gets revoked.
     */
    @Test
    void testQueueEntriesDiscardedOnPartitionRevoked() {
        final KafkaCommandProcessingQueue kafkaCommandProcessingQueue = new KafkaCommandProcessingQueue(mock(Context.class));
        // GIVEN a number of test commands, received via partition 0
        final LinkedList<KafkaBasedCommandContext> commandContexts = IntStream.range(0, 3)
                .mapToObj(this::getTestCommandContext)
                .collect(Collectors.toCollection(LinkedList::new));
        // ... added to the queue in order
        commandContexts.forEach(kafkaCommandProcessingQueue::add);

        // WHEN applying the sendAction for the last 2 commands
        final Future<Void> result2 = kafkaCommandProcessingQueue
                .applySendCommandAction(commandContexts.get(2), Future::succeededFuture);
        final Future<Void> result1 = kafkaCommandProcessingQueue
                .applySendCommandAction(commandContexts.get(1), Future::succeededFuture);

        // ... and then marking only one other partition as currently being handled
        kafkaCommandProcessingQueue.setCurrentlyHandledPartitions(Set.of(new TopicPartition(topic, 1)));

        // THEN the processing of the commands with the sendAction already applied (but previously waiting for command 1) is failed
        assertThat(result1.failed()).isTrue();
        assertThat(ServiceInvocationException.extractStatusCode(result1.cause())).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
        assertThat(result2.failed()).isTrue();
        assertThat(ServiceInvocationException.extractStatusCode(result2.cause())).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);

        // and applying the sendAction for the first command results in an error
        final Future<Void> result0 = kafkaCommandProcessingQueue
                .applySendCommandAction(commandContexts.get(0), Future::succeededFuture);
        assertThat(result0.failed()).isTrue();
        assertThat(ServiceInvocationException.extractStatusCode(result0.cause())).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
    }

    @SuppressWarnings("unchecked")
    private KafkaBasedCommandContext getTestCommandContext(final int offset) {
        final String deviceId = "deviceId";
        final List<KafkaHeader> headers = new ArrayList<>(List.of(
                KafkaHeader.header(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId),
                KafkaHeader.header(MessageHelper.SYS_PROPERTY_SUBJECT, "subject_" + offset),
                KafkaHeader.header(MessageHelper.SYS_PROPERTY_CORRELATION_ID, "correlationId")
        ));
        final KafkaConsumerRecord<String, Buffer> consumerRecord = mock(KafkaConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.topic()).thenReturn(topic);
        when(consumerRecord.key()).thenReturn(deviceId);
        when(consumerRecord.offset()).thenReturn((long) offset);
        when(consumerRecord.partition()).thenReturn(0);

        final KafkaBasedCommand cmd = KafkaBasedCommand.from(consumerRecord);
        return new KafkaBasedCommandContext(cmd, mock(Span.class), mock(CommandResponseSender.class)) {
            @Override
            public String toString() {
                return "Command " + offset;
            }
        };
    }

}
