/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli;

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

/**
 * A base class providing support for connecting to Hono.
 *
 */
abstract class AbstractClient {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    protected Context ctx;
    @Value(value = "${tenant.id}")
    protected String tenantId;
    @Value(value = "${message.type}")
    protected String messageType;
    protected Vertx vertx;
    protected HonoClient client;
    protected List<String> activeProfiles;


    /**
     * Empty default constructor.
     */
    protected AbstractClient() {
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
}
