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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RebalanceInProgressException;
import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.common.impl.Helper;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;

/**
 * A Kafka consumer that automatically commits partition offsets corresponding to the latest record per
 * partition whose (asynchronous) handling has been marked as completed.
 * <p>
 * <b>Commit handling</b>
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
 * When a partition gets revoked from this consumer, the consumer will delay committing the corresponding offset
 * for up to the period defined by <em>hono.offsets.commit.record.completion.timeout.millis</em> until record handling
 * Futures concerning that partition have been completed.
 * <p>
 * In contrast to the <em>enable.auto.commit</em> behaviour, identical offsets are skipped during successive commits.
 * It is only after a period defined by <em>hono.offsets.skip.recommit.period.seconds</em> has elapsed, that such offsets
 * are committed again. This is to make sure that such offsets don't reach their retention time, provided the recommit
 * period is lower than the <em>offsets.retention.minutes</em> broker config value.
 * <p>
 * <b>Rate limiting of record handling</b>
 * <p>
 * This consumer limits the number of records being currently in processing to prevent memory issues and reduce the
 * load on dependent services.
 * When the number of records for which handling is still incomplete has reached the throttling threshold of
 * {@value #THROTTLING_THRESHOLD_PERCENTAGE_OF_MAX_POLL_RECORDS} percent of the configured maximum number of records per
 * poll operation (<em>max.poll.records</em> config value), then polling is paused for a short time. If the number of
 * unprocessed records still isn't getting less, the poll operation is resumed (so that associated management task are
 * still done) but record fetching from all assigned topic partitions is suspended until the throttling threshold is
 * reached again.
 * The overall limit, i.e. the maximum number of incomplete record handler result futures at a given point in time, is
 * calculated from the above mentioned throttling threshold plus the maximum number of records per poll operation.
 *
 * @param <V> The type of record payload this consumer can process.
 */
public class AsyncHandlingAutoCommitKafkaConsumer<V> extends HonoKafkaConsumer<V> {

    /**
     * The name of the configuration property to define the period for which committing an already committed offset is
     * skipped, until it may be recommitted if it is still the latest offset.
     * This is to ensure that the offset commit message isn't being discarded at some point
     * (see the <em>offsets.retention.minutes</em> broker config setting, default being 7 days).
     */
    public static final String CONFIG_HONO_OFFSETS_SKIP_RECOMMIT_PERIOD_SECONDS = "hono.offsets.skip.recommit.period.seconds";
    /**
     * The name of the configuration property to define for how many milliseconds to wait for the completion of yet
     * incomplete record result futures before doing a synchronous offset commit as part of handling the revocation
     * of a partition assignment, e.g. when the consumer is being closed.
     * <p>
     * The value of that property should be chosen with regard to not adding too much delay when a rebalance happens
     * (e.g. this could potentially delay the result of {@link #ensureTopicIsAmongSubscribedTopicPatternTopics(String)})
     * and not choosing a too small period which would increase the chance of records getting handled twice by this and
     * another consumer.
     */
    public static final String CONFIG_HONO_OFFSETS_COMMIT_RECORD_COMPLETION_TIMEOUT_MILLIS = "hono.offsets.commit.record.completion.timeout.millis";
    /**
     * The default periodic commit interval.
     */
    public static final Duration DEFAULT_COMMIT_INTERVAL = Duration.ofSeconds(5);
    /**
     * The default period for which committing an already committed offset is skipped,
     * until it may be recommitted if it is still the latest offset.
     * This is to ensure that the offset commit message isn't being discarded at some point
     * (see the <em>offsets.retention.minutes</em> broker config setting, default being 7 days).
     */
    public static final Duration DEFAULT_OFFSETS_SKIP_RECOMMIT_PERIOD = Duration.ofHours(1);
    /**
     * The default amount of time to wait for the completion of yet incomplete record result futures before doing
     * a synchronous offset commit as part of handling the revocation of a partition assignment, e.g. when the
     * consumer is being closed.
     */
    public static final Duration DEFAULT_OFFSETS_COMMIT_RECORD_COMPLETION_TIMEOUT = Duration.ofMillis(300);
    /**
     * The maximum amount of time to pause record polling because the current number of records in processing
     * has exceeded the throttling threshold.
     */
    public static final Duration MAX_POLL_PAUSE = Duration.ofMillis(200);
    /**
     * Percentage of the <em>max.poll.records</em> config value to use as the threshold for throttling record
     * processing.
     */
    public static final int THROTTLING_THRESHOLD_PERCENTAGE_OF_MAX_POLL_RECORDS = 50;

