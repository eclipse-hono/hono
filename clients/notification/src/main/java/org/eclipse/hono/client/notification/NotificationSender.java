/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.notification;

import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingClient;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A client for publishing Hono internal notifications.
 */
public interface NotificationSender extends MessagingClient, Lifecycle {

    /**
     * Publish a notification that inform consuming components about an event in the publishing component.
     *
     * @param notification The notification to be published.
     * @param context The currently active OpenTracing span (may be {@code null}). An implementation should use this as
     *            the parent for any span it creates for tracing the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the notification has been published.
     *         <p>
     *         The future will be failed with a {@code org.eclipse.hono.client.ServerErrorException} if the data could
     *         not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if notification is {@code null}.
     */
    Future<Void> publish(Notification notification, SpanContext context);
}
