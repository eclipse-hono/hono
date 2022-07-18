/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

/**
 * An {@link ExecutionCondition} for the {@link EnabledIfMessagingSystemConfigured} annotation which derives the condition
 * for executing the annotated test based on the {@link IntegrationTestSupport#getConfiguredMessagingType()} value.
 *
 * @see EnabledIfMessagingSystemConfigured
 */
public class EnabledIfMessagingSystemConfiguredCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        return AnnotationUtils.findAnnotation(context.getElement(), EnabledIfMessagingSystemConfigured.class)
                .map(EnabledIfMessagingSystemConfigured::type)
                .map(annotationMessagingType -> {
                    if (annotationMessagingType != IntegrationTestSupport.getConfiguredMessagingType()) {
                        return ConditionEvaluationResult.disabled(String.format("%s messaging system not configured",
                                annotationMessagingType));
                    }
                    return ConditionEvaluationResult.enabled(null);
                })
                .orElseGet(() -> ConditionEvaluationResult.enabled(null));
    }

}
