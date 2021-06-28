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

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.Strings;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A Kafka consumer that automatically commits partition offsets corresponding to the latest record per
 * partition whose (asynchronous) handling has been marked as completed.
 * <p>
 * A scenario that this consumer addresses is one of a received record taking quite long to be handled asynchronously
 * while the consumer is closed and the application exits. With the standard <em>enable.auto.commit</em> behaviour,
 * an offset commit for that record will already be done on closing the consumer so that another consumer of the
 * consumer group won't read that record again, meaning the record potentially only gets partially handled.
 * Using the auto-commit implementation in this class, only the offset of a record whose handling was marked as
 * completed gets committed. This prevents incompletely handled records and thereby enables at-least-once semantics.
 * <p>
 * In terms of when offsets are committed, the behaviour is similar to the one used for a consumer with
 * <em>enable.auto.commit</em>. Commits are done periodically (using <em>commitAsync</em>) and when a rebalance
 * happens or the consumer is stopped (using <em>commitSync</em>). The periodic commit interval is defined via
 * the standard <em>auto.commit.interval.ms</em> configuration property.
 * <p>
 * In order to not fall behind with the position of the committed offset vs. the last received offset, users of this
 * class have to make sure that the record handling function, which provides the completion Future, is completed in time.
 * <p>
 * In contrast to the <em>enable.auto.commit</em> behaviour, identical offsets are skipped during successive commits.
 * It is only after a period defined by <em>hono.offsets.skip.recommit.period.seconds</em> has elapsed, that such offsets
 * are committed again. This is to make sure that such offsets don't reach their retention time, provided the recommit
 * period is lower than the <em>offsets.retention.minutes</em> broker config value.
 */
public class AsyncHandlingAutoCommitKafkaConsumer extends HonoKafkaConsumer {

    /**
     * The name of the configuration property to define the period for which committing an already committed offset is
     * skipped, until it may be recommitted if it is still the latest offset.
     */
    public static final String CONFIG_HONO_OFFSETS_SKIP_RECOMMIT_PERIOD_SECONDS = "hono.offsets.skip.recommit.period.seconds";
    /**
     * The default periodic commit interval.
     */
    public static final Duration DEFAULT_COMMIT_INTERVAL = Duration.ofSeconds(5);
    /**
     * The default period for which committing an already committed offset is skipped,
     * until it may be recommitted if it is still the latest offset.
     */
    public static final Duration DEFAULT_OFFSETS_SKIP_RECOMMIT_PERIOD = Duration.ofMinutes(30);

    private final long commitIntervalMillis;
    private final long skipOffsetRecommitPeriodSeconds;
    private final Map<TopicPartition, TopicPartitionOffsets> offsetsMap = new HashMap<>();
    private final AtomicBoolean periodicCommitInvocationInProgress = new AtomicBoolean();

    private Long periodicCommitTimerId;

    /**
     * Creates a consumer to receive records on the given topics.
     * <p>
     * Partition offsets returned by invoking the given supplier are committed periodically and
     * when a rebalance happens. The periodic commit interval is taken from the
     * <em>auto.commit.interval.ms</em> config value.
     *
     * @param vertx The Vert.x instance to use.
     * @param topics The Kafka topic to consume records from.
     * @param recordHandler The function to be invoked for each received record. The completion of its Future return
     *                      value marks the record handling as finished so that the corresponding offset may be committed.
     * @param consumerConfig The Kafka consumer configuration.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the consumerConfig is missing a "group.id" entry.
     */
    public AsyncHandlingAutoCommitKafkaConsumer(
            final Vertx vertx,
            final Set<String> topics,
            final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> recordHandler,
            final Map<String, String> consumerConfig) {
        this(new AtomicReference<>(), vertx, topics, null, recordHandler, consumerConfig);
    }

