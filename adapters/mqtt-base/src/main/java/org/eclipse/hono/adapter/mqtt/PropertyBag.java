/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.MultiMap;

/**
 * A collection of methods for processing a <em>property-bag</em> set at the end of a topic.
 *
 */
public final class PropertyBag {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyBag.class);

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
     * <p>
     * Note: The given topic must have a non-empty first path segment, otherwise
     * {@code null} will be returned.
     *
     * @param topic The topic that the message has been published to.
     * @return The property bag (which may have no properties) or {@code null} if the
     *         topic path is empty or has an empty first segment or the property bag
     *         part is invalid.
     * @throws NullPointerException if topic is {@code null}.
     */
    public static PropertyBag fromTopic(final String topic) {
        Objects.requireNonNull(topic);

        if (topic.isEmpty() || topic.startsWith("/")) {
            LOG.info("The provided topic '{}' is invalid, reason: empty or starts with a '/'", topic);
            return null;
        }

        final PropertyBag propertyBag;
        final int index = topic.lastIndexOf("/?");
        if (index == 0) {
            propertyBag = null; // empty topic path
        } else if (index > 0) {
            MultiMap properties = null;
            try {
                properties = new QueryStringDecoder(topic.substring(index))
                        .parameters()
                        .entrySet()
                        .stream()
                        .collect(MultiMap::caseInsensitiveMultiMap,
                                (multiMap, entry) -> multiMap.add(entry.getKey(), entry.getValue()),
                                MultiMap::addAll);
            } catch (final IllegalArgumentException e) {
                LOG.debug("invalid property bag", e);
            }
            propertyBag = properties != null
                    ? new PropertyBag(ResourceIdentifier.fromString(topic.substring(0, index)), properties)
                    : null;
        } else {
            // topic does not contain property bag part
            propertyBag = new PropertyBag(ResourceIdentifier.fromString(topic), MultiMap.caseInsensitiveMultiMap());
        }
        return propertyBag;
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
