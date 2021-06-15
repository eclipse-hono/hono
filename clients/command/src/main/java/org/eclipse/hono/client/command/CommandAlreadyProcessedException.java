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
 * A {@link ServerErrorException} indicating that the exact same command message was already
 * handled before.
 */
public class CommandAlreadyProcessedException extends ServerErrorException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new CommandAlreadyProcessedException.
     * <p>
     * The exception will have a <em>500: Internal Server Error</em> status code.
     */
    public CommandAlreadyProcessedException() {
        super(HttpURLConnection.HTTP_INTERNAL_ERROR);
    }
}
