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
import io.vertx.core.metrics.MetricsOptions;

/**
 * Vertx properties.
 */
public class VertxProperties {

    private boolean preferNative = VertxOptions.DEFAULT_PREFER_NATIVE_TRANSPORT;

    /**
     * Prefer to use native networking, or not.
     * <p>
     * Also see {@link VertxOptions#setPreferNativeTransport(boolean)}.
     * </p>
     * <p>
     * The default is to not prefer native networking.
     * </p>
     * 
     * @param preferNative {@code true} to prefer native networking, {@code false} otherwise.
     */
    public void setPreferNative(final boolean preferNative) {
        this.preferNative = preferNative;
    }

    private boolean enableMetrics = MetricsOptions.DEFAULT_METRICS_ENABLED;

    /**
     * Enable the vert.x metrics system, or not.
     * 
     * <p>
     * This decides if the vert.x metrics system will be enabled. Hono uses the Spring Boot integration with Micrometer,
     * so enabling the vert.x metrics will only enable the contribution of vert.x internal metrics to this system.
     * </p>
     * <p>
     * The default is to not enable vert.x metrics.
     * </p>
     * 
     * @param enableMetrics {@code true} to enable the metrics system, {@code false} otherwise.
     */
    public void setEnableMetrics(final boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }

    /**
     * Configure the Vertx options according to our settings.
     * 
     * @param options The options to configure.
     */
    public void configureVertx(final VertxOptions options) {

        options.setPreferNativeTransport(this.preferNative);

        if (this.enableMetrics) {
            options.setMetricsOptions(new MetricsOptions().setEnabled(true));
        }

    }

}
