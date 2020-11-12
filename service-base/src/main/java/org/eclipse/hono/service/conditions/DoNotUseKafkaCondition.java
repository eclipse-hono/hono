/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.conditions;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;

/**
 * Spring Boot condition that matches if the configuration property <em>hono.kafka.producerConfig.bootstrap.servers</em>
 * is not set. This indicates not to use Kafka as messaging.
 */
public class DoNotUseKafkaCondition extends NoneNestedConditions {

    DoNotUseKafkaCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(name = "hono.kafka.producerConfig.bootstrap.servers")
    static class DoUseKafkaCondition {
    }

}
