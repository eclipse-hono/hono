/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.kafka.client.test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.kafka.client.common.PartitionInfo;
import io.vertx.kafka.client.common.impl.Helper;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.KafkaWriteStream;
import io.vertx.kafka.client.producer.RecordMetadata;

/**
 * This is a fake Kafka producer. It provides the Vert.x abstraction for the {@link MockProducer} of Kafka's client.
 * <p>
 * This does not support every Vert.x <em>stream</em> related operation.
 * <p>
 * <b>Usage Example:</b>
 * <pre>
 * {@code
 *     final FakeProducer<String, String> fakeProducer = new FakeProducer<>();
 *
 *     final Handler<AsyncResult<RecordMetadata>> resultHandler = asyncResult -> {
 *         if (asyncResult.succeeded()) {
 *             System.out.println("Record produced successfully to topic: " + asyncResult.result().getTopic());
 *         } else {
 *             System.out.println("Sending record failed: " + asyncResult.cause().getMessage());
 *         }
 *     };
 *
 *     // send two records
 *     fakeProducer.send(new KafkaProducerRecordImpl<>("my-topic", "first message"), resultHandler);
 *     fakeProducer.send(new KafkaProducerRecordImpl<>("my-topic", "second message"), resultHandler);
 *
 *     final MockProducer<String, String> mockProducer = fakeProducer.getMockProducer();
 *
 *     // completes the result handler of the first record successfully
 *     mockProducer.completeNext();
 *     // fails the result handler of the second record
 *     mockProducer.errorNext(new KafkaException("something went wrong"));
 *
 *     // inspect the sent records
 *     final ProducerRecord<String, String> firstRecord = mockProducer.history().get(0);
 *     System.out.println(firstRecord);
 *
 *     final ProducerRecord<String, String> secondRecord = mockProducer.history().get(1);
 *     System.out.println(secondRecord);
 *
 *     // clean the history...
 *     mockProducer.clear();
 *     // ... or close it if you are done
 *     mockProducer.close();
 * }
 * </pre>
 *
 * @param <K> The type for the record key serialization.
 * @param <V> The type for the record value serialization.
 */
public class FakeProducer<K, V> implements KafkaProducer<K, V> {

    private final MockProducer<K, V> producer;
    private Handler<Throwable> exceptionHandler;

    /**
     * Creates a fake producer with a new instance of {@link MockProducer#MockProducer()}.
     */
    public FakeProducer() {
        producer = new MockProducer<>();
    }

    /**
     * Creates a fake producer with the given mock producer instance.
     *
     * @param producer The mock producer to be used.
     */
    public FakeProducer(final MockProducer<K, V> producer) {
        this.producer = producer;
    }

    /**
     * Gets the underlying {@link MockProducer}.
     *
     * @return the mock producer.
     */
    public MockProducer<K, V> getMockProducer() {
        return producer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KafkaProducer<K, V> exceptionHandler(final Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KafkaProducer<K, V> write(final KafkaProducerRecord<K, V> kafkaProducerRecord) {
        write(kafkaProducerRecord, null);
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation simply returns {@code this}.
     */
    @Override
    public KafkaProducer<K, V> setWriteQueueMaxSize(final int i) {
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}.
     */
    @Override
    public boolean writeQueueFull() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation simply returns {@code this}.
     */
    @Override
    public KafkaProducer<K, V> drainHandler(final Handler<Void> handler) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KafkaProducer<K, V> write(final KafkaProducerRecord<K, V> data, final Handler<AsyncResult<Void>> handler) {
        Handler<AsyncResult<RecordMetadata>> mdHandler = null;
        if (handler != null) {
            mdHandler = ar -> handler.handle(ar.mapEmpty());
        }
        send(data, mdHandler);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void end() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void end(final Handler<AsyncResult<Void>> handler) {
        if (handler != null) {
            handler.handle(Future.succeededFuture());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KafkaProducer<K, V> send(final KafkaProducerRecord<K, V> record) {
        send(record, null);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KafkaProducer<K, V> send(final KafkaProducerRecord<K, V> record,
            final Handler<AsyncResult<RecordMetadata>> handler) {

        try {
            producer.send(record.record(), (metadata, err) -> {
                if (err != null) {
                    if (exceptionHandler != null) {
                        exceptionHandler.handle(err);
                    }
                    if (handler != null) {
                        handler.handle(Future.failedFuture(err));
                    }
                } else if (handler != null) {
                    handler.handle(Future.succeededFuture(Helper.from(metadata)));
                }
            });
        } catch (Exception e) {
            if (exceptionHandler != null) {
                exceptionHandler.handle(e);
            }

            if (handler != null) {
                handler.handle(Future.failedFuture(e));
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KafkaProducer<K, V> partitionsFor(final String topic,
            final Handler<AsyncResult<List<PartitionInfo>>> handler) {

        final List<org.apache.kafka.common.PartitionInfo> partitionInfoList = producer.partitionsFor(topic);

        final List<PartitionInfo> partitions = partitionInfoList.stream().map(kafkaPartitionInfo -> new PartitionInfo()
                .setInSyncReplicas(
                        Stream.of(kafkaPartitionInfo.inSyncReplicas()).map(Helper::from).collect(Collectors.toList()))
                .setLeader(Helper.from(kafkaPartitionInfo.leader()))
                .setPartition(kafkaPartitionInfo.partition())
                .setReplicas(Stream.of(kafkaPartitionInfo.replicas()).map(Helper::from).collect(Collectors.toList()))
                .setTopic(kafkaPartitionInfo.topic())).collect(Collectors.toList());

        handler.handle(Future.succeededFuture(partitions));

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KafkaProducer<K, V> flush(final Handler<Void> completionHandler) {
        producer.flush();
        if (completionHandler != null) {
            completionHandler.handle(null);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        close(0L, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close(final Handler<AsyncResult<Void>> completionHandler) {
        close(0L, completionHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close(final long timeout, final Handler<AsyncResult<Void>> completionHandler) {
        producer.close();
        if (completionHandler != null) {
            completionHandler.handle(Future.succeededFuture());
        }
    }

    /**
     * Not implemented.
     *
     * @throws UnsupportedOperationException always.
     */
    @Override
    public KafkaWriteStream<K, V> asStream() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Producer<K, V> unwrap() {
        return producer;
    }
}
