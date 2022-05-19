/**
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
 */

package org.eclipse.hono.adapter.mqtt;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of {@link PropertyBag}.
 *
 */
public class PropertyBagTest {

    /**
     * Verifies the value of properties set in the <em>property-bag</em> of the message's topic.
     */
    @Test
    public void verifyPropertiesInPropertyBag() {
        final PropertyBag propertyBag = PropertyBag.fromTopic("event/tenant/device/?param1=30&param2=value2");
        assertThat(propertyBag.getProperty("param1")).isEqualTo("30");
        assertThat(propertyBag.getProperty("param2")).isEqualTo("value2");
    }

    /**
     * Verifies that the property bag object is null when no <em>property-bag</em> is set.
     */
    @Test
    public void verifyWhenNoPropertyBagIsSet() {
        final PropertyBag bag = PropertyBag.fromTopic("event/tenant/device");
        assertWithMessage("properties iterator").that(bag.getPropertiesIterator().hasNext()).isFalse();
        assertThat(bag.topicWithoutPropertyBag().getEndpoint()).isEqualTo("event");
        assertThat(bag.topicWithoutPropertyBag().getTenantId()).isEqualTo("tenant");
        assertThat(bag.topicWithoutPropertyBag().getResourceId()).isEqualTo("device");
    }

    /**
     * Verifies that no <em>property-bag</em> is set in the given topic with special characters.
     */
    @Test
    public void verifyTopicWithSpecialCharacters() {
        final PropertyBag bag = PropertyBag.fromTopic("event/tenant/device/segment1?param=value/segment2/");
        assertWithMessage("properties iterator").that(bag.getPropertiesIterator().hasNext()).isFalse();
        assertThat(bag.topicWithoutPropertyBag().getEndpoint()).isEqualTo("event");
        assertThat(bag.topicWithoutPropertyBag().getTenantId()).isEqualTo("tenant");
        assertThat(bag.topicWithoutPropertyBag().getResourceId()).isEqualTo("device");
        assertThat(bag.topicWithoutPropertyBag().getPathWithoutBase()).isEqualTo("device/segment1?param=value/segment2");
    }

    /**
     * Verifies that the <em>property-bag</em> is trimmed from a topic string and the rest is returned.
     */
    @Test
    public void verifyTopicWithoutPropertyBag() {
        final PropertyBag bag = PropertyBag.fromTopic("event/tenant/device/?hono-ttl=30");
        assertThat(bag.topicWithoutPropertyBag().toString()).isEqualTo("event/tenant/device");
    }

    /**
     * Verifies that <em>getPropertiesIterator</em> returns an iterator with the expected entries.
     */
    @Test
    public void testGetPropertiesIterator() {
        final PropertyBag propertyBag = PropertyBag.fromTopic("event/tenant/device/?param1=30&param2=value2");
        assertNotNull(propertyBag);
        final Iterator<Map.Entry<String, String>> propertiesIterator = propertyBag.getPropertiesIterator();
        final Map<String, String> tmpMap = new HashMap<>();
        propertiesIterator.forEachRemaining((entry) -> tmpMap.put(entry.getKey(), entry.getValue()));
        assertThat(tmpMap.size()).isEqualTo(2);
        assertThat(tmpMap.get("param1")).isEqualTo("30");
        assertThat(tmpMap.get("param2")).isEqualTo("value2");
    }

    /**
     * Verifies that the property names are treated as case insensitive by the <em>getProperty</em> method.
     */
    @Test
    public void testGetPropertyIsCaseInsensitive() {
        final PropertyBag propertyBag = PropertyBag.fromTopic("event/tenant/device/?cONteNT-TypE=text&pARam2=value2");

        assertThat(propertyBag.topicWithoutPropertyBag().toString()).isEqualTo("event/tenant/device");
        assertThat(propertyBag.getProperty("Content-Type")).isEqualTo("text");
        assertThat(propertyBag.getProperty("param2")).isEqualTo("value2");
    }

    /**
     * Verifies that <em>getPropertiesIterator</em> returns an empty iterator for a topic that doesn't contain
     * property bag entries.
     */
    @Test
    public void testGetPropertiesIteratorForTopicWithoutPropertyBag() {
        final PropertyBag propertyBag = PropertyBag.fromTopic("event/tenant/device/?");

        assertThat(propertyBag.topicWithoutPropertyBag().toString()).isEqualTo("event/tenant/device");
        assertThat(propertyBag.getPropertiesIterator().hasNext()).isFalse();
    }

    /**
     * Verifies that a PropertyBag cannot be created from a topic that is empty or only contains a property bag.
     */
    @Test
    public void testFromTopicWithEmptyTopicPath() {
        PropertyBag propertyBag = PropertyBag.fromTopic("/?");
        assertThat(propertyBag).isNull();

        propertyBag = PropertyBag.fromTopic("/?test=1");
        assertThat(propertyBag).isNull();
    }

    /**
     * Verifies that a PropertyBag cannot be created from a topic that contains an empty first segment.
     */
    @Test
    public void testFromTopicWithEmptyFirstTopicSegment() {
        PropertyBag propertyBag = PropertyBag.fromTopic("/test");
        assertThat(propertyBag).isNull();

        propertyBag = PropertyBag.fromTopic("/");
        assertThat(propertyBag).isNull();
    }

    /**
     * Verifies that a PropertyBag cannot be created from a topic that contains an invalid property bag string.
     */
    @Test
    public void testFromTopicWithInvalidPropertyBag() {
        final PropertyBag propertyBag = PropertyBag.fromTopic("test/?%%");

        assertThat(propertyBag).isNull();
    }
}
