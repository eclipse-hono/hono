/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tracing;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;

/**
 * Tests verifying behavior of {@link TracingHelper}.
 */
public class TracingHelperTest {

    /**
     * Verifies that a logging an error creates the appropriate log items.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testLogErrorWithMessage() {
        final Span span = mock(Span.class);
        final String errorMessage = "my error message";

        TracingHelper.logError(span, errorMessage);
        final ArgumentCaptor<Map> itemsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(span).log(itemsCaptor.capture());

        final Map capturedItemsMap = itemsCaptor.getValue();
        assertThat(capturedItemsMap, is(notNullValue()));
        assertThat(capturedItemsMap.size(), is(2));
        assertThat(capturedItemsMap.get(Fields.MESSAGE), is(errorMessage));
        assertThat(capturedItemsMap.get(Fields.EVENT), is(Tags.ERROR.getKey()));
    }

    /**
     * Verifies that a logging an error with a single item creates the appropriate log items.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testLogErrorWithSingletonMap() {
        final Span span = mock(Span.class);
        final String errorMessage = "my error message";

        TracingHelper.logError(span, Collections.singletonMap(Fields.MESSAGE, errorMessage));
        final ArgumentCaptor<Map> itemsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(span).log(itemsCaptor.capture());

        final Map capturedItemsMap = itemsCaptor.getValue();
        assertThat(capturedItemsMap, is(notNullValue()));
        assertThat(capturedItemsMap.size(), is(2));
        assertThat(capturedItemsMap.get(Fields.MESSAGE), is(errorMessage));
        assertThat(capturedItemsMap.get(Fields.EVENT), is(Tags.ERROR.getKey()));
    }

    /**
     * Verifies that a logging an error with given exception creates the appropriate log items.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testLogErrorWithException() {
        final Span span = mock(Span.class);
        final Exception exception = new Exception("my error message");

        TracingHelper.logError(span, exception);
        final ArgumentCaptor<Map> itemsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(span).log(itemsCaptor.capture());

        final Map capturedItemsMap = itemsCaptor.getValue();
        assertThat(capturedItemsMap, is(notNullValue()));
        assertThat(capturedItemsMap.size(), is(2));
        assertThat(capturedItemsMap.get(Fields.ERROR_OBJECT), is(exception));
        assertThat(capturedItemsMap.get(Fields.EVENT), is(Tags.ERROR.getKey()));
    }
}
