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

import org.eclipse.hono.service.amqp.AmqpAuthConnectedServiceBase;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


/**
 * Spring component that serves as the default implementation of Hono's device registry.
 * <p>
 * It implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a> and
 * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
 */
@Component
@Scope("prototype")
public final class SimpleDeviceRegistryServer extends AmqpAuthConnectedServiceBase<DeviceRegistryConfigProperties> {

    @Override
    protected String getServiceName() {
        return "Hono-DeviceRegistry";
    }
}
