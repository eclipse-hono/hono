/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.util;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A helper class for aggregating asynchronous invocation results.
 *
 */
public class AggregatingInvocationResultHandler implements Handler<Boolean> {

    private static final Logger LOG = LoggerFactory.getLogger(AggregatingInvocationResultHandler.class);
    private final AtomicInteger successfulResponses = new AtomicInteger();
    private final AtomicInteger unsuccessfulResponses = new AtomicInteger();
    private final int expectedNoOfResults;
    private final Handler<AsyncResult<Void>> overallResultHandler;

    public AggregatingInvocationResultHandler(
            final int expectedNoOfResults,
            final Handler<AsyncResult<Void>> overallResultHandler) {

        this.expectedNoOfResults = expectedNoOfResults;
        this.overallResultHandler = Objects.requireNonNull(overallResultHandler);
    }

    @Override
    public void handle(final Boolean succeeded) {
        if (succeeded) {
            successfulResponses.incrementAndGet();
        } else {
            unsuccessfulResponses.incrementAndGet();
        }
        if (successfulResponses.get() + unsuccessfulResponses.get() == expectedNoOfResults) {
            if (unsuccessfulResponses.get() > 0) {
                overallResultHandler.handle(Future.failedFuture(
                        String.format("%d invocations have been unsuccessful", unsuccessfulResponses.get())));
            } else {
                LOG.debug("all invocations have succeeded");
                overallResultHandler.handle(Future.succeededFuture());
            }
        }
    }
}