    /**
     * Creates a consumer to receive records on topics that match the given pattern.
     * <p>
     * Partition offsets returned by invoking the given supplier are committed periodically and
     * when a rebalance happens. The periodic commit interval is taken from the
     * <em>auto.commit.interval.ms</em> config value.
     *
     * @param vertx The Vert.x instance to use.
     * @param topicPattern The pattern of Kafka topic names to consume records from.
     * @param recordHandler The function to be invoked for each received record. The completion of its Future return
     *                      value marks the record handling as finished so that the corresponding offset may be committed.
     * @param consumerConfig The Kafka consumer configuration.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the consumerConfig is missing a "group.id" entry.
     */
    public AsyncHandlingAutoCommitKafkaConsumer(
            final Vertx vertx,
            final Pattern topicPattern,
            final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> recordHandler,
            final Map<String, String> consumerConfig) {
        this(new AtomicReference<>(), vertx, null, topicPattern, recordHandler, consumerConfig);
    }

    private AsyncHandlingAutoCommitKafkaConsumer(
            final AtomicReference<AsyncHandlingAutoCommitKafkaConsumer> selfRef,
            final Vertx vertx,
            final Set<String> topics,
            final Pattern topicPattern,
            final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> recordHandler,
            final Map<String, String> consumerConfig) {
        super(vertx, topics, topicPattern, mapRecordHandler(selfRef, recordHandler), validateAndAdaptConsumerConfig(consumerConfig));
        selfRef.setPlain(this);

        this.commitIntervalMillis = getCommitInterval(consumerConfig);
        this.skipOffsetRecommitPeriodSeconds = getSkipOffsetRecommitPeriodSeconds(consumerConfig);
    }

    private static Handler<KafkaConsumerRecord<String, Buffer>> mapRecordHandler(
            final AtomicReference<AsyncHandlingAutoCommitKafkaConsumer> selfRef,
            final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> recordHandler) {
        return record -> {
            final OffsetsQueueEntry offsetsQueueEntry = selfRef.getPlain().setRecordReceived(record);
            try {
                recordHandler.apply(record)
                        .onComplete(ar -> offsetsQueueEntry.setHandlingComplete());
            } catch (final Exception e) {
                selfRef.getPlain().log.warn("error handling record [topic: {}, partition: {}, offset: {}, headers: {}]",
                        record.topic(), record.partition(), record.offset(), record.headers(), e);
                offsetsQueueEntry.setHandlingComplete();
            }
        };
    }

    private static Map<String, String> validateAndAdaptConsumerConfig(final Map<String, String> consumerConfig) {
        if (Strings.isNullOrEmpty(consumerConfig.get(ConsumerConfig.GROUP_ID_CONFIG))) {
            throw new IllegalArgumentException(ConsumerConfig.GROUP_ID_CONFIG + " config entry has to be set");
        }
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return consumerConfig;
    }

    private static long getCommitInterval(final Map<String, String> consumerConfig) {
        return Optional.ofNullable(consumerConfig.get(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG))
                .map(Long::parseLong)
                .orElse(DEFAULT_COMMIT_INTERVAL.toMillis());
    }

    private static long getSkipOffsetRecommitPeriodSeconds(final Map<String, String> consumerConfig) {
        return Optional.ofNullable(consumerConfig.get(CONFIG_HONO_OFFSETS_SKIP_RECOMMIT_PERIOD_SECONDS))
                .map(Long::parseLong)
                .orElse(DEFAULT_OFFSETS_SKIP_RECOMMIT_PERIOD.toSeconds());
    }

    @Override
    protected void onRecordHandlerSkippedForExpiredRecord(final KafkaConsumerRecord<String, Buffer> record) {
        setRecordReceived(record).setHandlingComplete();
    }

    @Override
    public Future<Void> start() {
        return super.start().onComplete(v -> startPeriodicCommitTimer());
    }

    @Override
    public Future<Void> stop() {
        if (periodicCommitTimerId != null) {
            vertx.cancelTimer(periodicCommitTimerId);
        }
        return super.stop();
    }

