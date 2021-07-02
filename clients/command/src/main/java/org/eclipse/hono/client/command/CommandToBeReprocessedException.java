/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.command;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServerErrorException;

/**
 * A {@link ServerErrorException} indicating that command processing has been cancelled and that the exact
 * same command message is expected to be processed by another consumer at a later point in time.
 */
public class CommandToBeReprocessedException extends ServerErrorException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new CommandToBeReprocessedException.
     * <p>
     * The exception will have a <em>503: Service Unavailable</em> status code.
     */
    public CommandToBeReprocessedException() {
        super(HttpURLConnection.HTTP_UNAVAILABLE);
    }
}
