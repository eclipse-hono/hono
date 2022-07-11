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
 * Enables or disables a test container based on particular types of protocol adapters are running.
 *
 * @see EnabledIfProtocolAdaptersAreRunning
 */
public class EnabledIfProtocolAdaptersAreRunningCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        return AnnotationUtils.findAnnotation(
                context.getElement(),
                EnabledIfProtocolAdaptersAreRunning.class)
            .map(this::checkConditions)
            .orElseGet(() -> ConditionEvaluationResult.enabled(null));
    }

    private ConditionEvaluationResult checkConditions(final EnabledIfProtocolAdaptersAreRunning annotation) {

        if (annotation.amqpAdapter() && isAdapterDisabled("amqp")) {
            return ConditionEvaluationResult.disabled("AMQP adapter is not running");
        }
        if (annotation.coapAdapter() && isAdapterDisabled("coap")) {
            return ConditionEvaluationResult.disabled("CoAP adapter is not running");
        }
        if (annotation.httpAdapter() && isAdapterDisabled("http")) {
            return ConditionEvaluationResult.disabled("HTTP adapter is not running");
        }
        if (annotation.loraAdapter() && isAdapterDisabled("lora")) {
            return ConditionEvaluationResult.disabled("Lora adapter is not running");
        }
        if (annotation.mqttAdapter() && isAdapterDisabled("mqtt")) {
            return ConditionEvaluationResult.disabled("MQTT adapter is not running");
        }
        return ConditionEvaluationResult.enabled(null);
    }

    private static boolean isAdapterDisabled(final String adapterName) {
        return Boolean.getBoolean("adapter.%s.disabled".formatted(adapterName));
    }
}
