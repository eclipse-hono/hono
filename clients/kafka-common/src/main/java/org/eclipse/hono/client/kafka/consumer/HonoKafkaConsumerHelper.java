/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * Provides helper methods for working with a vert.x {@link KafkaConsumer}.
 */
public abstract class HonoKafkaConsumerHelper {

    private HonoKafkaConsumerHelper() {
    }

    /**
     * Returns a string representation of the given partitions, to be used for debug log messages.
     *
     * @param partitionsSet The partitions to use.
     * @return The string representation.
     * @throws NullPointerException if partitionsSet is {@code null}.
     */
    public static String getPartitionsDebugString(final Collection<TopicPartition> partitionsSet) {
        Objects.requireNonNull(partitionsSet);
        return partitionsSet.size() <= 20 // skip details for larger set
                ? partitionsSet.stream()
                .collect(Collectors.groupingBy(TopicPartition::topic,
                        Collectors.mapping(TopicPartition::partition, Collectors.toCollection(TreeSet::new))))
                .toString()
                : partitionsSet.size() + " topic partitions";
    }

    /**
     * Returns a string representation of the given offsets, to be used for debug/trace log messages.
     *
     * @param offsets The offsets to use.
     * @return The string representation.
     * @throws NullPointerException if offsets is {@code null}.
     */
    public static String getOffsetsDebugString(final Map<TopicPartition, OffsetAndMetadata> offsets) {
        Objects.requireNonNull(offsets);
        return offsets.size() <= 20 // skip details for larger map
                ? offsets.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().offset())).toString()
                : offsets.size() + " offsets";
    }
}
