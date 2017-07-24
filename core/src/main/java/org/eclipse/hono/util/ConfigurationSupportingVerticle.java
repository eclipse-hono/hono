/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.util;

import java.util.Objects;

import org.eclipse.hono.config.AbstractConfig;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AbstractVerticle;


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
     * Sets the properties to use for configuring the sockets to listen on.
     * 
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Autowired(required = false)
    public final void setConfig(final T props) {
        this.config = Objects.requireNonNull(props);
    }

    /**
     * Gets the properties in use for configuring the sockets to listen on.
     * 
     * @return The properties or {@code null} if not set.
     */
    public final T getConfig() {
        return this.config;
    }
}
