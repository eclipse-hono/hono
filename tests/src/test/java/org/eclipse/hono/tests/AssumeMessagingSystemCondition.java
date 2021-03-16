/*******************************************************************************
 * Copyright (c) 2021, 2021 Contributors to the Eclipse Foundation
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

import java.util.Optional;

import org.eclipse.hono.util.MessagingType;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

/**
 * An {@link ExecutionCondition} for the {@link AssumeMessagingSystem} annotation which derives the condition for
 * executing the annotated test from the system properties by leveraging the {@link IntegrationTestSupport} class.
 */
public class AssumeMessagingSystemCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        final Optional<AssumeMessagingSystem> annotation = AnnotationUtils.findAnnotation(context.getElement(), AssumeMessagingSystem.class);
        if (annotation.isPresent()) {
            final MessagingType assumedMessagingType = annotation.get().type();

            final MessagingType messagingType = IntegrationTestSupport.getConfiguredMessagingType();
            if (messagingType != assumedMessagingType) {
                return ConditionEvaluationResult.disabled(String.format("Test assumes to run on %s, but current test profile is %s. Skipping test!",
                        assumedMessagingType,
                        messagingType));
            } else {
                return ConditionEvaluationResult.enabled(String.format("Test assumes to run on %s, which matches current test profile is %s. Running test!",
                        assumedMessagingType,
                        messagingType));
            }
        }
        return ConditionEvaluationResult.enabled("Test makes no assumptions of messaging systems. Running test.");
    }

}
