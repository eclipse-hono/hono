/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.registry.infinispan;

import java.io.Serializable;
import java.util.Objects;
import org.infinispan.protostream.annotations.ProtoDoc;
import org.infinispan.protostream.annotations.ProtoField;

/**
 * A custom class to be used as key in the backend key-value storage.
 * This uses the uniques values of a registration to create a unique key to store the registration details.
 *
 *  See {@link CacheRegistrationService CacheRegistrationService} class.
 */
@ProtoDoc("@Indexed")
public class RegistrationKey implements Serializable {

    private String tenantId;
    private String deviceId;

    /**
     * Constructor without arguments for the protobuilder.
     */
    public RegistrationKey() {
    }

    /**
     * Creates a new RegistrationKey. Used by CacheRegistrationService.
     *
     * @param tenantId the id of the tenant owning the registration key.
     * @param deviceId the id of the device being registered.
     */
    public RegistrationKey(final String tenantId, final String deviceId) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o){
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RegistrationKey that = (RegistrationKey) o;
        return Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, deviceId);
    }

    @ProtoDoc("@Field")
    @ProtoField(number = 1, required = true)
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(final String tenantId) {
        this.tenantId = tenantId;
    }

    @ProtoDoc("@Field")
    @ProtoField(number = 2, required = true)
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }
}
