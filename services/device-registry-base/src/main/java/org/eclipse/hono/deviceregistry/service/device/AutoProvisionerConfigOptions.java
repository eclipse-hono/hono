/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.service.device;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;

/**
 * Configuration properties for Hono's gateway-based auto-provisioning.
 */
@ConfigMapping(prefix = "hono.autoprovisioning", namingStrategy = NamingStrategy.VERBATIM)
public interface AutoProvisionerConfigOptions {

    /**
     * Gets the delay before trying to send the auto-provisioning notification if the initial attempt
     * to send the event hasn't completed yet.
     * <p>
     * This will only be invoked for requests that have <i>not</i> triggered the auto-provisioning,
     * but instead have found the {@link org.eclipse.hono.util.RegistrationConstants#FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT}
     * flag in the device data to be {@code false}. Assuming that such a request has occurred very shortly after
     * the auto-provisioning, with the notification event still in the process of getting sent, the intention
     * here is to wait some time until the event was most probably sent. After the delay, the flag is checked
     * again and only if the flag is still {@code false}, meaning there was possibly an error sending the event
     * during auto-provisioning, the event will be sent as part of that request.
     *
     * @return The delay in milliseconds.
     */
    @WithDefault("50")
    long retryEventSendingDelay();
}
