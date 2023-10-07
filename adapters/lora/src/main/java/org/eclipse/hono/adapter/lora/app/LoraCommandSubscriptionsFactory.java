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

package org.eclipse.hono.adapter.lora.app;

import org.eclipse.hono.adapter.lora.LoraCommandSubscriptions;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A factory class that creates Lora adapter command subscriptions class. It'll always be a {@link Singleton}.
 */
@ApplicationScoped
public class LoraCommandSubscriptionsFactory {

    /**
     * {@link Singleton} of the Lora command subscriptions class.
     *
     * @param vertx The Vert.x instance to use.
     * @return Always returns a {@link Singleton}.
     */
    @Singleton
    @Produces
    LoraCommandSubscriptions commandSubscriptions(final Vertx vertx, final Tracer tracer) {
        final LoraCommandSubscriptions commandSubscriptions = new LoraCommandSubscriptions(vertx, tracer);
        commandSubscriptions.init();
        return commandSubscriptions;
    }
}
