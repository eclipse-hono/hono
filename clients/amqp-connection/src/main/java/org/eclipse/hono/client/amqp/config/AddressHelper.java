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

import java.util.Objects;

import org.eclipse.hono.util.Strings;

/**
 * Utility class for handling AMQP addresses.
 */
public class AddressHelper {

    private AddressHelper() {
    }

    /**
     * Gets the AMQP <em>target</em> address to use for creating with specific Hono endpoint.
     *
     * @param endpoint The endpoint for the address (e.g. telemetry).
     * @param tenantId The tenant of the address.
     * @param resourceId The device to upload data for. If {@code null}, the target address can be used
     *                 to upload data for arbitrary devices belonging to the tenant.
     * @param config The client configuration containing the rewrite rule to be applied to the address.
     *                           See {@link #rewrite(String, ClientConfigProperties)} for more information about syntax and behavior of this property.
     * @return The target address.
     * @throws NullPointerException if endpoint or tenant is {@code null}.
     */
    public static String getTargetAddress(final String endpoint, final String tenantId, final String resourceId, final ClientConfigProperties config) {
        final StringBuilder addressBuilder = new StringBuilder(Objects.requireNonNull(endpoint))
                .append("/").append(Objects.requireNonNull(tenantId));
        if (!Strings.isNullOrEmpty(resourceId)) {
            addressBuilder.append("/").append(resourceId);
        }

        return AddressHelper.rewrite(addressBuilder.toString(), config);
    }

    /**
     * Rewrites address for a certain endpoint according to the provided rule.
     *
     * @param address The address to be rewritten.
     * @param config The client configuration containing the rewrite rule.
     *               The rule is in the <em>$PATTERN $REPLACEMENT</em> format.
     *               Pattern and replacement use the regular Java pattern syntax.
     *               The pattern should match the original address.
     *               <p>
     *               Example:
     *               <em>([a-z_]+)/([\\w-]+) test-vhost/$1/$2</em>
     *               will rewrite
     *               <em>telemetry/DEFAULT_TENANT</em> to <em>test-vhost/telemetry/DEFAULT-TENANT</em>
     *               <p>
     *               If the configuration is {@code null} or the rule is {@code null}, empty, in the wrong format or the pattern doesn't match the address,
     *               the original address will be returned.
     * @return The address to be used.
     */
    public static String rewrite(final String address, final ClientConfigProperties config) {

        if (config != null &&
                config.getAddressRewritePattern() != null &&
                !Strings.isNullOrEmpty(config.getAddressRewriteReplacement())) {
            return config.getAddressRewritePattern().matcher(address).replaceAll(config.getAddressRewriteReplacement());
        }

        return address;
    }

}
