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

package org.eclipse.hono.application.client;

import io.vertx.core.Future;

/**
 * A client that consumes messages from Hono's northbound APIs.
 *
 * @param <T> The type of messages consumed by this client.
 */
public interface MessageConsumer<T extends Message<? extends MessageContext>> {

    /**
     * Closes the client.
     *
     * @return A future indicating the outcome of the operation.
     */
    Future<Void> close();

}
