/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.config;

import io.vertx.core.VertxOptions;

/**
 * Vertx properties.
 */
public class VertxProperties {

    private boolean preferNative = VertxOptions.DEFAULT_PREFER_NATIVE_TRANSPORT;

    /**
     * Prefer to use native networking or not.
     * <p>
     * Also see {@link VertxOptions#setPreferNativeTransport(boolean)}.
     * </p>
     * 
     * @param preferNative {@code true} to prefer native networking, {@code false} otherwise.
     */
    public void setPreferNative(final boolean preferNative) {
        this.preferNative = preferNative;
    }

    /**
     * Configure the Vertx options according to our settings.
     * 
     * @param options The options to configure.
     */
    public void configureVertx(final VertxOptions options) {

        options.setPreferNativeTransport(this.preferNative);

    }

}
