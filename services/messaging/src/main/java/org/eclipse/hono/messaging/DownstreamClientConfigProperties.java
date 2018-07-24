/*******************************************************************************
 * Copyright (c) 2016, 2017 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.messaging;

import org.eclipse.hono.config.ClientConfigProperties;


/**
 * Properties for configuring the connection to a downstream container.
 *
 */
public class DownstreamClientConfigProperties extends ClientConfigProperties {
    // this class is empty by intention
    // its only purpose is to have a specific subclass of ClientConfigProperies
    // which make autowiring of components in a Spring Boot application easier
}
