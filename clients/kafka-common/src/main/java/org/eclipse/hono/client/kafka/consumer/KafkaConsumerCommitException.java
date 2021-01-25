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

import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * An exception indicating that a Kafka consumer failed to commit offsets.
 * <p>
 * Possible root causes for a commit to fail are documented in {@link KafkaConsumer#commitSync()}.
 */
public class KafkaConsumerCommitException extends KafkaConsumerException {

    /**
     * Creates a new exception for a root cause.
     *
     * @param cause The root cause.
     */
    public KafkaConsumerCommitException(final Throwable cause) {
        super(cause);
    }

}
