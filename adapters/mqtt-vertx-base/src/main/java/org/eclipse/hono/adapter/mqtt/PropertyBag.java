/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.mqtt;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.util.ResourceIdentifier;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.MultiMap;

/**
 * A collection of methods for processing <em>property-bag</em> set at the end of a topic.
 *
 */
public final class PropertyBag {

    private final MultiMap properties;
    private final ResourceIdentifier topicWithoutPropertyBag;

    private PropertyBag(final ResourceIdentifier topicWithoutPropertyBag, final MultiMap properties) {
        this.topicWithoutPropertyBag = topicWithoutPropertyBag;
        this.properties = properties;
    }

    /**
     * Creates a property bag object from a topic.
     * <p>
     * The properties are retrieved from the topic by means of parsing
     * the topic after the last occurrence of <em>/?</em> as an HTTP query
     * string.
     *
     * @param topic The topic that the message has been published to.
     * @return The property bag (which may be empty) or {@code null} if the
     *         topic has zero length.
     * @throws NullPointerException if topic is {@code null}.
     */
    public static PropertyBag fromTopic(final String topic) {

        Objects.requireNonNull(topic);

        if (topic.isEmpty()) {
            return null;
        }

        final int index = topic.lastIndexOf("/?");
        if (index > 0) {
            final MultiMap properties = new QueryStringDecoder(topic.substring(index))
                    .parameters()
                    .entrySet()
                    .stream()
                    .collect(MultiMap::caseInsensitiveMultiMap,
                            (multiMap, entry) -> multiMap.add(entry.getKey(), entry.getValue()),
                            MultiMap::addAll);

            return new PropertyBag(ResourceIdentifier.fromString(topic.substring(0, index)), properties);
        }
        // topic does not contain property bag
        return new PropertyBag(ResourceIdentifier.fromString(topic), MultiMap.caseInsensitiveMultiMap());
    }

    /**
     * Gets a property value from the <em>property-bag</em>.
     * <p>
     * The case sensitivity of the property names are ignored while fetching a property value.
     *
     * @param name The property name.
     * @return The property value or {@code null} if the property is not set.
     */
    public String getProperty(final String name) {
        return properties.get(name);
    }

    /**
     * Gets an iterator iterating over the properties.
     *
     * @return The properties iterator.
     */
    public Iterator<Map.Entry<String, String>> getPropertiesIterator() {
        return properties.iterator();
    }

    /**
     * Returns the topic without the <em>property-bag</em>.
     *
     * @return The topic without the <em>property-bag</em>.
     */
    public ResourceIdentifier topicWithoutPropertyBag() {
        return topicWithoutPropertyBag;
    }
}
