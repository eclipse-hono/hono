/*******************************************************************************
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.CommandToBeReprocessedException;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommand;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
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
    void testSendActionIsInvokedInOrder(final Vertx vertx, final VertxTestContext ctx) {
        final Context vertxContext = vertx.getOrCreateContext();
        vertxContext.runOnContext(v -> {
            final KafkaCommandProcessingQueue kafkaCommandProcessingQueue = new KafkaCommandProcessingQueue(vertx);
            // GIVEN a number of test commands
            final LinkedList<KafkaBasedCommandContext> commandContexts = IntStream.range(0, 5)
                    .mapToObj(this::getTestCommandContext)
                    .collect(Collectors.toCollection(LinkedList::new));
            // ... added to the queue in order
            commandContexts.forEach(kafkaCommandProcessingQueue::add);

            // WHEN applying the sendAction on these commands in the reverse order
            final LinkedList<KafkaBasedCommandContext> sendActionInvoked = new LinkedList<>();
            final LinkedList<KafkaBasedCommandContext> applySendActionSucceeded = new LinkedList<>();
            final List<Future<Void>> resultFutures = new LinkedList<>();
            commandContexts.descendingIterator().forEachRemaining(context -> {
                resultFutures.add(kafkaCommandProcessingQueue
                        .applySendCommandAction(context, () -> {
                            sendActionInvoked.add(context);
                            return Future.succeededFuture();
                        })
                        .onSuccess(v2 -> applySendActionSucceeded.add(context)));
            });

            Future.all(resultFutures).onComplete(ctx.succeeding(ar -> {
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
        final Vertx vertx = mock(Vertx.class);
        final Context context = VertxMockSupport.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        final KafkaCommandProcessingQueue kafkaCommandProcessingQueue = new KafkaCommandProcessingQueue(vertx);
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

        // THEN the processing of the commands where applySendCommandAction() was already invoked (but where processing
        // was blocked waiting for command 1) is failed
        assertThat(result1.failed()).isTrue();
        assertThat(result1.cause()).isInstanceOf(CommandToBeReprocessedException.class);
        assertThat(result2.failed()).isTrue();
        assertThat(result2.cause()).isInstanceOf(CommandToBeReprocessedException.class);

        // and applying the sendAction for the first command also results in the same kind of error
        final Future<Void> result0 = kafkaCommandProcessingQueue
                .applySendCommandAction(commandContexts.get(0), Future::succeededFuture);
        assertThat(result0.failed()).isTrue();
        assertThat(result0.cause()).isInstanceOf(CommandToBeReprocessedException.class);
    }

    @SuppressWarnings("unchecked")
    private KafkaBasedCommandContext getTestCommandContext(final int offset) {
        final String deviceId = "deviceId";
        final List<KafkaHeader> headers = new ArrayList<>(List.of(
                KafkaRecordHelper.createDeviceIdHeader(deviceId),
                KafkaRecordHelper.createSubjectHeader("subject_" + offset),
                KafkaRecordHelper.createCorrelationIdHeader("correlationId")
        ));
        final KafkaConsumerRecord<String, Buffer> consumerRecord = mock(KafkaConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.topic()).thenReturn(topic);
        when(consumerRecord.key()).thenReturn(deviceId);
        when(consumerRecord.offset()).thenReturn((long) offset);
        when(consumerRecord.partition()).thenReturn(0);

        final KafkaBasedCommand cmd = KafkaBasedCommand.from(consumerRecord);
        return new KafkaBasedCommandContext(cmd, mock(CommandResponseSender.class), mock(Span.class)) {
            @Override
            public String toString() {
                return "Command " + offset;
            }
        };
    }

}
