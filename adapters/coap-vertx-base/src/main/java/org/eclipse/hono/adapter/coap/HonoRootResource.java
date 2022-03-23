/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.coap;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;

import io.vertx.core.Context;


/**
 * A root resource for Hono's CoAP adapter.
 * <p>
 * The resource always returns a 4.04 code.
 */
final class HonoRootResource extends CoapResource {

    private final Executor executor;

    /**
     * Creates a new root resource for a CoAP server.
     *
     * @param context The vert.x context that the resource should handle exchanges on.
     */
    HonoRootResource(final Supplier<Context> context) {
        super("");
        this.executor = new Executor() {
            @Override
            public void execute(final Runnable command) {
                Optional.ofNullable(context.get())
                    .ifPresentOrElse(ctx -> ctx.runOnContext(s -> command.run()), command);
            }
        };
    }

    @Override
    public Executor getExecutor() {
        return executor;
    }

    @Override
    public void handleRequest(final Exchange exchange) {
        exchange.sendResponse(new Response(ResponseCode.NOT_FOUND));
    }
}
