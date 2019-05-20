/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.jmeter.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

/**
 * Base class for implementing JMeter samplers connecting to Hono.
 *
 */
public abstract class AbstractClient {

    static final int    DEFAULT_ADDRESS_RESOLUTION_TIMEOUT_MILLIS = 2000;
    static final String TIME_STAMP_VARIABLE                       = "timeStamp";

    /**
     * The vert.x instance to use for connecting to Hono.
     */
    protected final Vertx vertx;

    /**
     * Creates a new client.
     */
    protected AbstractClient() {
        vertx = vertx();
    }

    final Vertx vertx() {
        final VertxOptions options = new VertxOptions()
                .setWarningExceptionTime(1500000000)
                .setAddressResolverOptions(new AddressResolverOptions()
                        .setCacheNegativeTimeToLive(0) // discard failed DNS lookup results immediately
                        .setCacheMaxTimeToLive(0) // support DNS based service resolution
                        .setRotateServers(true)
                        .setQueryTimeout(DEFAULT_ADDRESS_RESOLUTION_TIMEOUT_MILLIS));
        return Vertx.vertx(options);
    }

    /**
     * Closes the vert.x instance.
     * 
     * @return A future that will succeed once the instance is closed.
     */
    protected final Future<Void> closeVertx() {
        final Future<Void> result = Future.future();
        vertx.close(result);
        return result;
    }
}
