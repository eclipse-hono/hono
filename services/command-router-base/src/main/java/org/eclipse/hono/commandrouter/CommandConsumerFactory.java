/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Lifecycle;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A factory for creating consumers of command &amp; control messages.
 */
public interface CommandConsumerFactory extends Lifecycle {

    /**
     * Initializes the CommandConsumerFactory with the given commandTargetMapper.
     * <p>
     * This method must be invoked before the {@link #start()} method.
     *
     * @param commandTargetMapper The component for mapping an incoming command to the gateway (if applicable) and
     *            protocol adapter instance that can handle it. Note that no initialization of this factory will be done
     *            here, that is supposed to be done by the calling method.
     * @throws NullPointerException if commandTargetMapper is {@code null}.
     */
    void initialize(CommandTargetMapper commandTargetMapper);

    /**
     * Creates a command consumer to receive commands for the given tenant.
     * <p>
     * Note that {@link #initialize(CommandTargetMapper)} has to have been called already, otherwise a failed future
     * is returned.
     *
     * @param tenantId The tenant to consume commands for.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *                An implementation should use this as the parent for any span it creates for tracing
     *                the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} with an error code indicating
     *         the cause of the failure.
     * @throws NullPointerException if tenantId is {@code null}.
     */
    Future<Void> createCommandConsumer(String tenantId, SpanContext context);

}
