/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.example.protocolgateway.interfaces;

public interface CommandHandler {
    /**
     * Pass through function to handle commands and return response body
     *
     * @param commandPayload body of command
     * @param subject        subject of command
     * @param contentType    HTML content type
     * @param isOneWay       signals if response string necessary
     * @return
     */
    String handleCommand(String commandPayload, String subject, String contentType, boolean isOneWay);
}
