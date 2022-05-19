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

package org.eclipse.hono.commandrouter;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.util.Constants;

/**
 * Default AMQP server for Hono's Command Router service.
 */
public final class CommandRouterAmqpServer extends AmqpServiceBase<ServiceConfigProperties> {

    @Override
    protected String getServiceName() {
        return Constants.SERVICE_NAME_COMMAND_ROUTER;
    }
}
