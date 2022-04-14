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

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@code HonoRootResource}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class HonoRootResourceTest {

    @Test
    void testGetExecutorRunsTasksOnVertxEventLoop(final Vertx vertx, final VertxTestContext ctx) {
        final Context context = vertx.getOrCreateContext();
        final HonoRootResource rootResource = new HonoRootResource(() -> context);
        rootResource.getExecutor().execute(() -> {
            ctx.verify(() -> assertThat(Vertx.currentContext()).isEqualTo(context));
            ctx.completeNow();
        });
    }

}
