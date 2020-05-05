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
package org.eclipse.hono.service.management.credentials;

import java.time.Instant;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * This class encapsulates common secrets shared across all credential types used in Hono.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public abstract class CommonSecret {

    @JsonProperty
    private Boolean enabled;

    @JsonProperty
    private String id;

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE)
    @HonoTimestamp
    private Instant notBefore;
    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER)
    @HonoTimestamp
    private Instant notAfter;
    @JsonProperty
    private String comment;

    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Sets the enabled property to indicate to protocol adapters whether to use this secret during authentication.
     * 
     * @param enabled  Whether this secret type should be used to authenticate devices.
     * @return         a reference to this for fluent use.
     */
    public CommonSecret setEnabled(final Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public String getId() {
        return id;
    }

    /**
     * Sets the ID of the secret. This id may be assigned by the device registry.
     * The id must be unique within the credentials set containing it.
     *
     * @param id The string to set as the id.
     * @return   a reference to this for fluent use.
     */
    public CommonSecret setId(final String id) {
        this.id = id;
        return this;
    }

    /**
     * Gets the earliest instant in time that this secret may be used for authenticating a device.
     *
     * @return The instant.
     */
    public Instant getNotBefore() {
        return notBefore;
    }

    /**
     * Gets the latest instant in time that this secret may be used for authenticating a device.
     * 
     * @return The end date/time in which this secret is valid.
     */
    public Instant getNotAfter() {
        return notAfter;
    }

    /**
     * Sets the earliest instant in time that this secret may be used for authenticating a device.
     *
     * @param notBefore The start date/time in which this secret is valid.
     * @return          a reference to this for fluent use.
     */
    public CommonSecret setNotBefore(final Instant notBefore) {
        this.notBefore = notBefore;
        return this;
    }

    /**
     * Sets the latest instant in time that this secret may be used for authenticating a device.
     *
     * @param notAfter The end date/time in which this secret is valid.
     * @return         a reference to this for fluent use.
     */
    public CommonSecret setNotAfter(final Instant notAfter) {
        this.notAfter = notAfter;
        return this;
    }

    public String getComment() {
        return comment;
    }

    /**
     * Sets the human readable comment describing this secret.
     *
     * @param comment The human readable description for this secret.
     * @return        a reference to this for fluent use.
     */
    public CommonSecret setComment(final String comment) {
        this.comment = comment;
        return this;
    }

    /**
     * Creator of {@link ToStringHelper}.
     *
     * @return A new instance, never returns {@code null}.
     */
    protected ToStringHelper toStringHelper() {
        return MoreObjects
                .toStringHelper(this)
                .add("enabled", this.enabled)
                .add("notBefore", this.notBefore)
                .add("notAfter", this.notAfter)
                .add("comment", this.comment);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    /**
     * Check if the secret is valid.
     *
     * @throws IllegalStateException if the secret is not valid.
     */
    public void checkValidity() {
        if (this.notBefore != null && this.notAfter != null) {
            if (this.notBefore.isAfter(this.notAfter)) {
                throw new IllegalStateException("'not-before' must be before 'not-after'");
            }
        }
    }
}
