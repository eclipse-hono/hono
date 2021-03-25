/*
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
 */

package org.eclipse.hono.client.kafka.tracing;

import io.opentracing.Span;
import io.opentracing.tag.AbstractTag;

/**
 * An OpenTracing tag type for {@code Long} values.
 */
public class LongTag extends AbstractTag<Long> {

    /**
     * Creates an instance for the given key.
     *
     * @param tagKey The tag key.
     */
    public LongTag(final String tagKey) {
        super(tagKey);
    }

    @Override
    public void set(final Span span, final Long tagValue) {
        span.setTag(super.key, tagValue);
    }
}
