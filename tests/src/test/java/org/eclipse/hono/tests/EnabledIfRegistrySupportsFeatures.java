/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Configures a test to run only if the device registry supports a certain set of features.
 */
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfRegistrySupportsFeaturesCondition.class)
public @interface EnabledIfRegistrySupportsFeatures {

    /**
     * Checks if the registry must support tenant aliases.
     *
     * @return {@code true} if the test container must only be executed if the registry supports the feature.
     */
    boolean tenantAlias() default false;

    /**
     * Checks if the registry must support trust anchor groups.
     *
     * @return {@code true} if the test container must only be executed if the registry supports the feature.
     */
    boolean trustAnchorGroups() default false;

    /**
     * Checks if the registry must support the search tenants operation.
     *
     * @return {@code true} if the test container must only be executed if the registry supports the feature.
     */
    boolean searchTenants() default false;

    /**
     * Checks if the registry must support the search devices operation.
     *
     * @return {@code true} if the test container must only be executed if the registry supports the feature.
     */
    boolean searchDevices() default false;
}
