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
     * Creates a property bag object from the given topic by retrieving 
     * all the properties from the <em>property-bag</em>.
     *
     * @param topic The topic that the message has been published to.
     * @return The property bag object or {@code null} if no 
     *         <em>property-bag</em> is set in the topic.
     * @throws NullPointerException if topic is {@code null}.
     */
    public static PropertyBag fromTopic(final String topic) {

        Objects.requireNonNull(topic);

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
        return null;
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
