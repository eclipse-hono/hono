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

package org.eclipse.hono.service.base.jdbc.store.device;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.service.management.credentials.CommonCredential;

import com.google.common.base.MoreObjects;

/**
 * A read result for credentials information.
 */
public class CredentialsReadResult {

    private final String deviceId;
    private final List<CommonCredential> credentials;
    private final Optional<String> resourceVersion;

    /**
     * Create a new instance.
     * @param deviceId The device ID.
     * @param credentials The credentials.
     * @param resourceVersion The optional resource version.
     */
    public CredentialsReadResult(final String deviceId, final List<CommonCredential> credentials, final Optional<String> resourceVersion) {
        this.deviceId = Objects.requireNonNull(deviceId);
        this.credentials = Objects.requireNonNull(credentials);
        this.resourceVersion = Objects.requireNonNull(resourceVersion);
    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public List<CommonCredential> getCredentials() {
        return this.credentials;
    }

    public Optional<String> getResourceVersion() {
        return this.resourceVersion;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("deviceId", this.deviceId)
                .add("resourceVersion", this.resourceVersion)
                .add("credentials", this.credentials)
                .toString();
    }
}
