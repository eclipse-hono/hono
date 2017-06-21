/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

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
