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

    private static final int DEFAULT_TASK_EXECUTOR_QUEUE_SIZE = 1024;
    private static final Duration DEFAULT_CREDENTIALS_TTL = Duration.ofMinutes(1);
    private static final Duration DEFAULT_REGISTRATION_TTL = Duration.ofMinutes(1);
    private static final int DEFAULT_MAX_BCRYPT_COSTFACTOR = 10;

    private int taskExecutorQueueSize = DEFAULT_TASK_EXECUTOR_QUEUE_SIZE;

    private Duration credentialsTtl = DEFAULT_CREDENTIALS_TTL;
    private Duration registrationTtl = DEFAULT_REGISTRATION_TTL;

    private int maxBcryptCostfactor = DEFAULT_MAX_BCRYPT_COSTFACTOR;

    private final Set<String> hashAlgorithmsAllowList = new HashSet<>();

    public final int getTaskExecutorQueueSize() {
        return this.taskExecutorQueueSize;
    }

    public final void setTaskExecutorQueueSize(final int taskExecutorQueueSize) {
        this.taskExecutorQueueSize = taskExecutorQueueSize;
    }

    public final Duration getCredentialsTtl() {
        return this.credentialsTtl;
    }

    /**
     * Set TTL for credential responses, defaults to {@link #DEFAULT_CREDENTIALS_TTL}.
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

    public final Duration getRegistrationTtl() {
        return this.registrationTtl;
    }

    /**
     * Set TTL for registration responses, defaults to {@link #DEFAULT_REGISTRATION_TTL}.
     *
     * @param registrationTtl The TTL.
     * @throws IllegalArgumentException if the TTL value is less than one second.
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
