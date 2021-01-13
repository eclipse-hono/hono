/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.kafka.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Utility methods for working with Kafka records.
 */
public class RecordUtil {

    protected static final Logger LOG = LoggerFactory.getLogger(RecordUtil.class);

    private RecordUtil() {
    }

    /**
     * Gets the String value of a specific <em>header</em>.
     *
     * @param headers The headers to retrieve the value from.
     * @param name The property name.
     * @return The value or {@code null} if the headers do not contain a value of the expected type for the given
     *         name or if there are multiple headers with the given name.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static String getStringHeaderValue(final Collection<KafkaHeader> headers, final String name) {
        return getHeaderValue(headers, name, String.class);
    }

    /**
     * Gets the value of a specific <em>header</em>.
     *
     * @param <T> The expected type of the header to retrieve the value of.
     * @param headers The headers to retrieve the value from.
     * @param name The property name.
     * @param type The expected value type.
     * @return The value or {@code null} if the headers do not contain a value of the expected type for the given
     *         name or if there are multiple headers with the given name.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getHeaderValue(final Collection<KafkaHeader> headers, final String name,
            final Class<T> type) {
        Objects.requireNonNull(headers);
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);

        T result = null;
        final Iterator<KafkaHeader> matchingHeaders = headers.stream()
                .filter(h -> name.equals(h.key()))
                .iterator();
        if (matchingHeaders.hasNext()) {
            final Buffer value = matchingHeaders.next().value();
            if (value != null && !matchingHeaders.hasNext()) {
                // value not null and no multiple matching headers found
                if (type.equals(String.class)) {
                    result = (T) value.toString();
                } else {
                    try {
                        result = Json.decodeValue(value, type);
                    } catch (final DecodeException e) {
                        LOG.trace("header value '{}' not of given type {}", value, type.getSimpleName(), e);
                    }
                }
            }
        }
        return result;
    }

}
