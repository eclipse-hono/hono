/**
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
 */

package org.eclipse.hono.example.protocolgateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of {@link PropertyBag}.
 *
 */
public class PropertyBagTest {

    private static final String TOPIC_WITHOUT_PROPERTY_BAG = "devices/4712/messages/events/";
    private static final String KEY1 = "a";
    private static final String VALUE1 = "b";
    private static final String ENCODED1 = "a=b";
    private static final String KEY2 = "foo bar";
    private static final String VALUE2 = "b/a/z";
    private static final String ENCODED2 = "foo%20bar=b%2Fa%2Fz";
    private static final String ENCODED_TOPIC = TOPIC_WITHOUT_PROPERTY_BAG + "?" + ENCODED1 + "&" + ENCODED2;

    /**
     * Verifies that properties are set in the <em>property-bag</em> of the message's topic.
     */
    @Test
    public void testDecodePropertyBag() {
        final PropertyBag propertyBag = PropertyBag.decode(ENCODED_TOPIC);
        assertThat((Object) propertyBag.topicWithoutPropertyBag()).isEqualTo(TOPIC_WITHOUT_PROPERTY_BAG);
        assertThat((Object) propertyBag.getProperty(KEY1)).isEqualTo(VALUE1);
        assertThat((Object) propertyBag.getProperty(KEY2)).isEqualTo(VALUE2);
    }

    /**
     * Verifies that the <em>property-bag</em> is trimmed from a topic string and the rest is returned.
     */
    @Test
    public void testDecodeTopicWithoutPropertyBag() {
        assertThat((Object) PropertyBag.decode(TOPIC_WITHOUT_PROPERTY_BAG).topicWithoutPropertyBag())
                .isEqualTo(TOPIC_WITHOUT_PROPERTY_BAG);
    }

    /**
     * Verifies that <em>getPropertiesIterator</em> returns an iterator with the expected entries.
     */
    @Test
    public void testGetPropertyBagIterator() {
        final PropertyBag propertyBag = PropertyBag.decode(ENCODED_TOPIC);
        assertThat(propertyBag).isNotNull();
        final Iterator<Map.Entry<String, String>> propertiesIterator = propertyBag.getPropertyBagIterator();
        final Map<String, String> tmpMap = new HashMap<>();
        propertiesIterator.forEachRemaining((entry) -> tmpMap.put(entry.getKey(), entry.getValue()));
        assertThat((Object) tmpMap.size()).isEqualTo(2);
        assertThat((Object) tmpMap.get(KEY1)).isEqualTo(VALUE1);
        assertThat((Object) tmpMap.get(KEY2)).isEqualTo(VALUE2);
    }

    /**
     * Verifies that properties get encoded into the topic.
     */
    @Test
    public void testEncode() {
        final String topicWithPropertyBag = PropertyBag.encode(TOPIC_WITHOUT_PROPERTY_BAG,
                Map.of(KEY1, VALUE1, KEY2, VALUE2));

        // the order of the properties might differ
        assertThat(topicWithPropertyBag).startsWith(TOPIC_WITHOUT_PROPERTY_BAG + "?");
        assertThat(topicWithPropertyBag).contains(ENCODED1);
        assertThat(topicWithPropertyBag).contains(ENCODED2);
        assertThat(topicWithPropertyBag).contains("&");
        assertThat(topicWithPropertyBag).hasSize(53);
    }

    /**
     * Verifies that the encoded topic can be decoded with the expected results.
     */
    @Test
    public void testDecodeEncodedTopic() {
        final String topicWithPropertyBag = PropertyBag.encode(TOPIC_WITHOUT_PROPERTY_BAG,
                Map.of(KEY1, VALUE1, KEY2, VALUE2));

        final PropertyBag propertyBag = PropertyBag.decode(topicWithPropertyBag);
        assertThat((Object) propertyBag.topicWithoutPropertyBag()).isEqualTo(TOPIC_WITHOUT_PROPERTY_BAG);
        assertThat((Object) propertyBag.getProperty(KEY2)).isEqualTo(VALUE2);
        assertThat((Object) propertyBag.getProperty(KEY1)).isEqualTo(VALUE1);
    }

}
