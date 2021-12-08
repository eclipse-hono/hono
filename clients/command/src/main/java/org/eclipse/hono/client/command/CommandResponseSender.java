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
package org.eclipse.hono.client.command;

import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingClient;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A client for publishing a device's response to a command received from a downstream application.
 */
public interface CommandResponseSender extends MessagingClient, Lifecycle {

    /**
     * Sends a device's response to a command.
     *
     * @param tenant The tenant that the device belongs to.
     * @param device The registration assertion for the device that the data originates from.
     * @param response The response.
     * @param context The currently active OpenTracing span or {@code null} if no
     *         span is currently active. An implementation should use this as the
     *         parent for any new span(s) it creates for tracing the execution of
     *         this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the response has been sent downstream.
     *         <p>
     *         The future will be failed with a {@code org.eclipse.hono.client.ServiceInvocationException}
     *         if the response could not be sent.
     * @throws NullPointerException if tenant, device or response are {@code null}.
     */
    Future<Void> sendCommandResponse(
            TenantObject tenant,
            RegistrationAssertion device,
            CommandResponse response,
            SpanContext context);
}
