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

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for the Hono CLI module.
 */
public abstract class AbstractCliClient{
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
     * The client factory with methods to use.
     */
    protected ApplicationClientFactory clientFactory;

//    This will be used with the #1765.
//    protected AmqpAdapterClientFactory adapterFactory;

}

