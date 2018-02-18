/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.example;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.HonoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;

/**
 * A base class providing support for connecting to a Hono server.
 *
 */
abstract class AbstractExampleClient {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());
    protected final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 1000;

    protected Context ctx;
    @Value(value = "${tenant.id}")
    protected String tenantId;
    protected Vertx vertx;
    protected HonoClient client;
    protected List<String> activeProfiles;

    /**
     * Empty default constructor.
     */
    protected AbstractExampleClient() {
    }

    @Autowired
    public final void setActiveProfiles(final Environment env) {
        activeProfiles = Arrays.asList(env.getActiveProfiles());
    }

    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = vertx;
        this.ctx = vertx.getOrCreateContext();
    }

    @Autowired
    public final void setHonoClient(final HonoClient client) {
        this.client = Objects.requireNonNull(client);
    }

    protected final ProtonClientOptions getClientOptions() {
        return new ProtonClientOptions().setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MILLIS).setReconnectAttempts(2);
    }
}
