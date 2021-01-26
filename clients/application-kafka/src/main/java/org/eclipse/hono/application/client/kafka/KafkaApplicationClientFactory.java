/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.application.client.kafka;

import org.eclipse.hono.application.client.ApplicationClientFactory;

/**
 * A factory for creating clients for Hono's Kafka-based northbound APIs.
 */
public interface KafkaApplicationClientFactory extends ApplicationClientFactory<KafkaMessageContext> {

}
