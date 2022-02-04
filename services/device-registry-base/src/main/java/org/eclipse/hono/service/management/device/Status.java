/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A status base object of a model entity.
 *
 * @param <T> The concrete type of the status object for a model entity.
 */
@RegisterForReflection
public class Status<T extends Status<T>> {

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    @HonoTimestamp
    private Instant creationTime;

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS_LAST_UPDATE)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    @HonoTimestamp
    private Instant lastUpdate;

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS_LAST_USER)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private String lastUser;

    /**
     * Sets the creation time to the given value.
     *
     * @param creationTime The creation time.
     *
     * @return A reference to this, enabling fluent use.
     */
    @SuppressWarnings("unchecked")
    public final T setCreationTime(final Instant creationTime) {
        this.creationTime = creationTime;
        return (T) this;
    }

    public final Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Sets the time of the last update to the given value.
     *
     * @param lastUpdate The time of the last update.
     *
     * @return A reference to this, enabling fluent use.
     */
    @SuppressWarnings("unchecked")
    public final T setLastUpdate(final Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
        return (T) this;
    }

    public final Instant getLastUpdate() {
        return lastUpdate;
    }


    /**
     * Updates the lastUpdate and lastUser fields with the current time and user.
     *
     * @param lastUser : the user Id to update with.
     * @return A reference to this for fluent use.
     */
    @SuppressWarnings("unchecked")
    public final T update(final String lastUser) {
        this.lastUpdate = Instant.now();
        this.lastUser = lastUser;
        return (T) this;
    }

    public final String getLastUser() {
        return lastUser;
    }

}
