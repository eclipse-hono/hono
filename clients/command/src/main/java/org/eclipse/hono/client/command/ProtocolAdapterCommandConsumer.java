/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.command;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A Protocol Adapter consumer of commands for a specific device.
 *
 */
public interface ProtocolAdapterCommandConsumer {

    /**
     * Closes the consumer.
     *
     * @param sendEvent {@code true} if <em>disconnected notification</em> event should be sent.
     * @param spanContext The span context (may be {@code null}).
     * @return A future indicating the outcome of the operation. The future will be failed with a
     *         {@code org.eclipse.hono.client.ServiceInvocationException} if there was an error closing the consumer and
     *         with a {@code org.eclipse.hono.client.ClientErrorException} with
     *         {@link java.net.HttpURLConnection#HTTP_PRECON_FAILED} if the consumer has been closed/overwritten
     *         already.
     */
    Future<Void> close(boolean sendEvent, SpanContext spanContext);
}
