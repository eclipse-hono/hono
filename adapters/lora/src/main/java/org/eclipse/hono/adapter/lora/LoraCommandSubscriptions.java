/*******************************************************************************
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hono.adapter.lora.providers.LoraProvider;
import org.eclipse.hono.client.command.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.TriTuple;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Class that holds the Lora adapter command subscriptions, that are shared between multiple {@link LoraProtocolAdapter}
 * verticles. It also takes care to close the subscriptions for deleted/disabled tenants.
 */
public class LoraCommandSubscriptions {

    private final Vertx vertx;
    private final Tracer tracer;
    private final Map<SubscriptionKey, TriTuple<ProtocolAdapterCommandConsumer, LoraProvider, Context>> commandSubscriptions;

    /**
     * Creates a new Lora command subscriptions class.
     *
     * @param vertx The Vert.x instance to use.
     * @param tracer The tracer instance.
     */
    public LoraCommandSubscriptions(final Vertx vertx, final Tracer tracer) {
        this.vertx = vertx;
        this.tracer = tracer;
        this.commandSubscriptions = new ConcurrentHashMap<>();
    }

    /**
     * Subscribes for tenant disabled/deleted events, in order to close the consumer objects associated with them later.
     */
    public void init() {
        NotificationEventBusSupport.registerConsumer(vertx, TenantChangeNotification.TYPE,
                notification -> {
                    if (LifecycleChange.DELETE.equals(notification.getChange())
                            || (LifecycleChange.UPDATE.equals(notification.getChange())
                                    && !notification.isTenantEnabled())) {
                        closeConsumersForTenant(notification.getTenantId());
                    }
                });

        NotificationEventBusSupport.registerConsumer(vertx, DeviceChangeNotification.TYPE,
                notification -> {
                    if (LifecycleChange.DELETE.equals(notification.getChange())) {
                        closeConsumersForGateway(notification.getTenantId(), notification.getDeviceId());
                    }
                });
    }

    private void closeConsumersForGateway(final String tenantId, final String gatewayId) {
        final SubscriptionKey key = new SubscriptionKey(tenantId, gatewayId);
        final var subscription = commandSubscriptions.remove(key);
        if (subscription == null) {
            return;
        }

        // Clear currently active span, i.e. the event bus message span, so that it isn't used as parent span.
        tracer.activateSpan(null);
        final Context currentCtx = vertx.getOrCreateContext();
        final Span span = TracingHelper
                .buildSpan(tracer, null, "close command subscription for deleted gateway",
                        getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .start();
        TracingHelper.setDeviceTags(span, tenantId, gatewayId);
        final Context subscriptionContext = subscription.three();
        subscriptionContext.runOnContext(x -> {
            final ProtocolAdapterCommandConsumer commandConsumer = subscription.one();
            commandConsumer.close(false, span.context()).onComplete(r -> {
                currentCtx.runOnContext(s -> span.finish());
            });
        });
    }

    private void closeConsumersForTenant(final String tenantId) {
        if (!commandSubscriptions.entrySet().stream().anyMatch(e -> e.getKey().getTenant().equals(tenantId))) {
            return;
        }

        // Clear currently active span, i.e. the event bus message span, so that it isn't used as parent span.
        tracer.activateSpan(null);
        final Context currentCtx = vertx.getOrCreateContext();
        final Span span = TracingHelper
                .buildSpan(tracer, null, "close command subscriptions for disabled/deleted tenant",
                        getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .start();
        TracingHelper.setDeviceTags(span, tenantId, null);
        final List<Future<Void>> consumerCloseFutures = Collections.synchronizedList(new ArrayList<>());
        final var iter = commandSubscriptions.entrySet().iterator();
        while (iter.hasNext()) {
            final var subscription = iter.next();
            if (!subscription.getKey().getTenant().equals(tenantId)) {
                continue;
            }

            final Promise<Void> closePromise = Promise.promise();
            consumerCloseFutures.add(closePromise.future());
            final Context subscriptionContext = subscription.getValue().three();
            subscriptionContext.runOnContext(x -> {
                final ProtocolAdapterCommandConsumer commandConsumer = subscription.getValue().one();
                commandConsumer.close(false, span.context()).onComplete(closePromise);
            });
            iter.remove();
        }

        Future.join(consumerCloseFutures).onComplete(x -> {
            currentCtx.runOnContext(s -> span.finish());
        });
    }

    /**
     * Checks whether a subscription exists.
     *
     * @param key the {@link SubscriptionKey}.
     * @return whether the specified subscription exists.
     */
    public boolean contains(final SubscriptionKey key) {
        return commandSubscriptions.containsKey(key);
    }

    /**
     * Get a subscription by a given {@link SubscriptionKey}.
     *
     * @param key The {@link SubscriptionKey}.
     * @return the specified subscription. Returns <code>null</code> if the subscription doesn't exist.
     */
    public TriTuple<ProtocolAdapterCommandConsumer, LoraProvider, Context> getSubscription(final SubscriptionKey key) {
        return commandSubscriptions.get(key);
    }

    /**
     * Adds/Registers a new subscription.
     *
     * @param key The {@link SubscriptionKey}.
     * @param consumer The consumer.
     * @param loraProvider The Lora provider.
     * @param ctx The vertx context used for later execution of code on the Lora adapter verticle.
     * @return The previous subscription on that {@link SubscriptionKey}, <code>null</code> if there wasn't any.
     */
    public TriTuple<ProtocolAdapterCommandConsumer, LoraProvider, Context> add(final SubscriptionKey key,
            final ProtocolAdapterCommandConsumer consumer, final LoraProvider loraProvider, final Context ctx) {
        return commandSubscriptions.put(key, TriTuple.of(consumer, loraProvider, ctx));
    }
}
