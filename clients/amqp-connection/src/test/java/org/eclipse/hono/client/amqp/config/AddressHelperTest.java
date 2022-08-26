/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.amqp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests address rewrite rules.
 */
public class AddressHelperTest {

    final String address = "telemetry/DEFAULT";

    /**
     * Tests address rewrite rules.
     */
    @Test
    public void testAddressRewrite() {

        final ClientConfigProperties config = createConfig("([a-z_]+)/([\\w-]+) test-vhost/$1/$2");
        assertEquals("test-vhost/" + address, AddressHelper.rewrite(address, config));


        final String namespaceAddress = "telemetry/iotns.iot";
        final ClientConfigProperties namespaceConfig = createConfig("([a-z_]+)/([\\w-]+).([\\w-]+) $2/$1/$2.$3");
        assertEquals("iotns/" + namespaceAddress, AddressHelper.rewrite(namespaceAddress, namespaceConfig));

        final String eventAddress = "event/iotns.iot";
        final ClientConfigProperties altConfig = createConfig(("[a-z_]+/([\\w-]+).[\\w-]+ $1/$0"));
        assertEquals("iotns/" + eventAddress, AddressHelper.rewrite(eventAddress, altConfig));

    }

    /**
     * Tests that original address is returned if rewrite rule is not using a valid syntax.
     */
    @Test
    public void testAddressRewriteWithNonValidSyntaxValue() {
        assertEquals(address, AddressHelper.rewrite(address, createConfig("TEST")));
    }

    /**
     * Tests that original address is returned if rewrite rule pattern doesn't match it.
     */
    @Test
    public void testAddressRewriteForNonMatchingPattern() {
        assertEquals(address, AddressHelper.rewrite(address, createConfig("something $0")));
    }

    /**
     * Tests that original address is returned if rewrite rule is not set.
     */
    @Test
    public void testAddressRewriteForNonSetValues() {
        assertEquals(address, AddressHelper.rewrite(address, createConfig(null)));
        assertEquals(address, AddressHelper.rewrite(address, createConfig("")));
    }

    /**
     * Creates a client configuration with specified rewrite rule.
     *
     * @param addressRewriteRule The rewrite rule.
     * @return The client configuration.
     */
    protected ClientConfigProperties createConfig(final String addressRewriteRule) {
        final ClientConfigProperties config = new ClientConfigProperties();
        config.setAddressRewriteRule(addressRewriteRule);
        return config;
    }
}
