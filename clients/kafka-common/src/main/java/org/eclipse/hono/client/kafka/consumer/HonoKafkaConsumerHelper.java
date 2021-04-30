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
package org.eclipse.hono.client.kafka.consumer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.kafka.client.common.PartitionInfo;
import io.vertx.kafka.client.common.impl.Helper;
import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * Provides helper methods for working with a vert.x {@link KafkaConsumer}.
 */
public abstract class HonoKafkaConsumerHelper {

    private HonoKafkaConsumerHelper() {
    }

    /**
     * Get metadata about the partitions for a given topic.
     * <p>
     * This method is adapted from {@code io.vertx.kafka.client.consumer.impl.KafkaConsumerImpl#partitionsFor(String, Handler)}
     * and fixes an NPE in case {@code KafkaConsumer#partitionsFor(String)} returns {@code null}
     * (happens if "auto.create.topics.enable" is false).
     * <p>
     * This method will become obsolete when updating to a Kafka client in which https://issues.apache.org/jira/browse/KAFKA-12260
     * ("PartitionsFor should not return null value") is solved.
     * TODO remove this method once updated Kafka client is used
     *
     * @param kafkaConsumer The KafkaConsumer to use.
     * @param topic The topic to get partitions info for.
     * @param handler The handler to invoke with the result.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void partitionsFor(final KafkaConsumer<?, ?> kafkaConsumer,
            final String topic, final Handler<AsyncResult<List<PartitionInfo>>> handler) {
        Objects.requireNonNull(kafkaConsumer);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(handler);
        kafkaConsumer.asStream().partitionsFor(topic, done -> {

            if (done.succeeded()) {
                if (done.result() == null) {
                    handler.handle(Future.succeededFuture(List.of()));
                } else {
                    final List<PartitionInfo> partitions = new ArrayList<>();
                    for (final org.apache.kafka.common.PartitionInfo kafkaPartitionInfo: done.result()) {

                        final PartitionInfo partitionInfo = new PartitionInfo();
                        partitionInfo
                                .setInSyncReplicas(
                                        Stream.of(kafkaPartitionInfo.inSyncReplicas()).map(Helper::from).collect(Collectors.toList()))
                                .setLeader(Helper.from(kafkaPartitionInfo.leader()))
                                .setPartition(kafkaPartitionInfo.partition())
                                .setReplicas(
                                        Stream.of(kafkaPartitionInfo.replicas()).map(Helper::from).collect(Collectors.toList()))
                                .setTopic(kafkaPartitionInfo.topic());

                        partitions.add(partitionInfo);
                    }
                    handler.handle(Future.succeededFuture(partitions));
                }
            } else {
                handler.handle(Future.failedFuture(done.cause()));
            }
        });
    }

    /**
     * Returns a string representation of the given partitions, to be used for debug log messages.
     *
     * @param partitionsSet The partitions to use.
     * @return The string representation.
     * @throws NullPointerException if partitionsSet is {@code null}.
     */
    public static String getPartitionsDebugString(final Collection<io.vertx.kafka.client.common.TopicPartition> partitionsSet) {
        Objects.requireNonNull(partitionsSet);
        return partitionsSet.size() <= 20 // skip details for larger set
                ? partitionsSet.stream()
                .collect(Collectors.groupingBy(io.vertx.kafka.client.common.TopicPartition::getTopic,
                        Collectors.mapping(io.vertx.kafka.client.common.TopicPartition::getPartition, Collectors.toCollection(TreeSet::new))))
                .toString()
                : partitionsSet.size() + " topic partitions";
    }
}
