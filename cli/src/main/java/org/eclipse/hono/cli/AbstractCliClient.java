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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Abstract base class for the Hono CLI module.
 */
public abstract class AbstractCliClient {

    protected Vertx vertx;
    protected Context ctx;
    protected List<String> activeProfiles;

    @Autowired
    public final void setActiveProfiles(final Environment env) {
        activeProfiles = Arrays.asList(env.getActiveProfiles());
    }

    /**
     * Sets the vertx instance.
     * 
     * @param vertx The vertx instance.
     */
    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = vertx;
        this.ctx = vertx.getOrCreateContext();
    }

}
