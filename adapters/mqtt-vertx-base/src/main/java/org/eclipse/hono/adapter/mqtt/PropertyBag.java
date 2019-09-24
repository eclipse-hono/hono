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

import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A collection of methods for processing <em>property-bag</em> set at the end of a topic.
 *
 */
public final class PropertyBag {

    private final Map<String, List<String>> properties;
    private final String topicWithoutPropertyBag;

    private PropertyBag(final String topicWithoutPropertyBag, final Map<String, List<String>> properties) {
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
            return new PropertyBag(
                    topic.substring(0, index),
                    new QueryStringDecoder(topic.substring(index)).parameters());
        }
        return null;
    }

    /**
     * Gets a property value from the <em>property-bag</em>.
     *
     * @param name The property name.
     * @return The property value or {@code null} if the property is not set.
     */
    public String getProperty(final String name) {
        return Optional.ofNullable(properties)
                .map(props -> props.get(name))
                .map(values -> values.get(0))
                .orElse(null);
    }    

    /**
     * Returns the topic without the <em>property-bag</em>.
     *
     * @return The topic without the <em>property-bag</em>.
     */
    public String topicWithoutPropertyBag() {
        return topicWithoutPropertyBag;
    }
}
