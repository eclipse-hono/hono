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

package org.eclipse.hono.kafka.test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import io.vertx.core.buffer.Buffer;

/**
 * A {@link MockConsumer} with additional support, e.g. invoking {@code ConsumerRebalanceListener} handlers.
 */
public class KafkaMockConsumer extends MockConsumer<String, Buffer> {

    public static final Node DEFAULT_NODE = new Node(1, "broker1", 9092);

    private Collection<TopicPartition> nextPollRebalancePartitionAssignment;
    private Collection<TopicPartition> onSubscribeRebalancePartitionAssignment;
    private ConsumerRebalanceListener rebalanceListener;
    private final AtomicBoolean skipSettingClosedFlagOnNextClose = new AtomicBoolean();

    /**
     * Creates a new KafkaMockConsumer.
     *
     * @param offsetResetStrategy The offset reset strategy to use.
     */
    public KafkaMockConsumer(final OffsetResetStrategy offsetResetStrategy) {
        super(offsetResetStrategy);
    }

    /**
     * Updates the topic partitions of this consumer by adding the given topic partition.
     *
     * @param topicPartition The topic partition to set.
     * @param node The node acting as the leader for the topic partition.
     */
    public void updatePartitions(final TopicPartition topicPartition, final Node node) {
        updatePartitions(topicPartition.topic(), List.of(getPartitionInfo(topicPartition.topic(),
                topicPartition.partition(), node)));
    }

    private static PartitionInfo getPartitionInfo(final String topic, final int partition, final Node node) {
        final Node[] replicas = new Node[]{};
        return new PartitionInfo(topic, partition, node, replicas, replicas);
    }

    /**
     * Marks the following subscribe() invocations to be followed by a rebalance with the given partition
     * assignment, if the given assignment collection isn't {@code null}. The rebalance will be invoked
     * on the first poll() invocation after the subscribe() call.
     *
     * @param assignment The partition assignment to set. Use {@code null} to reset the assignment and skip
     *                   the rebalance on following subscribe() invocations.
     */
    public void setRebalancePartitionAssignmentAfterSubscribe(final Collection<TopicPartition> assignment) {
        onSubscribeRebalancePartitionAssignment = assignment;
    }

    @Override
    public synchronized void subscribe(final Pattern pattern, final ConsumerRebalanceListener listener) {
        this.rebalanceListener = listener;
        subscription().clear(); // clear() is needed for successive invocations of subscribe(pattern) to work;
                                // otherwise the 2nd invocation will cause the subscription list to only contain
                                // partitions added in between
        super.subscribe(pattern, listener);
        nextPollRebalancePartitionAssignment = onSubscribeRebalancePartitionAssignment;
    }

    @Override
    public synchronized void subscribe(final Collection<String> topics,
            final ConsumerRebalanceListener listener) {
        this.rebalanceListener = listener;
        super.subscribe(topics, listener);
        nextPollRebalancePartitionAssignment = onSubscribeRebalancePartitionAssignment;
    }

    @Override
    public synchronized ConsumerRecords<String, Buffer> poll(final Duration timeout) {
        Optional.ofNullable(nextPollRebalancePartitionAssignment)
                .ifPresent(newAssignment -> {
                    nextPollRebalancePartitionAssignment = null;
                    rebalance(newAssignment);
                });
        return super.poll(timeout);
    }

    @Override
    public synchronized void rebalance(final Collection<TopicPartition> newAssignment) {
        Optional.ofNullable(rebalanceListener)
                .ifPresent(listener -> listener.onPartitionsRevoked(assignment()));
        super.rebalance(newAssignment);
        Optional.ofNullable(rebalanceListener)
                .ifPresent(listener -> listener.onPartitionsAssigned(newAssignment));
    }

    /**
     * Skips setting the "closed" flag on the next close() invocation.
     * <p>
     * Can be used to still be able to invoke some methods (e.g. committed()) on the MockConsumer
     * after close() was called.
     */
    public void setSkipSettingClosedFlagOnNextClose() {
        skipSettingClosedFlagOnNextClose.setPlain(true);
    }

    @Override
    public synchronized void close() {
        Optional.ofNullable(rebalanceListener)
                .ifPresent(listener -> listener.onPartitionsRevoked(assignment()));
        if (!skipSettingClosedFlagOnNextClose.compareAndSet(true, false)) {
            super.close();
        }
    }
}
