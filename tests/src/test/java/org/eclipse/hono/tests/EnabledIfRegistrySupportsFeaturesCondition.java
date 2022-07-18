/**
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
 */


package org.eclipse.hono.tests;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;


/**
 * Enables or disables a test container based on features that the device registry supports.
 *
 * @see EnabledIfRegistrySupportsFeatures
 */
public class EnabledIfRegistrySupportsFeaturesCondition implements ExecutionCondition {

    private static final String DEVICEREGISTRY_TYPE_MONGODB = "mongodb";
    private static final String HONO_DEVICEREGISTRY_TYPE = System.getProperty(
            "deviceregistry.type",
            DEVICEREGISTRY_TYPE_MONGODB);

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        return AnnotationUtils.findAnnotation(
                context.getElement(),
                EnabledIfRegistrySupportsFeatures.class)
            .map(this::checkConditions)
            .orElseGet(() -> ConditionEvaluationResult.enabled(null));
    }

    private ConditionEvaluationResult checkConditions(final EnabledIfRegistrySupportsFeatures annotation) {

        if (annotation.searchDevices() && !isSearchDevicesSupportedByRegistry()) {
            return ConditionEvaluationResult.disabled("device registry does not support 'search devices' operation");
        }
        if (annotation.searchTenants() && !isSearchTenantsSupportedByRegistry()) {
            return ConditionEvaluationResult.disabled("device registry does not support 'search tenants' operation");
        }
        if (annotation.tenantAlias() && !isTenantAliasSupported()) {
            return ConditionEvaluationResult.disabled("device registry does not support tenant aliases");
        }
        if (annotation.trustAnchorGroups() && !isTrustAnchorGroupsSupported()) {
            return ConditionEvaluationResult.disabled("device registry does not support trust anchor groups");
        }
        return ConditionEvaluationResult.enabled(null);
    }

    /**
     * Checks if the device registry being used supports searching for devices using
     * arbitrary criteria.
     *
     * @return {@code true} if the registry supports searching.
     */
    private static boolean isSearchDevicesSupportedByRegistry() {
        return HONO_DEVICEREGISTRY_TYPE.equals(DEVICEREGISTRY_TYPE_MONGODB);
    }

    /**
     * Checks if the device registry being used supports searching for tenants using
     * arbitrary criteria.
     *
     * @return {@code true} if the registry supports searching.
     */
    private static boolean isSearchTenantsSupportedByRegistry() {
        return HONO_DEVICEREGISTRY_TYPE.equals(DEVICEREGISTRY_TYPE_MONGODB);
    }

    /**
     * Checks if the Device Registry supports sharing of CAs across trust anchor groups.
     *
     * @return {@code true} if the registry supports sharing CAs.
     */
    private static boolean isTrustAnchorGroupsSupported() {
        return HONO_DEVICEREGISTRY_TYPE.equals(DEVICEREGISTRY_TYPE_MONGODB);
    }

    /**
     * Checks if the Device Registry supports specifying and using a tenant alias.
     *
     * @return {@code true} if the registry supports tenant aliases.
     */
    private static boolean isTenantAliasSupported() {
        return HONO_DEVICEREGISTRY_TYPE.equals(DEVICEREGISTRY_TYPE_MONGODB);
    }
}
