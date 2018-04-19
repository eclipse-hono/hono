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
     * Sets the specific object instance to use for configuring this <em>Verticle</em>.
     * 
     * @param props The properties.
     */
    protected final void setSpecificConfig(final T props) {
        this.config = props;
    }

    /**
     * Sets the properties to use for configuring this <em>Verticle</em>.
     * <p>
     * Subclasses <em>must</em> invoke {@link #setSpecificConfig(Object)} with the configuration
     * object.
     * <p>
     * This method mainly exists so that subclasses can annotate its concrete implementation
     * with Spring annotations like @c{@code Autowired} and/or {@code Qualifier} to get injected
     * a particular bean instance.
     * 
     * @param configuration The configuration properties.
     * @throws NullPointerException if configuration is {@code null}.
     */
    public abstract void setConfig(T configuration);

    /**
     * Gets the properties that this <em>Verticle</em> has been configured with.
     * 
     * @return The properties or {@code null} if not set.
     */
    public final T getConfig() {
        return this.config;
    }
}
