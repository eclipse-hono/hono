/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import java.util.Objects;

import org.eclipse.hono.client.MessageSender;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for uploading telemtry data to a Hono server.
 */
public final class TelemetrySenderImpl extends AbstractSender {

    private static final String TELEMETRY_ENDPOINT_NAME  = "telemetry";

    private TelemetrySenderImpl(final ProtonSender sender, final String tenantId, final String targetAddress,
            final Context context, final Handler<String> closeHook) {
        super(sender, tenantId, targetAddress, context, closeHook);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for uploading data to Hono's telemetry endpoint.
     * 
     * @param tenantId The tenant to upload data for.
     * @param deviceId The device to upload data for. If {@code null}, the target address can be used
     *                 to upload data for arbitrary devices belonging to the tenant.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress(final String tenantId, final String deviceId) {
        StringBuilder targetAddress = new StringBuilder(TELEMETRY_ENDPOINT_NAME).append("/").append(Objects.requireNonNull(tenantId));
        if (deviceId != null && deviceId.length() > 0) {
            targetAddress.append("/").append(deviceId);
        }
        return targetAddress.toString();
    }

    @Override
    public String getEndpoint() {
        return TELEMETRY_ENDPOINT_NAME;
    }

    @Override
    protected String getTo(final String deviceId) {
        return getTargetAddress(tenantId, deviceId);
    }

    /**
     * Creates a new sender for publishing telemetry data to a Hono server.
     * 
     * @param context The vertx context to run all interactions with the server on.
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the telemetry data will be uploaded for.
     * @param deviceId The device that the telemetry data will be uploaded for or {@code null}
     *                 if the data to be uploaded will be produced by arbitrary devices of the
     *                 tenant.
     * @param closeHook The handler to invoke when the Hono server closes the sender. The sender's
     *                  target address is provided as an argument to the handler.
     * @param creationHandler The handler to invoke with the result of the creation attempt.
     * @throws NullPointerException if any of context, connection, tenant or handler is {@code null}.
     */
    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final String deviceId,
            final Handler<String> closeHook,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(creationHandler);

        final String targetAddress = getTargetAddress(tenantId, deviceId);
        createSender(context, con, targetAddress, ProtonQoS.AT_MOST_ONCE, closeHook).setHandler(created -> {
            if (created.succeeded()) {
                creationHandler.handle(Future.succeededFuture(
                        new TelemetrySenderImpl(created.result(), tenantId, targetAddress, context, closeHook)));
            } else {
                creationHandler.handle(Future.failedFuture(created.cause()));
            }
        });
    }
}
