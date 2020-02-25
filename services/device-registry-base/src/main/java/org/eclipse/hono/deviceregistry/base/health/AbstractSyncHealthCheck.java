/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.base.health;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.hono.service.HealthCheckProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

public abstract class AbstractSyncHealthCheck implements HealthCheckProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractSyncHealthCheck.class);

    private final Vertx vertx;
    private final String name;

    public AbstractSyncHealthCheck(final Vertx vertx, final String name) {
        this.vertx = vertx;
        this.name = name;
    }

    protected String getName() {
        return this.name;
    }

    /**
     * Convert a reason and exception to KO status.
     *
     * @param reason The reason. Must not be {@code null}.
     * @param e The exception. May be {@code null}.
     * @return The status. Never is {@code null}.
     */
    protected static Status KO(final String reason, final Throwable e) {

        final JsonObject info = new JsonObject()
                .put("reason", reason);

        if (e != null) {
            info.put("message", e.getMessage());
            final StringWriter sw = new StringWriter();
            try (final PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
            }
            info.put("exception", sw.toString());
        }

        log.debug("Check KO: {}", info);

        return Status.KO(info);

    }

    protected Status checkReadinessSync() {
        return Status.OK();
    }

    protected Status checkLivenessSync() {
        return Status.OK();
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        readinessHandler.register(getName(), future -> {
            this.vertx.executeBlocking(future2 -> {

                try {
                    future2.complete(checkReadinessSync());
                } catch (Exception e) {
                    future2.fail(e);
                }

            }, false, future);
        });
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        livenessHandler.register(getName(), future -> {
            this.vertx.executeBlocking(future2 -> {

                try {
                    future2.complete(checkLivenessSync());
                } catch (Exception e) {
                    future2.fail(e);
                }

            }, false, future);
        });
    }

}
