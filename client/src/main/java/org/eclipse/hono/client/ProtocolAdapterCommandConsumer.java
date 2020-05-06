/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * Represents the device specific command consumer used in protocol adapters.
 *
 */
public interface ProtocolAdapterCommandConsumer {

    /**
     * Closes the consumer.
     *
     * @param spanContext The span context (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     */
    Future<Void> close(SpanContext spanContext);

}
