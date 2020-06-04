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

package org.eclipse.hono.service.management.device;

import java.time.Instant;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A status used in Device value object.
 */
public final class Status {

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    @HonoTimestamp
    private final Instant creationTime;

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS_LAST_UPDATE)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    @HonoTimestamp
    private Instant lastUpdate;

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS_LAST_USER)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private String lastUser;

    /**
     * Create a new Status initialised at the creation time.
     */
    public Status() {

        this.creationTime = Instant.now();
        this.lastUpdate = creationTime;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }


    /**
     * Updates the lastUpdate and lastUser fields with the current time and user.
     *
     * @param lastUser : the user Id to update with.
     * @return A reference to this for fluent use.
     */
    public Status update(final String lastUser) {
        this.lastUpdate = Instant.now();
        this.lastUser = lastUser;

        return this;
    }

    public String getLastUser() {
        return lastUser;
    }

}
