/**
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
 */

import org.eclipse.hono.adapter.mqtt.PropertyBag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals("30", propertyBag.getProperty("param1"));
        assertEquals("value2", propertyBag.getProperty("param2"));
    }

    /**
     * Verifies that the property bag object is null when no <em>property-bag</em> is set.
     */
    @Test
    public void VerifyWhenNoPropertyBagIsSet() {
        assertNull(PropertyBag.fromTopic("event/tenant/device"));
    }

    /**
     * Verifies that no <em>property-bag</em> is set in the given topic with special characters.
     */
    @Test
    public void verifyTopicWithSpecialCharacters() {
        assertNull(PropertyBag.fromTopic("event/tenant/device/segment1?param=value/segment2/"));
    }

    /**
     * Verifies that the <em>property-bag</em> is trimmed from a topic string and the rest is returned.
     */
    @Test
    public void verifyTopicWithoutPropertyBag() {
        assertEquals("event/tenant/device",
                PropertyBag.fromTopic("event/tenant/device/?hono-ttl=30")
                        .topicWithoutPropertyBag());
    }
}
