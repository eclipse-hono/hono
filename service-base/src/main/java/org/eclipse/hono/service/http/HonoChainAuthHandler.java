/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.http;

import org.eclipse.hono.client.ServiceInvocationException;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.ChainAuthHandlerImpl;


/**
 * A Hono specific version of vert.x web's standard {@code ChainAuthHandlerImpl}
 * that extracts and handles a {@link ServiceInvocationException} conveyed as the
 * root cause in an {@code HttpStatusException} when an authentication failure
 * occurs.
 *
 */
public class HonoChainAuthHandler extends ChainAuthHandlerImpl {

    /**
     * {@inheritDoc}
     * 
     * Delegates error handling to {@link AuthHandlerTools#processException(RoutingContext, Throwable, String)}
     * in order to handle server errors properly.
     */
    @Override
    protected void processException(final RoutingContext ctx, final Throwable exception) {

        if (!ctx.response().ended()) {
            AuthHandlerTools.processException(ctx, exception, authenticateHeader(ctx));
        }
    }
}
