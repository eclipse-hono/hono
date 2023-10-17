/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterResult;
import org.eclipse.hono.commandrouter.CommandRouterService;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

/**
 * An implementation of Hono's <em>Command Router</em> API.
 */
public class CommandRouterServiceImpl implements CommandRouterService, HealthCheckProvider, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(CommandRouterServiceImpl.class);

    private final ServiceConfigProperties config;
    private final DeviceRegistrationClient registrationClient;
    private final TenantClient tenantClient;
    private final DeviceConnectionInfo deviceConnectionInfo;
    private final MessagingClientProvider<CommandConsumerFactory> commandConsumerFactoryProvider;
    private final MessagingClientProvider<EventSender> eventSenderProvider;
    private final AdapterInstanceStatusService adapterInstanceStatusService;
    private final Tracer tracer;
    private final Deque<Pair<String, Integer>> tenantsToEnable = new ArrayDeque<>();
    private final Set<String> reenabledTenants = new HashSet<>();
    private final Set<String> tenantsInProcess = new HashSet<>();
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * Vert.x context that this service has been started in.
     */
    private Context context;

    /**
     * Creates a new CommandRouterServiceImpl.
     *
     * @param config The command router's AMQP server configuration.
     * @param registrationClient The device registration client.
     * @param tenantClient The tenant client.
     * @param deviceConnectionInfo The client for accessing device connection data.
     * @param commandConsumerFactoryProvider The factory provider to use for creating clients to receive commands.
     * @param eventSenderProvider The factory provider to use for creating clients to send events.
     * @param adapterInstanceStatusService The service providing info about the status of adapter instances.
     * @param tracer The Open Tracing tracer to use for tracking processing of requests.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CommandRouterServiceImpl(
            final ServiceConfigProperties config,
            final DeviceRegistrationClient registrationClient,
            final TenantClient tenantClient,
            final DeviceConnectionInfo deviceConnectionInfo,
            final MessagingClientProvider<CommandConsumerFactory> commandConsumerFactoryProvider,
            final MessagingClientProvider<EventSender> eventSenderProvider,
            final AdapterInstanceStatusService adapterInstanceStatusService,
            final Tracer tracer) {

        this.config = Objects.requireNonNull(config);
        this.registrationClient = Objects.requireNonNull(registrationClient);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.deviceConnectionInfo = Objects.requireNonNull(deviceConnectionInfo);
        this.commandConsumerFactoryProvider = Objects.requireNonNull(commandConsumerFactoryProvider);
        this.eventSenderProvider = Objects.requireNonNull(eventSenderProvider);
        this.adapterInstanceStatusService = Objects.requireNonNull(adapterInstanceStatusService);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * Sets the vert.x context to run on.
     * <p>
     * If not set explicitly, the context is determined during startup.
     *
     * @param context The context.
     */
    void setContext(final Context context) {
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public Future<Void> start() {
        if (context == null) {
            context = Vertx.currentContext();
            if (context == null) {
                return Future.failedFuture(new IllegalStateException("Service must be started in a Vert.x context"));
            }
        }
        if (!commandConsumerFactoryProvider.containsImplementations()) {
            return Future.failedFuture("no command consumer factory provider set");
        }

        if (running.compareAndSet(false, true)) {
            registrationClient.start();
            tenantClient.start();
            if (deviceConnectionInfo instanceof Lifecycle) {
                ((Lifecycle) deviceConnectionInfo).start();
            }
            commandConsumerFactoryProvider.start();
            eventSenderProvider.start();
            adapterInstanceStatusService.start();
        }

        deviceConnectionInfo.setDeviceToAdapterMappingErrorListener((tenantId, deviceId, adapterInstanceId, span) -> {
            return sendDisconnectEventIfNeeded(tenantId, deviceId, true, adapterInstanceId, span);

        });
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stop() {
        LOG.info("stopping command router");

        if (running.compareAndSet(true, false)) {
            final List<Future<Void>> results = new ArrayList<>();
            results.add(registrationClient.stop());
            results.add(tenantClient.stop());
            if (deviceConnectionInfo instanceof Lifecycle) {
                results.add(((Lifecycle) deviceConnectionInfo).stop());
            }
            results.add(eventSenderProvider.stop());
            results.add(commandConsumerFactoryProvider.stop());
            results.add(adapterInstanceStatusService.stop());
            tenantsToEnable.clear();
            return Future.join(results)
                    .onFailure(t -> {
                        LOG.info("error while stopping command router", t);
                    })
                    .map(ok -> {
                        LOG.info("successfully stopped command router");
                        return (Void) null;
                    });
        } else {
            return Future.succeededFuture();
        }
    }

    @Override
    public Future<CommandRouterResult> setLastKnownGatewayForDevice(final String tenantId, final String deviceId,
            final String gatewayId, final Span span) {

        return deviceConnectionInfo.setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, span)
                .map(ok -> CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT));
    }

    @Override
    public Future<CommandRouterResult> setLastKnownGatewayForDevice(final String tenantId,
            final Map<String, String> deviceIdToGatewayIdMap, final Span span) {
        return deviceConnectionInfo.setLastKnownGatewayForDevice(tenantId, deviceIdToGatewayIdMap, span)
                .map(ok -> CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT));
    }

    @Override
    public Future<CommandRouterResult> registerCommandConsumer(final String tenantId, final String deviceId,
            final boolean sendEvent, final String adapterInstanceId, final Duration lifespan, final Span span) {

        final Future<TenantObject> tenantObjectFuture = tenantClient.get(tenantId, span.context());
        return tenantObjectFuture
                .compose(tenantObject -> {
                    final CommandConsumerFactory primaryFactory = commandConsumerFactoryProvider
                            .getClient(tenantObject);
                    final Future<Void> primaryConsumerFuture = primaryFactory.createCommandConsumer(tenantId,
                            span.context());

                    if (primaryFactory.getMessagingType() == MessagingType.kafka
                            && commandConsumerFactoryProvider.getClient(MessagingType.amqp) != null) {
                        // tenant is configured to use Kafka but AMQP is also configured
                        span.log("also creating secondary, AMQP-based consumer");
                        final Future<Void> amqpConsumerFuture = commandConsumerFactoryProvider
                                .getClient(MessagingType.amqp)
                                .createCommandConsumer(tenantId, span.context());
                        return Future.join(primaryConsumerFuture, amqpConsumerFuture)
                                .map(v -> (Void) null)
                                .recover(thr -> {
                                    if (amqpConsumerFuture.failed()) {
                                        span.log("ignoring failure to create secondary, AMQP-based command consumer");
                                    }
                                    return primaryConsumerFuture;
                                });
                    }
                    // the reverse case of an AMQP-configured tenant while Kafka is available is handled implicitly
                    // because of the Kafka wildcard topic subscription (no auto-creation triggered in that case)
                    return primaryConsumerFuture;
                })
                .compose(v -> deviceConnectionInfo
                        .setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId,
                                getSanitizedLifespan(lifespan), span)
                        .onFailure(thr -> {
                            LOG.info("error setting command handling adapter instance [tenant: {}, device: {}]",
                                    tenantId, deviceId, thr);
                        }))
                .compose(v2 -> {
                    if (sendEvent) {
                        return sendConnectedTtdEvent(tenantObjectFuture.result(), deviceId, adapterInstanceId,
                                span.context()).onFailure(thr -> {
                                    LOG.info("error sending connected Ttd event [tenant: {}, device: {}]",
                                            tenantId, deviceId, thr);
                                });
                    } else {
                        return Future.succeededFuture();
                    }
                })
                .map(v3 -> CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT))
                .otherwise(t -> CommandRouterResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    private Duration getSanitizedLifespan(final Duration lifespan) {
        // lifespan greater than what can be expressed in nanoseconds (i.e. 292 years) is considered unlimited, preventing ArithmeticExceptions down the road
        return lifespan == null || lifespan.isNegative()
                || lifespan.getSeconds() > (Long.MAX_VALUE / 1000_000_000L) ? Duration.ofSeconds(-1) : lifespan;
    }

    @Override
    public Future<CommandRouterResult> unregisterCommandConsumer(final String tenantId, final String deviceId,
            final boolean sendEvent, final String adapterInstanceId, final Span span) {

        return deviceConnectionInfo
                .removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span)
                .recover(thr -> {
                    if (ServiceInvocationException.extractStatusCode(thr) != HttpURLConnection.HTTP_PRECON_FAILED) {
                        LOG.info("error removing command handling adapter instance [tenant: {}, device: {}]", tenantId,
                                deviceId, thr);
                        // for no precon-failed errors continue and send the event
                        return sendDisconnectEventIfNeeded(tenantId, deviceId, sendEvent, adapterInstanceId, span)
                                // keep the original error and not propagate the event send result
                                .recover(thr2 -> Future.failedFuture(thr)).compose(v -> Future.failedFuture(thr));
                    } else {
                        // for precon-failed errors do not send the event
                        return Future.failedFuture(thr);
                    }
                })
                .compose(v -> sendDisconnectEventIfNeeded(tenantId, deviceId, sendEvent, adapterInstanceId, span))
                .map(v -> CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT))
                .otherwise(t -> CommandRouterResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    private Future<Void> sendDisconnectEventIfNeeded(final String tenantId, final String deviceId,
            final boolean sendEvent, final String adapterInstanceId, final Span span) {
        if (sendEvent) {
            return tenantClient.get(tenantId, span.context())
                    .compose(tenantObject -> sendDisconnectedTtdEvent(tenantObject, deviceId, adapterInstanceId,
                            span.context())
                                    .onFailure(thr -> {
                                        LOG.info("error sending disconnected Ttd event [tenant: {}, device: {}]",
                                                tenantId, deviceId, thr);
                                    }));
        } else {
            return Future.succeededFuture();
        }
    }

    @Override
    public Future<CommandRouterResult> enableCommandRouting(final List<String> tenantIds, final Span span) {

        if (!running.get()) {
            return Future.succeededFuture(CommandRouterResult.from(HttpURLConnection.HTTP_UNAVAILABLE));
        }

        Objects.requireNonNull(tenantIds);
        final boolean isProcessingRequired = tenantsToEnable.isEmpty();
        tenantIds.stream()
            .filter(s -> !reenabledTenants.contains(s))
            .filter(s -> !tenantsInProcess.contains(s))
            .filter(s -> !tenantsToEnable.stream()
                .anyMatch(entry -> entry.one().equals(s)))
            .forEach(s -> tenantsToEnable.addLast(Pair.of(s, 1)));

        if (isProcessingRequired) {
            LOG.debug("triggering re-enabling of command routing");
            processTenantQueue(span.context());
        }
        return Future.succeededFuture(CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT));
    }

    private void processTenantQueue(final SpanContext tracingContext) {

        final var attempt = tenantsToEnable.pollFirst();
        if (attempt == null) {
            // at this point there might still be pending (re-try) tasks on the event loop,
            // thus we need to wait for those to have finished before declaring victory
            if (tenantsInProcess.isEmpty()) {
                reenabledTenants.clear();
                LOG.debug("finished re-enabling of command routing");
            }
        } else {
            tenantsInProcess.add(attempt.one());
            final long delay = calculateDelayMillis(attempt.two());
            if (delay <= 0) {
                context.runOnContext(go -> activateCommandRouting(attempt, tracingContext));
            } else {
                context.owner().setTimer(delay, tid -> activateCommandRouting(attempt, tracingContext));
            }
        }
    }

    private long calculateDelayMillis(final int attemptNo) {
        if (attemptNo == 1) {
            // run first attempt immediately
            return 0L;
        } else if (attemptNo > 6) {
            // cap delay at 10secs
            return 10000L;
        } else {
            // wait for 2^attemptNo times 100 millis before retrying
            return (1 << attemptNo) * 100L;
        }
    }

    private void activateCommandRouting(
            final Pair<String, Integer> attempt,
            final SpanContext tracingContext) {

        if (!running.get()) {
            // component has been stopped, no need to create command consumer in this case
            tenantsInProcess.remove(attempt.one());
            return;
        }

        final Span span = tracer.buildSpan("re-enable command routing for tenant")
                .addReference(References.FOLLOWS_FROM, tracingContext)
                .withTag(TracingHelper.TAG_TENANT_ID, attempt.one())
                .start();
        final var logEntries = new HashMap<String, Object>(2);
        logEntries.put("attempt#", attempt.two());
        tenantClient.get(attempt.one(), span.context())
            .map(tenantObject -> commandConsumerFactoryProvider.getClient(tenantObject))
            .map(factory -> factory.createCommandConsumer(attempt.one(), span.context()))
            .onSuccess(ok -> {
                logEntries.put(Fields.MESSAGE, "successfully created command consumer");
                span.log(logEntries);
                reenabledTenants.add(attempt.one());
            })
            .onFailure(t -> {
                logEntries.put(Fields.MESSAGE, "failed to create command consumer");
                logEntries.put(Fields.ERROR_OBJECT, t);
                TracingHelper.logError(span, logEntries);
                if (t instanceof ServerErrorException) {
                    // add to end of queue in order to retry at a later time
                    LOG.info("failed to create command consumer [attempt#: {}]", attempt.two(), t);
                    span.log("marking tenant for later re-try to create command consumer");
                    tenantsToEnable.addLast(Pair.of(attempt.one(), attempt.two() + 1));
                }
            })
            .onComplete(r -> {
                span.finish();
                tenantsInProcess.remove(attempt.one());
                processTenantQueue(tracingContext);
            });
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        if (registrationClient instanceof ServiceClient client) {
            client.registerReadinessChecks(handler);
        }
        if (tenantClient instanceof ServiceClient client) {
            client.registerReadinessChecks(handler);
        }
        if (deviceConnectionInfo instanceof ServiceClient client) {
            client.registerReadinessChecks(handler);
        }
        commandConsumerFactoryProvider.registerReadinessChecks(handler);
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        registerEventLoopBlockedCheck(handler);
        if (registrationClient instanceof ServiceClient client) {
            client.registerLivenessChecks(handler);
        }
        if (tenantClient instanceof ServiceClient client) {
            client.registerLivenessChecks(handler);
        }
        if (deviceConnectionInfo instanceof ServiceClient client) {
            client.registerLivenessChecks(handler);
        }
        commandConsumerFactoryProvider.registerLivenessChecks(handler);
    }

    /**
     * Registers a health check which tries to run an action on the context that this service was started in.
     * <p>
     * If the vert.x event loop of that context is blocked, the health check procedure will not complete
     * with OK status within the defined timeout.
     *
     * @param handler The health check handler to register the checks with.
     */
    protected void registerEventLoopBlockedCheck(final HealthCheckHandler handler) {

        handler.register(
                "event-loop-blocked-check",
                config.getEventLoopBlockedCheckTimeout(),
                procedure -> {
                    final Context currentContext = Vertx.currentContext();

                    if (currentContext != context) {
                        context.runOnContext(action -> {
                            procedure.tryComplete(Status.OK());
                        });
                    } else {
                        LOG.debug("Command router - HealthCheck Server context match. Assume protocol adapter is alive.");
                        procedure.tryComplete(Status.OK());
                    }
                });
    }

    /**
     * Sends an <em>empty notification</em> event for a device that will remain connected for an indeterminate amount of
     * time.
     *
     * @param tenant The tenant that the device belongs to, who owns the device.
     * @param deviceId The device for which the TTD is reported.
     * @param adapterInstanceId The adapter instanceId or {@code null}.
     * @param context The currently active OpenTracing span that is used to trace the sending of the event.
     * @return A future indicating the outcome of the operation. The future will be succeeded if the TTD event has been
     *         sent downstream successfully. Otherwise, it will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     */
    protected final Future<Void> sendConnectedTtdEvent(
            final TenantObject tenant,
            final String deviceId,
            final String adapterInstanceId,
            final SpanContext context) {

        return sendTtdEvent(tenant, deviceId, adapterInstanceId, MessageHelper.TTD_VALUE_UNLIMITED, context);
    }

    /**
     * Sends an <em>empty notification</em> event for a device that has disconnected from a protocol adapter.
     *
     * @param tenant The tenant object that the device belongs to, who owns the device.
     * @param deviceId The device for which the TTD is reported.
     * @param adapterInstanceId The authenticated device or {@code null}.
     * @param context The currently active OpenTracing span that is used to trace the sending of the event.
     * @return A future indicating the outcome of the operation. The future will be succeeded if the TTD event has been
     *         sent downstream successfully. Otherwise, it will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     */
    protected final Future<Void> sendDisconnectedTtdEvent(
            final TenantObject tenant,
            final String deviceId,
            final String adapterInstanceId,
            final SpanContext context) {

        return sendTtdEvent(tenant, deviceId, adapterInstanceId, 0, context);
    }

    private EventSender getEventSender(final TenantObject tenant) {
        return eventSenderProvider.getClient(tenant);
    }

    private Future<Void> sendTtdEvent(
            final TenantObject tenant,
            final String deviceId,
            final String adapterInstanceId,
            final Integer ttd,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(ttd);

        final Map<String, Object> props = new HashMap<>();
        props.put(MessageHelper.APP_PROPERTY_ORIG_ADAPTER, adapterInstanceId);
        props.put(MessageHelper.APP_PROPERTY_QOS, QoS.AT_LEAST_ONCE.ordinal());
        props.put(CommandConstants.MSG_PROPERTY_DEVICE_TTD, ttd);
        return getEventSender(tenant).sendEvent(
                tenant,
                new RegistrationAssertion(deviceId),
                EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION,
                null,
                props,
                context)
                .onSuccess(s -> LOG.debug(
                        "successfully sent TTD notification [tenant: {}, device-id: {}, TTD: {}]",
                        tenant, deviceId, ttd))
                .onFailure(t -> LOG.debug(
                        "failed to send TTD notification [tenant: {}, device-id: {}, TTD: {}]",
                        tenant, deviceId, ttd));
    }
}
