/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import java.util.Objects;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;


/**
 * A base class for implementing <em>Verticle</em>s.
 *
 * This class provides support for accessing configuration properties.
 *
 * @param <T> The type of configuration properties this verticle supports.
 *
 */
public abstract class ConfigurationSupportingVerticle<T> extends AbstractVerticle {

    private T config;

    /**
     * Sets the properties to use for configuring this <em>Verticle</em>.
     *
     * @param configuration The configuration properties.
     * @throws NullPointerException if configuration is {@code null}.
     */
    public final void setConfig(final T configuration) {
        this.config = Objects.requireNonNull(configuration);
    }

    /**
     * Gets the properties that this <em>Verticle</em> has been configured with.
     *
     * @return The properties or {@code null} if not set.
     */
    public final T getConfig() {
        return this.config;
    }

    /**
     * Gets the vert.x context that this verticle is associated with.
     *
     * @return The context or {@code null} if this verticle has not been deployed (yet).
     */
    protected final Context getContext() {
        return context;
    }
}
