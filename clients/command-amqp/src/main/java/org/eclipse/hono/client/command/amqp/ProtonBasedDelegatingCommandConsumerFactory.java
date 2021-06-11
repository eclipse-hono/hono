/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command.amqp;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.CommandTargetMapper.CommandTargetMapperContext;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory.CommandHandlingAdapterInfoAccess;
import org.eclipse.hono.client.SendMessageSampler.Factory;
import org.eclipse.hono.client.amqp.AbstractServiceClient;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.command.CommandConsumerFactory;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.DeviceConnectionClient;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.util.RegistrationAssertion;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;


/**
 * A vertx-proton based factory for creating consumers of command messages received via the
 * AMQP 1.0 Messaging Network.
 * <p>
 * This implementation supports delegation of command message handling to another protocol adapter instance
 * by routing the message on an adapter-instance specific address.
 * <p>
 * This functionality is implemented via the wrapped default {@link ProtocolAdapterCommandConsumerFactory}
 * implementation.
 *
 */
public class ProtonBasedDelegatingCommandConsumerFactory extends AbstractServiceClient implements CommandConsumerFactory {

    private final ProtocolAdapterCommandConsumerFactory factory;
    private final CommandTargetMapper commandTargetMapper;
    private final DeviceConnectionClient deviceConnectionClient;

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the AMQP 1.0 Messaging Network.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param deviceConnectionClient The client to use for accessing the Device Connection service.
     * @param deviceRegistrationClient The client to use for accessing the Device Registration service.
     * @param tracer The OpenTracing tracer to use for tracking the processing of messages.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public ProtonBasedDelegatingCommandConsumerFactory(
            final HonoConnection connection,
            final Factory samplerFactory,
            final DeviceConnectionClient deviceConnectionClient,
            final DeviceRegistrationClient deviceRegistrationClient,
            final Tracer tracer) {

        super(connection, samplerFactory);

        this.deviceConnectionClient = Objects.requireNonNull(deviceConnectionClient);
        Objects.requireNonNull(deviceRegistrationClient);
        Objects.requireNonNull(tracer);

        commandTargetMapper = CommandTargetMapper.create(tracer);
        commandTargetMapper.initialize(new CommandTargetMapperContext() {

            @Override
            public Future<List<String>> getViaGateways(
                    final String tenant,
                    final String deviceId,
                    final SpanContext context) {

                Objects.requireNonNull(tenant);
                Objects.requireNonNull(deviceId);

                return deviceRegistrationClient.assertRegistration(tenant, deviceId, null, context)
                        .map(RegistrationAssertion::getAuthorizedGateways);
            }

            @Override
            public Future<JsonObject> getCommandHandlingAdapterInstances(
                    final String tenant,
                    final String deviceId,
                    final List<String> viaGateways,
                    final SpanContext context) {

                Objects.requireNonNull(tenant);
                Objects.requireNonNull(deviceId);
                Objects.requireNonNull(viaGateways);

                return deviceConnectionClient.getCommandHandlingAdapterInstances(
                        tenant, deviceId, viaGateways, context);
            }
        });
        factory = ProtocolAdapterCommandConsumerFactory.create(connection, samplerFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CommandConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandHandler,
            final Duration lifespan,
            final SpanContext context) {

        return factory.createCommandConsumer(
                tenantId,
                deviceId,
                ctx -> commandHandler.handle(new ProtonBasedLegacyCommandContextWrapper(ctx)),
                lifespan,
                context)
                .map(adapterCommandConsumer -> {
                    return new CommandConsumer() {

                        @Override
                        public Future<Void> close(final SpanContext spanContext) {
                            return adapterCommandConsumer.close(spanContext);
                        }
                    };
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CommandConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Handler<CommandContext> commandHandler,
            final Duration lifespan,
            final SpanContext context) {

        return factory.createCommandConsumer(
                tenantId,
                deviceId,
                gatewayId,
                ctx -> commandHandler.handle(new ProtonBasedLegacyCommandContextWrapper(ctx)),
                lifespan,
                context)
                .map(adapterCommandConsumer -> {
                    return new CommandConsumer() {

                        @Override
                        public Future<Void> close(final SpanContext spanContext) {
                            return adapterCommandConsumer.close(spanContext);
                        }
                    };
                });
    }

    @Override
    public Future<Void> start() {
        return connection.connect()
                .onSuccess(ok -> log.info("connection to {} endpoint has been established", connection.getConfig().getServerRole()))
                .onFailure(t -> log.warn("failed to establish connection to {} endpoint", connection.getConfig().getServerRole(), t))
                .onComplete(ar -> {
                    // initialize factory (also if connection attempt failed - retry logic takes care of that)
                    factory.initialize(commandTargetMapper, new CommandHandlingAdapterInfoAccess() {

                        @Override
                        public Future<Void> setCommandHandlingAdapterInstance(
                                final String tenant,
                                final String deviceId,
                                final String adapterInstanceId,
                                final Duration lifespan,
                                final SpanContext context) {
                            return deviceConnectionClient.setCommandHandlingAdapterInstance(tenant, deviceId,
                                    adapterInstanceId, lifespan, context);
                        }

                        @Override
                        public Future<Void> removeCommandHandlingAdapterInstance(
                                final String tenant,
                                final String deviceId,
                                final String adapterInstanceId,
                                final SpanContext context) {
                            return deviceConnectionClient.removeCommandHandlingAdapterInstance(tenant, deviceId,
                                    adapterInstanceId, context);
                        }
                    });
                })
                .mapEmpty();
    }
}