    @Override
    protected void onPartitionsAssignedBlocking(final Set<io.vertx.kafka.client.common.TopicPartition> partitionsSet) {
        final Consumer<String, Buffer> wrappedConsumer = getKafkaConsumer().asStream().unwrap();
        clearObsoleteTopicPartitionOffsets(wrappedConsumer.assignment());
    }

    @Override
    protected void onPartitionsRevokedBlocking(final Set<io.vertx.kafka.client.common.TopicPartition> partitionsSet) {
        commitOffsetsSync();
    }

    /**
     * To be run on the Kafka polling thread.
     */
    private void commitOffsetsSync() {
        if (Vertx.currentContext() != null) {
            throw new IllegalStateException("must be run on the polling thread");
        }
        final Map<TopicPartition, OffsetAndMetadata> offsets = getOffsetsToCommit();
        if (!offsets.isEmpty()) {
            try {
                if (log.isTraceEnabled()) {
                    log.trace("commitSync; offsets: [{}]", HonoKafkaConsumerHelper.getOffsetsDebugString(offsets));
                }
                // commit invoked on the wrappedConsumer, so that it is run synchronously on the current thread
                final Consumer<String, Buffer> wrappedConsumer = getKafkaConsumer().asStream().unwrap();
                wrappedConsumer.commitSync(offsets);
                setCommittedOffsets(offsets);
                log.trace("commitSync succeeded");
            } catch (final Exception e) {
                log.warn("commit failed: {}", e.toString());
            }
        } else {
            log.trace("skip commitSync - no offsets to commit");
        }
    }

    private void startPeriodicCommitTimer() {
        periodicCommitTimerId = vertx.setPeriodic(commitIntervalMillis, tid -> {
            if (!periodicCommitInvocationInProgress.compareAndSet(false, true)) {
                log.trace("periodic commit already triggered, skipping invocation");
                return;
            }
            // run periodic commit on the kafka polling thread, to be able to call commitAsync (not provided by the vert.x KafkaConsumer)
            runOnKafkaWorkerThread(v -> {
                final var offsets = getOffsetsToCommit();
                if (!offsets.isEmpty()) {
                    if (log.isTraceEnabled()) {
                        log.trace("do periodic commit; offsets: [{}]", HonoKafkaConsumerHelper.getOffsetsDebugString(offsets));
                    }
                    final Consumer<String, Buffer> wrappedConsumer = getKafkaConsumer().asStream().unwrap();
                    try {
                        wrappedConsumer.commitAsync(offsets, (committedOffsets, error) -> {
                            if (error != null) {
                                log.info("periodic commit failed: {}", error.toString());
                            } else {
                                log.trace("periodic commit succeeded");
                                setCommittedOffsets(committedOffsets);
                            }
                        });
                    } catch (final Exception ex) {
                        log.error("error doing periodic commit", ex);
                    }
                } else {
                    log.trace("skip periodic commit - no offsets to commit");
                }
                periodicCommitInvocationInProgress.set(false);
            });
        });
    }

    // synchronized because offsetsMap is accessed from vert.x event loop and kafka polling thread
    private synchronized OffsetsQueueEntry setRecordReceived(final KafkaConsumerRecord<String, Buffer> record) {
        final TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
        return offsetsMap
                .computeIfAbsent(topicPartition, k -> new TopicPartitionOffsets(topicPartition))
                .addOffset(record.offset());
    }

