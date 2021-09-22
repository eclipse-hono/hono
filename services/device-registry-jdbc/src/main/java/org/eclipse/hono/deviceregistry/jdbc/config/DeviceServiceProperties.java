/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.jdbc.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.hono.util.RegistryManagementConstants;

/**
 * Device service properties.
 */
public class DeviceServiceProperties {

    /**
     * The value indicating an <em>unlimited</em> number of devices to be allowed for a tenant.
     */
    public static final int UNLIMITED_DEVICES_PER_TENANT = -1;

    private static final Duration DEFAULT_CREDENTIALS_TTL = Duration.ofMinutes(1);
    private static final Duration DEFAULT_REGISTRATION_TTL = Duration.ofMinutes(1);
    private static final int DEFAULT_MAX_BCRYPT_COSTFACTOR = 10;

    private final Set<String> hashAlgorithmsAllowList = new HashSet<>();

    private Duration credentialsTtl = DEFAULT_CREDENTIALS_TTL;
    private Duration registrationTtl = DEFAULT_REGISTRATION_TTL;

    private int maxBcryptCostfactor = DEFAULT_MAX_BCRYPT_COSTFACTOR;

    private int maxDevicesPerTenant = UNLIMITED_DEVICES_PER_TENANT;

    /**
     * Checks if the number of devices per tenant is limited.
     *
     * @return {@code true} if the configured number of devices per tenant is &gt;
     *         {@value #UNLIMITED_DEVICES_PER_TENANT}.
     */
    public boolean isNumberOfDevicesPerTenantLimited() {
        return maxDevicesPerTenant > UNLIMITED_DEVICES_PER_TENANT;
    }

    /**
     * Gets the maximum number of devices that can be registered for each tenant.
     * <p>
     * The default value of this property is {@value #UNLIMITED_DEVICES_PER_TENANT}.
     *
     * @return The maximum number of devices.
     */
    public int getMaxDevicesPerTenant() {
        return maxDevicesPerTenant;
    }

    /**
     * Sets the maximum number of devices that can be registered for each tenant.
     * <p>
     * The default value of this property is {@value #UNLIMITED_DEVICES_PER_TENANT}.
     *
     * @param maxDevices The maximum number of devices.
     * @throws IllegalArgumentException if the number of devices is is set to less
     *                                  than {@value #UNLIMITED_DEVICES_PER_TENANT}.
     */
    public void setMaxDevicesPerTenant(final int maxDevices) {
        if (maxDevices < UNLIMITED_DEVICES_PER_TENANT) {
            throw new IllegalArgumentException(
                    String.format("Maximum devices must be set to value >= %s", UNLIMITED_DEVICES_PER_TENANT));
        }
        this.maxDevicesPerTenant = maxDevices;
    }

    /**
     * Gets the duration after which retrieved credentials information must be considered stale.
     * <p>
     * The default value of this property is one minute.
     *
     * @return The duration.
     */
    public final Duration getCredentialsTtl() {
        return this.credentialsTtl;
    }

    /**
     * Sets the duration after which retrieved credentials information must be considered stale.
     * <p>
     * The default value of this property is one minute.
     *
     * @param credentialsTtl The TTL.
     * @throws NullPointerException if ttl is {@code null}.
     * @throws IllegalArgumentException if the TTL value is less than one second.
     */
    public final void setCredentialsTtl(final Duration credentialsTtl) {
        Objects.requireNonNull(credentialsTtl);
        if (credentialsTtl.toSeconds() <= 0) {
            throw new IllegalArgumentException("'credentialsTtl' must be a positive duration of at least one second");
        }
        this.credentialsTtl = credentialsTtl;
    }

    /**
     * Gets the duration after which retrieved device registration information must be considered stale.
     * <p>
     * The default value of this property is one minute.
     *
     * @return The duration.
     */
    public final Duration getRegistrationTtl() {
        return this.registrationTtl;
    }

    /**
     * Sets the duration after which retrieved device registration information must be considered stale.
     * <p>
     * The default value of this property is one minute.
     *
     * @param registrationTtl The duration.
     * @throws IllegalArgumentException if the duration is less than one second.
     */

    public final void setRegistrationTtl(final Duration registrationTtl) {
        if (registrationTtl.toSeconds() <= 0) {
            throw new IllegalArgumentException("'registrationTtl' must be a positive duration of at least one second");
        }
        this.registrationTtl = registrationTtl;
    }

    /**
     * Gets the maximum number of iterations to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     *
     * @return The maximum number.
     */
    public int getMaxBcryptCostfactor() {
        return this.maxBcryptCostfactor;
    }

    /**
     * Sets the maximum number of iterations to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     *
     * @param costfactor The maximum number.
     * @throws IllegalArgumentException if iterations is &lt; 4 or &gt; 31.
     */
    public final void setMaxBcryptCostfactor(final int costfactor) {
        if (costfactor < 4 || costfactor > 31) {
            throw new IllegalArgumentException("iterations must be > 3 and < 32");
        } else {
            this.maxBcryptCostfactor = costfactor;
        }
    }


    /**
     * Gets the list of supported hashing algorithms for pre-hashed passwords.
     * <p>
     * The device registry will not accept credentials using a hashing
     * algorithm that is not contained in this list.
     * If the list is empty, the device registry will accept any hashing algorithm.
     * <p>
     * Default value is an empty list.
     *
     * @return The supported algorithms.
     */
    public final Set<String> getHashAlgorithmsAllowList() {
        return Collections.unmodifiableSet(this.hashAlgorithmsAllowList);
    }

    /**
     * Sets the list of supported hashing algorithms for pre-hashed passwords.
     * <p>
     * The device registry will not accept credentials using a hashing
     * algorithm that is not contained in this list.
     * If the list is empty, the device registry will accept any hashing algorithm.
     * <p>
     * Default value is an empty list.
     *
     * @param hashAlgorithmsAllowList The algorithms to support.
     * @throws NullPointerException if the list is {@code null}.
     */
    public final void setHashAlgorithmsAllowList(final String[] hashAlgorithmsAllowList) {
        Objects.requireNonNull(hashAlgorithmsAllowList);
        this.hashAlgorithmsAllowList.clear();
        this.hashAlgorithmsAllowList.addAll(Arrays.asList(hashAlgorithmsAllowList));
    }

    /**
     * Sets the regular expression that should be used to validate authentication identifiers (user names) of
     * hashed-password credentials.
     * <p>
     * After successful validation of the expression's syntax, the regex is set as the value
     * of system property {@value RegistryManagementConstants#SYSTEM_PROPERTY_USERNAME_REGEX}.
     *
     * @param regex The regular expression to use.
     * @throws NullPointerException if regex is {@code null}.
     * @throws java.util.regex.PatternSyntaxException if regex is not a valid regular expression.
     */
    public final void setUsernamePattern(final String regex) {
        Objects.requireNonNull(regex);
        // verify regex syntax
        Pattern.compile(regex);
        System.setProperty(RegistryManagementConstants.SYSTEM_PROPERTY_USERNAME_REGEX, regex);
    }
}
