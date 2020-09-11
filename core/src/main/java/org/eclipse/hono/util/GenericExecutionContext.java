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
package org.eclipse.hono.util;

import org.eclipse.hono.tracing.SpanContextHolder;

import io.opentracing.SpanContext;

/**
 * A generic execution context that stores properties in a {@code Map}.
 *
 */
public class GenericExecutionContext extends MapBasedExecutionContext {

    /**
     * Creates a new execution context.
     */
    public GenericExecutionContext() {
        super(new SpanContextHolder(null));
    }

    /**
     * Creates a new execution context.
     *
     * @param spanContext The <em>OpenTracing</em> context to use for tracking the processing of this context.
     */
    public GenericExecutionContext(final SpanContext spanContext) {
        super(new SpanContextHolder(spanContext));
    }
}
