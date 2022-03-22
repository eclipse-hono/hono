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


package org.eclipse.hono.client.amqp.connection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.amqp.connection.HonoProtonHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link HonoProtonHelper}.
 *
 */
@ExtendWith(VertxExtension.class)
class HonoProtonHelperTest {

    /**
     * Verifies that code is scheduled to be executed on a given Context
     * other than the current Context.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testExecuteOnContextRunsOnGivenContext(final VertxTestContext ctx) {

        final Context mockContext = mock(Context.class);
        doAnswer(invocation -> {
            final Handler<Void> codeToRun = invocation.getArgument(0);
            codeToRun.handle(null);
            return null;
        }).when(mockContext).runOnContext(any(Handler.class));

        HonoProtonHelper.executeOnContext(mockContext, result -> result.complete("done"))
        .onComplete(ctx.succeeding(s -> {
            ctx.verify(() -> {
                verify(mockContext).runOnContext(any(Handler.class));
                assertThat(s).isEqualTo("done");
            });
            ctx.completeNow();
        }));
    }
}
