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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * A factory for creating clients for the <em>Kafka messaging infrastructure</em> to receive commands.
 * <p>
 * This factory uses wild-card based topic pattern to subscribe for commands, which receives the command 
 * messages irrespective of the tenants.
 * <p>
 * Command messages are first received by the kafka based consumer. It is then determined which protocol adapter instance
 * can handle the command. The command is then forwarded to the Kafka cluster on an topic containing that adapter
 * instance id.
 */
public class KafkaBasedCommandConsumerFactoryImpl implements CommandConsumerFactory {
    private static final Logger log = LoggerFactory.getLogger(KafkaBasedCommandConsumerFactoryImpl.class);
    private static final Pattern COMMANDS_TOPIC_PATTERN = Pattern
            .compile(Pattern.quote(HonoTopic.Type.COMMAND.prefix) + ".*");

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final KafkaConsumer<String, Buffer> kafkaConsumer;
    private final KafkaBasedInternalCommandSender internalCommandSender;
    private final Tracer tracer;

    private CommandTargetMapper commandTargetMapper;
    private KafkaBasedMappingAndDelegatingCommandHandler commandHandler;

    /**
     * Creates a new factory to process commands via the Kafka cluster.
     *
     * @param vertx The Vert.x instance to use.
     * @param kafkaProducerFactory The producer factory for creating Kafka producers for sending messages.
     * @param kafkaProducerConfig The Kafka producer configuration.
     * @param kafkaConsumerConfig The Kafka consumer configuration.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedCommandConsumerFactoryImpl(
            final Vertx vertx,
            final KafkaProducerFactory<String, Buffer> kafkaProducerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig,
            final KafkaConsumerConfigProperties kafkaConsumerConfig,
            final Tracer tracer) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(kafkaProducerFactory);
        Objects.requireNonNull(kafkaProducerConfig);
        Objects.requireNonNull(kafkaConsumerConfig);
        Objects.requireNonNull(tracer);

        this.internalCommandSender = new KafkaBasedInternalCommandSender(kafkaProducerFactory, kafkaProducerConfig, tracer);
        final Map<String, String> consumerConfig = kafkaConsumerConfig.getConsumerConfig("cmd-router");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "cmd-router-group");
        this.kafkaConsumer = KafkaConsumer.create(vertx, consumerConfig, String.class, Buffer.class);
        this.tracer = tracer;
    }

    @Override
    public void initialize(final CommandTargetMapper commandTargetMapper) {
        Objects.requireNonNull(commandTargetMapper);
        this.commandTargetMapper = commandTargetMapper;
        initialized.set(true);
    }

    @Override
    public Future<Void> start() {
        final Promise<Void> subscriptionPromise = Promise.promise();
        final Promise<Void> partitionsAssignedPromise = Promise.promise();

        if (!initialized.get()) {
            return Future.failedFuture("not initialized");
        }

        commandHandler = new KafkaBasedMappingAndDelegatingCommandHandler(commandTargetMapper, internalCommandSender,
                tracer);
        //TODO in the next iteration: handling of offsets and commits. 
        // And if required, to use a consumer(at most once) class similar to the AbstractAtLeastOnceKafkaConsumer
        kafkaConsumer
                .handler(commandHandler::mapAndDelegateIncomingCommandMessage)
                .partitionsAssignedHandler(ok -> partitionsAssignedPromise.tryComplete())
                .exceptionHandler(error -> log.error("consumer error occurred", error))
                .subscribe(COMMANDS_TOPIC_PATTERN, subscriptionPromise);

        return commandHandler.start()
                .compose(ok -> CompositeFuture.all(subscriptionPromise.future(), partitionsAssignedPromise.future()))
                .map(ok -> {
                    log.debug("subscribed to topic pattern [{}]", COMMANDS_TOPIC_PATTERN);
                    return null;
                });
    }

    @Override 
    public Future<Void> stop() {
        final Promise<Void> consumerClosePromise = Promise.promise();

        kafkaConsumer.close(consumerClosePromise);

        return CompositeFuture.all(commandHandler.stop(), consumerClosePromise.future())
                .mapEmpty();
    }

    @Override
    public Future<Void> createCommandConsumer(final String tenantId, final SpanContext context) {
        // The consumer is created already in start method as it uses topic pattern (irrespective of tenant-id) to
        // subscribe.
        return Future.succeededFuture();
    }
}
