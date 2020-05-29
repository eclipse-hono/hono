/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import org.eclipse.hono.cli.shell.InputReader;
import org.eclipse.hono.cli.shell.ShellHelper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Abstract base class for the Hono CLI module.
 * <p>
 * Contains connection Vert.x instance and client properties autowired as spring beans.
 * The profile and the corresponding factory methods will be valued at time of connection (when the user execute the command).
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
     * The class containing all client properties.
     */
    protected ClientConfigProperties honoClientConfig;
    /**
     * To connection created.
     */
    protected HonoConnection con;
    /**
     * To stop the executions of internal commands.
     */
    protected CountDownLatch connLatch;
    /**
     * Spring Shell inputReader.
     */
    @Autowired
    protected @Lazy InputReader inputReader;
    /**
     * Spring Shell output helper.
     * <p>
     * it can be passed to the method class instead to use the log (it's more stylish).
     */
    @Autowired
    protected ShellHelper shellHelper;
    /**
     * Sets the default client configuration.
     *
     * @param honoClientConfig The Client Configuration class instance.
     */
    @Autowired
    private void setClientConfigProperties(final ClientConfigProperties honoClientConfig) {
        this.honoClientConfig = Objects.requireNonNull(honoClientConfig);
    }
    /**
     * Sets the vert.x instance.
     *
     * @param vertx The vert.x instance.
     * @throws NullPointerException if vert.x is {@code null}.
     */
    @Autowired
    protected void setVertx(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
        this.ctx = vertx.getOrCreateContext();
    }
    /**
     * Handler for the Hono connection establishment.
     *
     * @param startup The async result of the connection.
     */
    protected void handleConnectionStatus(final AsyncResult<HonoConnection> startup) {
        if (startup.succeeded()) {
            con = startup.result();
            shellHelper.printInfo("Connection created!");
        } else {
            shellHelper.printError("Error occurred during initialization of receiver: " + startup.cause().getMessage());
            if (connLatch != null) {
                connLatch.countDown();
            }
        }
    }

}

