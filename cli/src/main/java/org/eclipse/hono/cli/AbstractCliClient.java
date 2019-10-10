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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Abstract base class for the Hono CLI module.
 */
public abstract class AbstractCliClient {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The vert.x instance to run on.
     */
    protected Vertx vertx;
    /**
     * The vert.x context to run on.
     */
    protected Context ctx;
    /**
     * The Spring Boot profiles that are active.
     */
    protected List<String> activeProfiles;

    /**
     * Sets the Spring environment.
     * 
     * @param env The environment.
     * @throws NullPointerException if environment is {@code null}.
     */
    @Autowired
    public final void setActiveProfiles(final Environment env) {
        Objects.requireNonNull(env);
        activeProfiles = Arrays.asList(env.getActiveProfiles());
    }

    /**
     * Sets the vert.x instance.
     * 
     * @param vertx The vert.x instance.
     * @throws NullPointerException if vert.x is {@code null}.
     */
    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
        this.ctx = vertx.getOrCreateContext();
    }

}