    // synchronized because offsetsMap is accessed from vert.x event loop and kafka polling thread
    private synchronized void clearObsoleteTopicPartitionOffsets(final Collection<TopicPartition> currentlyAssignedPartitions) {
        Objects.requireNonNull(currentlyAssignedPartitions);
        final var partitionOffsetsIterator = offsetsMap.entrySet().iterator();
        while (partitionOffsetsIterator.hasNext()) {
            final var topicPartitionOffsetsEntry = partitionOffsetsIterator.next();
            if (!currentlyAssignedPartitions.contains(topicPartitionOffsetsEntry.getKey())) {
                if (topicPartitionOffsetsEntry.getValue().needsCommit()) {
                    log.warn("partition [{}] not assigned to consumer anymore but latest handled record offset hasn't been committed yet! {}",
                            topicPartitionOffsetsEntry.getKey(), topicPartitionOffsetsEntry.getValue().getStateInfo());
                } else if (!topicPartitionOffsetsEntry.getValue().allCompleted()) {
                    log.debug("partition [{}] not assigned to consumer anymore but not all read records have been fully processed yet! {}",
                            topicPartitionOffsetsEntry.getKey(), topicPartitionOffsetsEntry.getValue().getStateInfo());
                } else {
                    log.trace("partition [{}] not assigned to consumer anymore; no still outstanding offset commits there",
                            topicPartitionOffsetsEntry.getKey());
                }
                partitionOffsetsIterator.remove();
            }
        }
    }

    // synchronized because offsetsMap is accessed from vert.x event loop and kafka polling thread
    private synchronized Map<TopicPartition, OffsetAndMetadata> getOffsetsToCommit() {
        return offsetsMap.entrySet().stream()
                // map each to key/value Pair only if offsets needs to be committed
                .flatMap(entry -> entry.getValue().getLastSequentiallyCompletedOffsetForCommit().stream()
                        .map(uncommittedOffset -> Pair.of(entry.getKey(),
                                new OffsetAndMetadata(uncommittedOffset + 1, ""))))
                .collect(Collectors.toMap(Pair::one, Pair::two));
    }

    // synchronized because offsetsMap is accessed from vert.x event loop and kafka polling thread
    private synchronized void setCommittedOffsets(final Map<TopicPartition, OffsetAndMetadata> offsets) {
        offsets.forEach((partition, offsetAndMetadata) -> {
            Optional.ofNullable(offsetsMap.get(partition))
                    .ifPresent(queue -> queue.setLastCommittedOffset(offsetAndMetadata.offset() - 1));
        });
    }

    /**
     * Keeps offset data for a TopicPartition.
     */
    class TopicPartitionOffsets {

        private final TopicPartition topicPartition;
        private final Deque<OffsetsQueueEntry> queue = new LinkedList<>();

        private long lastSequentiallyCompletedOffset = -1;
        private long lastCommittedOffset = -1;
        private Instant lastCommitTime;

        /**
         * Creates a new TopicPartitionOffsets object.
         *
         * @param topicPartition The topic partition to store the offsets for.
         * @throws NullPointerException if topicPartition is {@code null}.
         */
        TopicPartitionOffsets(final TopicPartition topicPartition) {
            this.topicPartition = Objects.requireNonNull(topicPartition);
        }

        /**
         * Registers the given offset. To be invoked when the record has just been received.
         * @param offset The offset to register.
         * @return The added queue entry.
         */
        public OffsetsQueueEntry addOffset(final long offset) {
            cleanupAndUpdateLastCompletedOffset();
            final OffsetsQueueEntry queueEntry = new OffsetsQueueEntry(offset);
            queue.add(queueEntry);
            return queueEntry;
        }

