/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Abstract base class for the class containing the sub commands execution.
 */
public abstract class AbstractCommand {
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
     * To signal the CLI main class of the ended execution.
     */
    protected CountDownLatch latch;

}

