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

/**
 * A message of Hono's north bound APIs, exchanged between the messaging system and the back end application.
 *
 * @param <T> The type of context that the message is being received in.
 */
public interface Message<T extends MessageContext> {

    /**
     * Gets the message context which is specific for the messaging system in use.
     *
     * @return The context.
     */
    T getMessageContext();
}