        /**
         * Gets the partition offset to use for an offset commit. That is the offset of the last record in the row of
         * fully handled records if it either hasn't been committed yet or the time of the last commit is older than
         * the 'skipOffsetRecommitPeriodSeconds'. Note that for the actual commit, {@code 1} has to be added to the
         * returned value.
         * <p>
         * Otherwise an empty Optional is returned.
         *
         * @return The offset wrapped in an Optional or an empty Optional if no offset commit is needed.
         */
        public Optional<Long> getLastSequentiallyCompletedOffsetForCommit() {
            cleanupAndUpdateLastCompletedOffset();
            if (lastSequentiallyCompletedOffset == -1) {
                return Optional.empty();
            }
            if (!queue.isEmpty()) {
                log.trace("getOffsetsToCommit: record with offset {} to use for commit is {} entries behind last received offset {}; partition [{}]",
                        lastSequentiallyCompletedOffset, queue.size(), queue.getLast().getOffset(), topicPartition);
            }
            if (lastSequentiallyCompletedOffset != lastCommittedOffset) {
                return Optional.of(lastSequentiallyCompletedOffset);
            } else if (lastCommitTime != null
                    && lastCommitTime.isBefore(Instant.now().minusSeconds(skipOffsetRecommitPeriodSeconds))) {
                log.trace("getOffsetsToCommit: record with offset {} will be recommitted (last commit {} too long ago); partition [{}]",
                        lastSequentiallyCompletedOffset, lastCommitTime, topicPartition);
                return Optional.of(lastSequentiallyCompletedOffset);
            } else {
                return Optional.empty();
            }
        }

        private void cleanupAndUpdateLastCompletedOffset() {
            while (Optional.ofNullable(queue.peek()).map(OffsetsQueueEntry::isHandlingComplete).orElse(false)) {
                lastSequentiallyCompletedOffset = queue.remove().getOffset();
            }
        }

        /**
         * Marks the given offset (as returned by {@link #getLastSequentiallyCompletedOffsetForCommit()}) as
         * committed. Note that the offset here is the offset of the last completed record, the value in the actual
         * commit is this value plus {@code 1}.
         *
         * @param offset The offset to set.
         */
        public void setLastCommittedOffset(final long offset) {
            if (offset >= lastCommittedOffset) {
                this.lastCommitTime = Instant.now();
                this.lastCommittedOffset = offset;
            }
        }

        /**
         * Checks whether all received records have been marked as completed.
         *
         * @return {@code true} if all received records are completed.
         */
        public boolean allCompleted() {
            cleanupAndUpdateLastCompletedOffset();
            return queue.isEmpty();
        }

        /**
         * Checks whether there is an already completed record whose offset hasn't been committed yet.
         *
         * @return {@code true} if an offset commit is needed for this TopicPartition.
         */
        public boolean needsCommit() {
            cleanupAndUpdateLastCompletedOffset();
            return lastSequentiallyCompletedOffset != -1 && lastSequentiallyCompletedOffset != lastCommittedOffset;
        }

        /**
         * Gets information about the state of this object suitable for log output.
         *
         * @return The info string.
         */
        public String getStateInfo() {
            cleanupAndUpdateLastCompletedOffset();
            return '{' + "lastSequentiallyCompletedOffset=" + lastSequentiallyCompletedOffset
                    + ", lastCommittedOffset=" + lastCommittedOffset
                    + (queue.size() <= 20 ? ", queue=" + queue : ", queue.size=" + queue.size())
                    + '}';
        }
    }

    /**
     * An entry used in the TopicPartitionOffsets.
     */
    static class OffsetsQueueEntry {
        private final long offset;
        private final AtomicBoolean handlingComplete = new AtomicBoolean();

        /**
         * Creates a new OffsetsQueueEntry.
         * @param offset The offset of the entry.
         */
        OffsetsQueueEntry(final long offset) {
            this.offset = offset;
        }

        /**
         * Gets the offset.
         * @return The offset.
         */
        public long getOffset() {
            return offset;
        }

        /**
         * Marks the handling of the record corresponding to this entry as completed.
         */
        public void setHandlingComplete() {
            handlingComplete.set(true);
        }

        /**
         * Checks whether the handling of the record corresponding to this entry has been marked as completed.
         * @return {@code true} if record handling is complete.
         */
        public boolean isHandlingComplete() {
            return handlingComplete.get();
        }

        @Override
        public String toString() {
            return offset  + (handlingComplete.get() ? " (completed)" : "");
        }
    }
}