    private static final Logger LOG = LoggerFactory.getLogger(AsyncHandlingAutoCommitKafkaConsumer.class);

    private final int throttlingThreshold;
    private final int throttlingResumeDelta;
    private final long commitIntervalMillis;
    private final long skipOffsetRecommitPeriodSeconds;
    private final long offsetsCommitRecordCompletionTimeoutMillis;
    private final Map<TopicPartition, TopicPartitionOffsets> offsetsMap = new HashMap<>();
    /**
     * Map keeping the last offsets committed by this consumer for the partitions of the subscribed topics.
     * It is used to skip unnecessary offset commits while not having to query the committed offsets from the server.
     * <p>
     * Offsets here are the same as used in consumer.position() and consumer.commit() invocations (i.e. they refer to
     * the next record to be read).
     */
    private final Map<TopicPartition, Long> lastKnownCommittedOffsets = new HashMap<>();
    private final AtomicBoolean periodicCommitInvocationInProgress = new AtomicBoolean();
    private final AtomicBoolean periodicCommitRetryAfterRebalanceNeeded = new AtomicBoolean();
    private final AtomicBoolean skipPeriodicCommit = new AtomicBoolean();
    private final AtomicInteger recordsInProcessingCounter = new AtomicInteger();
    private final AtomicInteger recordsLeftInBatchCounter = new AtomicInteger();
    private final AtomicReference<UncompletedRecordsCompletionLatch> uncompletedRecordsCompletionLatchRef = new AtomicReference<>();

