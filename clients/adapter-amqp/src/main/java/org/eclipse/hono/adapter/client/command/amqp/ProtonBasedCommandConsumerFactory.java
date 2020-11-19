/**
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


package org.eclipse.hono.adapter.client.command.amqp;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.adapter.client.amqp.AbstractServiceClient;
import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.adapter.client.command.CommandConsumer;
import org.eclipse.hono.adapter.client.command.CommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.CommandTargetMapper.CommandTargetMapperContext;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory.CommandHandlingAdapterInfoAccess;
import org.eclipse.hono.client.SendMessageSampler.Factory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationAssertion;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;


/**
 * A vertx-proton based factory for creating consumers of command messages received via the
 * AMQP 1.0 Messaging Network.
 * <p>
 * This implementation wraps a {@link ProtocolAdapterCommandConsumerFactory} and thus also supports
 * routing of commands to a target protocol adapter instance.
 *
 */
public class ProtonBasedCommandConsumerFactory extends AbstractServiceClient implements CommandConsumerFactory {

    private final ProtocolAdapterCommandConsumerFactory factory;

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the AMQP 1.0 Messaging Network.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @param deviceConnectionClient The client to use for accessing the Device Connection service.
     * @param deviceRegistrationClient The client to use for accessing the Device Registration service.
     * @param tracer The OpenTracing tracer to use for tracking the processing of messages.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public ProtonBasedCommandConsumerFactory(
            final HonoConnection connection,
            final Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig,
            final DeviceConnectionClient deviceConnectionClient,
            final DeviceRegistrationClient deviceRegistrationClient,
            final Tracer tracer) {

        super(connection, samplerFactory, adapterConfig);

        Objects.requireNonNull(deviceConnectionClient);
        Objects.requireNonNull(deviceRegistrationClient);
        Objects.requireNonNull(tracer);

        final CommandTargetMapper commandTargetMapper = CommandTargetMapper.create(tracer);
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
        factory.initialize(commandTargetMapper, new CommandHandlingAdapterInfoAccess() {

            @Override
            public Future<Void> setCommandHandlingAdapterInstance(
                    final String tenant,
                    final String deviceId,
                    final String adapterInstanceId,
                    final Duration lifespan,
                    final SpanContext context) {
                return deviceConnectionClient.setCommandHandlingAdapterInstance(tenant, deviceId, adapterInstanceId, lifespan, context);
            }

            @Override
            public Future<Void> removeCommandHandlingAdapterInstance(
                    final String tenant,
                    final String deviceId,
                    final String adapterInstanceId,
                    final SpanContext context) {
                return deviceConnectionClient.removeCommandHandlingAdapterInstance(tenant, deviceId, adapterInstanceId, context);
            }
        });
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
                ctx -> {
                    commandHandler.handle(new CommandContextAdapter(ctx));
                },
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
                ctx -> {
                    commandHandler.handle(new CommandContextAdapter(ctx));
                },
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

    private static class CommandContextAdapter implements CommandContext {

        private final org.eclipse.hono.client.CommandContext ctx;
        private final ProtonBasedCommand command;

        /**
         * Creates a new adapter for a context.
         *
         * @throws NullPointerException if context is {@code null}.
         */
        CommandContextAdapter(final org.eclipse.hono.client.CommandContext context) {
            this.ctx = Objects.requireNonNull(context);
            this.command = new ProtonBasedCommand(context.getCommand());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void logCommandToSpan(final Span span) {
            command.logToSpan(span);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Command getCommand() {
            return command;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void accept() {
            ctx.accept();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void release() {
            ctx.release();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void modify(final boolean deliveryFailed, final boolean undeliverableHere) {
            ctx.modify(deliveryFailed, undeliverableHere);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void reject(final String cause) {
            final ErrorCondition error = ProtonHelper.condition(Constants.AMQP_BAD_REQUEST, cause);
            ctx.reject(error);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T get(final String key) {
            return ctx.get(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T get(final String key, final T defaultValue) {
            return ctx.get(key, defaultValue);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void put(final String key, final Object value) {
            ctx.put(key, value);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SpanContext getTracingContext() {
            return ctx.getTracingContext();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Span getTracingSpan() {
            return ctx.getTracingSpan();
        }
    }
}
