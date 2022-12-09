/*
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
 */

package org.eclipse.hono.util;

/**
 * The particular type of messaging infrastructure being used.
 */
public enum MessagingType {

    /**
     * AMQP 1.0 based messaging infrastructure.
     */
    amqp,
    /**
     * Apache Kafka based messaging infrastructure.
     */
    kafka,
    /**
     * Pub/Sub based messaging infrastructure.
     */
    pubsub

}
