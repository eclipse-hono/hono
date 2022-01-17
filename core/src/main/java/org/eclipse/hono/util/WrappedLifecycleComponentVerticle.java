/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import java.util.Objects;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

/**
 * Enables a component with life cycle support to be used as a vert.x verticle.
 */
public class WrappedLifecycleComponentVerticle extends AbstractVerticle {

    private final Lifecycle lifecycleComponent;

    /**
     * Creates a new WrappedLifecycleComponentVerticle.
     *
     * @param lifecycleComponent The component whose <em>start</em> and <em>stop</em> methods shall be called
     *                           on {@link #start(Promise)} and {@link #stop(Promise)} of this verticle.
     * @throws NullPointerException If lifecycleComponent is {@code null}.
     */
    public WrappedLifecycleComponentVerticle(final Lifecycle lifecycleComponent) {
        this.lifecycleComponent = Objects.requireNonNull(lifecycleComponent);
    }

    @Override
    public void start(final Promise<Void> startPromise) {
        lifecycleComponent.start().onComplete(startPromise);
    }

    @Override
    public void stop(final Promise<Void> stopPromise) {
        lifecycleComponent.stop().onComplete(stopPromise);
    }
}
