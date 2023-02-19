/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.californium.elements.auth.AdditionalInfo;
import org.eclipse.californium.scandium.auth.ApplicationLevelInfoSupplier;
import org.eclipse.hono.service.auth.DeviceUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Device information supplier.
 *
 * Supports {@link AdditionalInfo} as custom argument for handshaker callback.
 */
public class DeviceInfoSupplier implements ApplicationLevelInfoSupplier {

    /**
     * Key in the extended info of the peer identity containing the auth-id used for authentication.
     */
    public static final String EXT_INFO_KEY_HONO_AUTH_ID = "hono-auth-id";
    /**
     * Key in the extended info of the peer identity containing the authenticated Device.
     */
    public static final String EXT_INFO_KEY_HONO_DEVICE = "hono-device";

    private static final Logger LOG = LoggerFactory.getLogger(DeviceInfoSupplier.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public AdditionalInfo getInfo(final Principal clientIdentity, final Object customArgument) {
        if (customArgument instanceof AdditionalInfo) {
            final AdditionalInfo info = (AdditionalInfo) customArgument;
            final String authId = info.get(EXT_INFO_KEY_HONO_AUTH_ID, String.class);
            final var device = info.get(EXT_INFO_KEY_HONO_DEVICE, DeviceUser.class);
            LOG.debug("get additional info auth-id: {}, device: {}@{}", authId, device.getDeviceId(),
                    device.getTenantId());
            return info;
        }
        LOG.debug("get no additional info");
        return AdditionalInfo.empty();
    }

    /**
     * Create additional device information.
     *
     * @param device device
     * @param authId auth-id
     * @return additional device information.
     * @see DeviceInfoSupplier#EXT_INFO_KEY_HONO_DEVICE
     * @see DeviceInfoSupplier#EXT_INFO_KEY_HONO_AUTH_ID
     */
    public static AdditionalInfo createDeviceInfo(final DeviceUser device, final String authId) {
        final Map<String, Object> result = new HashMap<>();
        result.put(EXT_INFO_KEY_HONO_DEVICE, device);
        result.put(EXT_INFO_KEY_HONO_AUTH_ID, authId);
        return AdditionalInfo.from(result);
    }
}