    private Instant fetchingPauseStartTime = Instant.MAX;
    private Long periodicCommitTimerId;
    private Instant lastPollInstant = Instant.EPOCH;

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
            final Function<KafkaConsumerRecord<String, V>, Future<Void>> recordHandler,
            final Map<String, String> consumerConfig) {
        this(vertx, topics, null, recordHandler, consumerConfig);
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
            final Function<KafkaConsumerRecord<String, V>, Future<Void>> recordHandler,
            final Map<String, String> consumerConfig) {
        this(vertx, null, topicPattern, recordHandler, consumerConfig);
    }

    private AsyncHandlingAutoCommitKafkaConsumer(
            final Vertx vertx,
            final Set<String> topics,
            final Pattern topicPattern,
            final Function<KafkaConsumerRecord<String, V>, Future<Void>> recordHandler,
            final Map<String, String> consumerConfig) {
        super(vertx, topics, topicPattern, validateAndAdaptConsumerConfig(consumerConfig));
        setRecordHandler(record -> this.handleRecord(record, recordHandler));

        final int maxPollRecords = getMaxPollRecordsConfig(consumerConfig); // default 500, in which case the overall maxRecordsInProcessing limit is 750
        this.throttlingThreshold = Math.max(maxPollRecords * THROTTLING_THRESHOLD_PERCENTAGE_OF_MAX_POLL_RECORDS / 100, 1); // default 250
        this.throttlingResumeDelta = throttlingThreshold * 5 / 100;

        this.commitIntervalMillis = getCommitInterval(consumerConfig);
        this.skipOffsetRecommitPeriodSeconds = getSkipOffsetRecommitPeriodSeconds(consumerConfig);
        this.offsetsCommitRecordCompletionTimeoutMillis = getOffsetsCommitRecordCompletionTimeoutMillis(consumerConfig);
    }

    @Override
    protected final void onBatchOfRecordsReceived(final KafkaConsumerRecords<String, V> records) {
        recordsLeftInBatchCounter.set(records.size());
        lastPollInstant = Instant.now();
    }

    private void handleRecord(
            final KafkaConsumerRecord<String, V> record,
            final Function<KafkaConsumerRecord<String, V>, Future<Void>> recordHandler) {
        // check whether consumer needs to be paused
        final int recordsLeftInBatch = recordsLeftInBatchCounter.decrementAndGet();
        final int recordsInProcessing = recordsInProcessingCounter.incrementAndGet();
        if (recordsInProcessing >= throttlingThreshold) {
            if (lastPollInstant.plus(MAX_POLL_PAUSE).isAfter(Instant.now()) && (recordsLeftInBatch > 0 || recordsInProcessing == throttlingThreshold)
                    && pauseRecordHandlingAndPolling(MAX_POLL_PAUSE)) {
                // short-term measure: pausing on a per-record basis (short-term because polling mustn't be paused for too long)
                LOG.debug("paused consumer record handling/polling; no. of records in processing: {}, throttling threshold: {} [client-id: {}]",
                        recordsInProcessing, throttlingThreshold, getClientId());
            } else if (recordsLeftInBatch == 0 && pauseRecordFetching()) {
                // last poll too long ago, don't pause polling any more but instead let each poll from now on return an empty batch;
                // this will keep "recordsInProcessing" from exceeding "throttlingThreshold + maxPollRecords" (i.e. 750 by default)
                LOG.info("suspending record fetching; no. of records in processing: {}, throttling threshold: {} [client-id: {}]",
                        recordsInProcessing, throttlingThreshold, getClientId());
                fetchingPauseStartTime = Instant.now();
            } // else: we have already paused polling for too long, so, until we've reached the last of the batch, we can only let the already fetched records be handled here
        }
        final TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
        final OffsetsQueueEntry offsetsQueueEntry = setRecordReceived(record.offset(), topicPartition);
        try {
            recordHandler.apply(record)
                    .onComplete(ar -> setRecordHandlingComplete(offsetsQueueEntry, topicPartition));
        } catch (final Exception e) {
            LOG.warn("error handling record [topic: {}, partition: {}, offset: {}, headers: {}] [client-id: {}]",
                    record.topic(), record.partition(), record.offset(), record.headers(), getClientId(), e);
            setRecordHandlingComplete(offsetsQueueEntry, topicPartition);
        }
    }

    private void setRecordHandlingComplete(final OffsetsQueueEntry offsetsQueueEntry, final TopicPartition topicPartition) {
        offsetsQueueEntry.setHandlingComplete();
        synchronized (uncompletedRecordsCompletionLatchRef) {
            Optional.ofNullable(uncompletedRecordsCompletionLatchRef.get())
                    .ifPresent(latch -> latch.onRecordHandlingCompleted(topicPartition));
        }
        final int recordsInProcessing = recordsInProcessingCounter.decrementAndGet();
        if (recordsInProcessing <= throttlingThreshold && resumeRecordFetching()) {
            LOG.info("resumed consumer record fetching after {}ms; current no. of records in processing: {} [client-id: {}]",
                    Duration.between(fetchingPauseStartTime, Instant.now()).toMillis(), recordsInProcessing, getClientId());
        } else if (recordsInProcessing <= (throttlingThreshold - throttlingResumeDelta) && resumeRecordHandlingAndPolling()) {
            LOG.debug("resumed consumer record polling; current no. of records in processing: {} [client-id: {}]",
                    recordsInProcessing, getClientId());
        }
    }

    private static Map<String, String> validateAndAdaptConsumerConfig(final Map<String, String> consumerConfig) {
        if (Strings.isNullOrEmpty(consumerConfig.get(ConsumerConfig.GROUP_ID_CONFIG))) {
            throw new IllegalArgumentException(ConsumerConfig.GROUP_ID_CONFIG + " config entry has to be set");
        }
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return consumerConfig;
    }

    private int getMaxPollRecordsConfig(final Map<String, String> consumerConfig) {
        return Optional.ofNullable(consumerConfig.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG))
                .map(Integer::parseInt)
                .orElse(500);
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

    private static long getOffsetsCommitRecordCompletionTimeoutMillis(final Map<String, String> consumerConfig) {
        return Optional.ofNullable(consumerConfig.get(CONFIG_HONO_OFFSETS_COMMIT_RECORD_COMPLETION_TIMEOUT_MILLIS))
                .map(Long::parseUnsignedLong)
                .orElse(DEFAULT_OFFSETS_COMMIT_RECORD_COMPLETION_TIMEOUT.toMillis());
    }

    @Override
    protected void onRecordHandlerSkippedForExpiredRecord(final KafkaConsumerRecord<String, V> record) {
        final OffsetsQueueEntry queueEntry = setRecordReceived(record.offset(),
                new TopicPartition(record.topic(), record.partition()));
        queueEntry.setHandlingComplete();
    }

    @Override
    public Future<Void> start() {
        addOnKafkaConsumerReadyHandler(ready -> {
            periodicCommitTimerId = vertx.setPeriodic(commitIntervalMillis, tid -> doPeriodicCommit());
        });
        return super.start();
    }

    @Override
    public Future<Void> stop() {
        if (periodicCommitTimerId != null) {
            vertx.cancelTimer(periodicCommitTimerId);
        }
        return super.stop()
                // go through offsetsMap in order to log missing commits
                .onComplete(v -> clearObsoleteTopicPartitionOffsets(List.of()));
    }

    @Override
    protected void onPartitionsAssignedBlocking(final Set<io.vertx.kafka.client.common.TopicPartition> partitionsSet) {
        clearObsoleteTopicPartitionOffsets(getUnderlyingConsumer().assignment());
        // remove lastKnownCommittedOffsets entries belonging to deleted topics
        if (topicPattern != null) {
            final Set<String> subscribedTopicPatternTopics = getSubscribedTopicPatternTopics();
            lastKnownCommittedOffsets.entrySet()
                    .removeIf(entry -> !subscribedTopicPatternTopics.contains(entry.getKey().topic()));
        }
        if (!partitionsSet.isEmpty() && isAutoOffsetResetConfigLatest()) {
            // for each partition ensure an offset gets committed on the next commit if there possibly has never been a commit before;
            // otherwise records published during an upcoming rebalance might be skipped if the partition gets assigned
            // to another consumer which then just begins reading on the latest offset, not the offset from before the rebalance
            ensureOffsetCommitsExistForNewlyAssignedPartitions(partitionsSet);
        }
        skipPeriodicCommit.set(false);
        if (periodicCommitRetryAfterRebalanceNeeded.get()) {
            runOnContext(v -> {
                if (periodicCommitRetryAfterRebalanceNeeded.compareAndSet(true, false)) {
                    doPeriodicCommit();
                }
            });
        }
    }

    // synchronized because offsetsMap is accessed from vert.x event loop and kafka polling thread
    private synchronized void ensureOffsetCommitsExistForNewlyAssignedPartitions(
            final Set<io.vertx.kafka.client.common.TopicPartition> partitionsSet) {
        final List<TopicPartition> partitionsForNextCommit = new ArrayList<>();

        final Set<TopicPartition> partitionsToCheckCommittedOffsetsFor = partitionsSet.stream().map(Helper::to)
                .filter(partition -> !offsetsMap.containsKey(partition) && lastKnownCommittedOffsets.get(partition) == null)
                .collect(Collectors.toSet());
        // fetch committed offsets so that we only add to partitionsForNextCommit if really needed
        fetchCommittedOffsetsOnPartitionsAssigned(partitionsToCheckCommittedOffsetsFor);
        partitionsSet.stream().map(Helper::to)
                .filter(partition -> !offsetsMap.containsKey(partition))
                .forEach(partition -> {
                    try {
                        final long position = getUnderlyingConsumer().position(partition);
                        final boolean positionCommitted = Optional
                                .ofNullable(lastKnownCommittedOffsets.get(partition))
                                .map(committedPos -> committedPos.equals(position)).orElse(false);
                        if (!positionCommitted) {
                            partitionsForNextCommit.add(partition);
                        }
                        offsetsMap.put(partition,
                                new TopicPartitionOffsets(partition, position, positionCommitted));
                    } catch (final Exception ex) {
                        LOG.warn("error fetching position for newly assigned partition [{}] [client-id: {}]", partition,
                                getClientId(), ex);
                    }
                });
        if (LOG.isDebugEnabled() && !partitionsForNextCommit.isEmpty()) {
            LOG.debug("onPartitionsAssigned: partitions to be part of next offset commit: [{}]",
                    HonoKafkaConsumerHelper.getPartitionsDebugString(partitionsForNextCommit));
        }
    }

    private void fetchCommittedOffsetsOnPartitionsAssigned(final Set<TopicPartition> partitions) {
        if (!partitions.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("onPartitionsAssigned: fetching committed offsets for [{}]", HonoKafkaConsumerHelper.getPartitionsDebugString(partitions));
            }
            try {
                getUnderlyingConsumer().committed(partitions).forEach((partition, position) -> {
                    if (position != null) {
                        lastKnownCommittedOffsets.put(partition, position.offset());
                    } else {
                        lastKnownCommittedOffsets.remove(partition);
                    }
                });
            } catch (final Exception ex) {
                LOG.warn("error fetching committed offsets for newly assigned partitions [{}] [client-id: {}]",
                        HonoKafkaConsumerHelper.getPartitionsDebugString(partitions),
                        getClientId(), ex);
            }
        }
    }

    @Override
    protected void onPartitionsRevokedBlocking(final Set<io.vertx.kafka.client.common.TopicPartition> partitionsSet) {
        skipPeriodicCommit.set(true);
        // potentially wait some time for record processing to finish before committing offsets
        if (!partitionsSet.isEmpty() && offsetsCommitRecordCompletionTimeoutMillis > 0) {
            UncompletedRecordsCompletionLatch latch = null;
            synchronized (uncompletedRecordsCompletionLatchRef) {
                final var uncompletedRecordsPartitions = getUncompletedRecordsPartitions(Helper.to(partitionsSet));
                if (!uncompletedRecordsPartitions.isEmpty()) {
                    LOG.info("init latch to wait up to {}ms for the completion of record handling concerning {} [client-id: {}]",
                            offsetsCommitRecordCompletionTimeoutMillis,
                            uncompletedRecordsPartitions.size() <= 10 ? uncompletedRecordsPartitions.keySet()
                                    : (uncompletedRecordsPartitions.size() + " partitions"),
                            getClientId());
                    latch = new UncompletedRecordsCompletionLatch(uncompletedRecordsPartitions);
                    uncompletedRecordsCompletionLatchRef.set(latch);
                }
            }
            if (latch != null) {
                try {
                    if (latch.await(offsetsCommitRecordCompletionTimeoutMillis, TimeUnit.MILLISECONDS)) {
                        LOG.trace("latch to wait for the completion of record handling was released in time");
                    } else {
                        LOG.info("timed out waiting for record handling to finish after {}ms [client-id: {}]",
                                offsetsCommitRecordCompletionTimeoutMillis, getClientId());
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    uncompletedRecordsCompletionLatchRef.set(null);
                }
            }
        }
        commitOffsetsSync();
        // offsetsMap kept unchanged here - will be updated on the following onPartitionsAssigned
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
                if (LOG.isTraceEnabled()) {
                    LOG.trace("commitSync; offsets: [{}]", HonoKafkaConsumerHelper.getOffsetsDebugString(offsets));
                }
                // commit invoked on the underlying consumer, so that it is run synchronously on the current thread
                getUnderlyingConsumer().commitSync(offsets);
                setCommittedOffsets(offsets);
                LOG.trace("commitSync succeeded");
            } catch (final Exception e) {
                LOG.warn("commit failed: {} [client-id: {}]", e, getClientId());
            }
        } else {
            LOG.trace("skip commitSync - no offsets to commit");
        }
    }

    private void doPeriodicCommit() {
        if (skipPeriodicCommit.get()) {
            return;
        }
        periodicCommitRetryAfterRebalanceNeeded.set(false);
        if (!periodicCommitInvocationInProgress.compareAndSet(false, true)) {
            LOG.trace("periodic commit already triggered, skipping invocation");
            return;
        }
        // run periodic commit on the kafka polling thread, to be able to call commitAsync (not provided by the vert.x KafkaConsumer)
        runOnKafkaWorkerThread(v -> {
            final var offsets = getOffsetsToCommit();
            if (!offsets.isEmpty()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("do periodic commit; offsets: [{}]", HonoKafkaConsumerHelper.getOffsetsDebugString(offsets));
                }
                try {
                    getUnderlyingConsumer().commitAsync(offsets, (committedOffsets, error) -> {
                        if (error instanceof RebalanceInProgressException) {
                            LOG.debug("could not do periodic commit: {} [client-id: {}]", error, getClientId());
                            if (isCooperativeRebalancingConfigured()) {
                                // with cooperative rebalancing, there isn't necessarily an offset commit during the rebalance, so retry the offset commit after the rebalance
                                periodicCommitRetryAfterRebalanceNeeded.set(true);
                            }
                        } else if (error != null) {
                            LOG.info("periodic commit failed: {} [client-id: {}]", error, getClientId());
                        } else {
                            LOG.trace("periodic commit succeeded");
                            setCommittedOffsets(committedOffsets);
                        }
                    });
                } catch (final Exception ex) {
                    LOG.error("error doing periodic commit [client-id: {}]", getClientId(), ex);
                }
            } else {
                LOG.trace("skip periodic commit - no offsets to commit");
            }
            periodicCommitInvocationInProgress.set(false);
        });
    }

    // synchronized because offsetsMap is accessed from vert.x event loop and kafka polling thread
    private synchronized Map<TopicPartition, TopicPartitionOffsets> getUncompletedRecordsPartitions(final Set<TopicPartition> partitions) {
        return offsetsMap.entrySet().stream()
                .filter(entry -> partitions.contains(entry.getKey()) && !entry.getValue().allCompleted())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // synchronized because offsetsMap is accessed from vert.x event loop and kafka polling thread
    private synchronized OffsetsQueueEntry setRecordReceived(final long recordOffset, final TopicPartition topicPartition) {
        return offsetsMap.computeIfAbsent(topicPartition, k -> new TopicPartitionOffsets(topicPartition))
                .addOffset(recordOffset);
    }

    // synchronized because offsetsMap is accessed from vert.x event loop and kafka polling thread
    private synchronized void clearObsoleteTopicPartitionOffsets(final Collection<TopicPartition> currentlyAssignedPartitions) {
        Objects.requireNonNull(currentlyAssignedPartitions);
        final var partitionOffsetsIterator = offsetsMap.entrySet().iterator();
        while (partitionOffsetsIterator.hasNext()) {
            final var topicPartitionOffsetsEntry = partitionOffsetsIterator.next();
            if (!currentlyAssignedPartitions.contains(topicPartitionOffsetsEntry.getKey())) {
                if (topicPartitionOffsetsEntry.getValue().needsCommit()) {
                    LOG.warn("partition [{}] not assigned to consumer [{}] anymore but latest handled record offset hasn't been committed yet! {}",
                            topicPartitionOffsetsEntry.getKey(), getClientId(), topicPartitionOffsetsEntry.getValue().getStateInfo());
                } else if (!topicPartitionOffsetsEntry.getValue().allCompleted()) {
                    LOG.debug("partition [{}] not assigned to consumer [{}] anymore but not all read records have been fully processed yet! {}",
                            topicPartitionOffsetsEntry.getKey(), getClientId(), topicPartitionOffsetsEntry.getValue().getStateInfo());
                } else {
                    LOG.trace("partition [{}] not assigned to consumer anymore; no still outstanding offset commits there",
                            topicPartitionOffsetsEntry.getKey());
                }
                partitionOffsetsIterator.remove();
            }
        }
    }

    /**
     * Checks whether offset commits are currently needed for the partitions of the given topic.
     * <p>
     * This may be used to check whether a topic can safely be deleted if no further incoming records are expected.
     *
     * @param topic The topic to check.
     * @return {@code true} if offsets need to be committed.
     */
    public boolean isOffsetsCommitNeededForTopic(final String topic) {
        return getOffsetsToCommit().keySet().stream().anyMatch(tp -> tp.topic().equals(topic));
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
            lastKnownCommittedOffsets.put(partition, offsetAndMetadata.offset());
        });
    }

    /**
     * Keeps offset data for a TopicPartition.
     */
    class TopicPartitionOffsets {

        private static final long UNDEFINED_OFFSET = -2;
        private final TopicPartition topicPartition;
        private final Deque<OffsetsQueueEntry> queue = new ArrayDeque<>();

        private long lastSequentiallyCompletedOffset = UNDEFINED_OFFSET;
        private long lastCommittedOffset = UNDEFINED_OFFSET;
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
         * Creates a new TopicPartitionOffsets object with the initial fetch position.
         * <p>
         * If the <em>initialPositionCommitted</em> parameter is {@code false}, meaning the given position hasn't
         * (necessarily) been committed yet, the position will be marked as to be committed in the next offset commit,
         * as indicated by the return values of {@link #needsCommit()} and {@link #getLastSequentiallyCompletedOffsetForCommit()}.
         *
         * @param topicPartition The topic partition to store the offsets for.
         * @param initialPosition The offset of the next record that will be fetched for this partition.
         * @param initialPositionCommitted {@code true} if the given <em>initialPosition</em>> is known to have already
         *            been committed.
         * @throws NullPointerException if topicPartition is {@code null}.
         */
        TopicPartitionOffsets(final TopicPartition topicPartition, final long initialPosition, final boolean initialPositionCommitted) {
            this(topicPartition);
            this.lastSequentiallyCompletedOffset = initialPosition - 1;
            this.lastCommittedOffset = initialPositionCommitted ? this.lastSequentiallyCompletedOffset : UNDEFINED_OFFSET;
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
            if (lastSequentiallyCompletedOffset == UNDEFINED_OFFSET) {
                return Optional.empty();
            }
            if (!queue.isEmpty()) {
                LOG.trace("getOffsetsToCommit: offset {} to use for commit is {} entries behind last received offset {}; partition [{}]",
                        lastSequentiallyCompletedOffset, queue.size(), queue.getLast().getOffset(), topicPartition);
            }
            if (lastSequentiallyCompletedOffset != lastCommittedOffset) {
                return Optional.of(lastSequentiallyCompletedOffset);
            } else if (lastCommitTime != null
                    && lastCommitTime.isBefore(Instant.now().minusSeconds(skipOffsetRecommitPeriodSeconds))) {
                LOG.trace("getOffsetsToCommit: offset {} will be recommitted (last commit {} too long ago); partition [{}]",
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
            return lastSequentiallyCompletedOffset != UNDEFINED_OFFSET && lastSequentiallyCompletedOffset != lastCommittedOffset;
        }

        /**
         * Gets information about the state of this object suitable for log output.
         *
         * @return The info string.
         */
        public String getStateInfo() {
            cleanupAndUpdateLastCompletedOffset();
            return '{' + "lastSequentiallyCompletedOffset=" + getOffsetString(lastSequentiallyCompletedOffset)
                    + ", lastCommittedOffset=" + getOffsetString(lastCommittedOffset)
                    + (queue.size() <= 20 ? ", queue=" + queue : ", queue.size=" + queue.size())
                    + '}';
        }

        private String getOffsetString(final long offset) {
            return offset == UNDEFINED_OFFSET ? "undefined" : Long.toString(offset);
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

    /**
     * A latch to wait for record result futures to be completed concerning a given set of partitions.
     */
    class UncompletedRecordsCompletionLatch {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final Map<TopicPartition, TopicPartitionOffsets> uncompletedRecordsPartitions;

        UncompletedRecordsCompletionLatch(final Map<TopicPartition, TopicPartitionOffsets> uncompletedRecordsPartitions) {
            this.uncompletedRecordsPartitions = uncompletedRecordsPartitions;
        }

        /**
         * To be invoked when handling of a record with the given partition is completed.
         * <p>
         * Checks if this means that handling of records in all relevant partitions is completed
         * and releases the latch in that case.
         *
         * @param partition The partition the completed record is associated with.
         */
        public void onRecordHandlingCompleted(final TopicPartition partition) {
            final TopicPartitionOffsets offsets = uncompletedRecordsPartitions.get(partition);
            if (offsets != null && offsets.allCompleted()) {
                uncompletedRecordsPartitions.remove(partition);
                if (uncompletedRecordsPartitions.isEmpty()) {
                    latch.countDown();
                }
            }
        }

        /**
         * Waits for handling of all records to be completed and the latch to be released.
         *
         * @param timeout The maximum time to wait.
         * @param unit The time unit of the {@code timeout} argument.
         * @return {@code true} if the count reached zero and {@code false}
         *         if the waiting time elapsed before the count reached zero
         * @throws InterruptedException if the current thread is interrupted while waiting.
         */
        public boolean await(final long timeout, final TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }
}
