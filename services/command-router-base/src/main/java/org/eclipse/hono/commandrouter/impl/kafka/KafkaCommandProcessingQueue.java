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

package org.eclipse.hono.commandrouter.impl.kafka;

import java.util.Collection;
import java.util.Objects;

import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.commandrouter.impl.AbstractCommandProcessingQueue;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Queue with the commands currently being processed.
 * <p>
 * The final step of processing a command, forwarding it to its target, is invoked here maintaining the
 * order of the incoming commands.
 * <p>
 * Command order is maintained here across commands received on the same topic partition. As commands use the device
 * identifier as partition key, this means that commands for a specific device are forwarded in the same order they
 * were received.
 * <p>
 * A further objective of this class is preventing the same command to be forwarded twice to its target. When a topic
 * partition was revoked from the Kafka consumer that feeds this queue, it is ensured that commands still in processing
 * (i.e. commands whose record offsets have not been committed) are not forwarded to their target. These commands are
 * meant to be read and handled by another consumer then.
 */
public class KafkaCommandProcessingQueue extends AbstractCommandProcessingQueue<KafkaBasedCommandContext, TopicPartition> {

    /**
     * Creates a new KafkaCommandProcessingQueue.
     * @param vertx The vert.x instance to use.
     * @throws NullPointerException if vertx is {@code null}.
     */
    public KafkaCommandProcessingQueue(final Vertx vertx) {
        super(vertx);
    }

    @Override
    protected TopicPartition getQueueKey(final KafkaBasedCommandContext commandContext) {
        final KafkaConsumerRecord<String, Buffer> record = commandContext.getCommand().getRecord();
        return new TopicPartition(record.topic(), record.partition());
    }

    @Override
    protected String getCommandSourceForLog(final TopicPartition queueKey) {
        return "partition [" + queueKey + "]";
    }

    /**
     * Informs this queue about changes to the partitions that the command consumer is receiving records from.
     * @param partitions The newly assigned partitions.
     * @throws NullPointerException if partitions is {@code null}.
     */
    public void setCurrentlyHandledPartitions(final Collection<TopicPartition> partitions) {
        Objects.requireNonNull(partitions);
        removeCommandQueueEntries(queueKey -> !partitions.contains(queueKey));
    }

    /**
     * Informs this queue about the partitions that the command consumer is not receiving records from any more.
     * @param partitions The revoked partitions.
     * @throws NullPointerException if partitions is {@code null}.
     */
    public void setRevokedPartitions(final Collection<TopicPartition> partitions) {
        Objects.requireNonNull(partitions);
        removeCommandQueueEntries(queueKey -> partitions.contains(queueKey));
    }
}
