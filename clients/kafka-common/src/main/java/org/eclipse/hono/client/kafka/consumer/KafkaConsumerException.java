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

package org.eclipse.hono.client.kafka.consumer;

/**
 * An exception indicating that consuming data from Kafka failed.
 */
public class KafkaConsumerException extends RuntimeException {

    private static final long serialVersionUID = 9110074538472592799L;

    /**
     * Creates a new exception for a root cause.
     *
     * @param cause The root cause.
     */
    public KafkaConsumerException(final Throwable cause) {
        super(cause);
    }

}
