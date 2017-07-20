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

package org.eclipse.hono.deviceregistry;

import org.eclipse.hono.service.registration.BaseDeviceRegistryServer;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


/**
 * Spring component that serves as the default implementation of Hono's device registry.
 */
@Component
@Scope("prototype")
public final class SimpleDeviceRegistryServer extends BaseDeviceRegistryServer<DeviceRegistryConfigProperties> {

}
