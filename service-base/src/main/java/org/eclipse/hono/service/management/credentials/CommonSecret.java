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

import static org.eclipse.hono.util.CredentialsConstants.FIELD_SECRETS_NOT_AFTER;
import static org.eclipse.hono.util.CredentialsConstants.FIELD_SECRETS_NOT_BEFORE;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * Secret Information.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public abstract class CommonSecret {

    @JsonProperty
    private Boolean enabled;

    private OffsetDateTime notBefore;
    private OffsetDateTime notAfter;
    @JsonProperty
    private String comment;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the earliest instant in time that this secret may be used for authenticating a device.
     * 
     * @return The instant as an ISO string.
     * @see DateTimeFormatter#ISO_OFFSET_DATE_TIME
     */
    @JsonGetter(FIELD_SECRETS_NOT_BEFORE)
    public String getNotBeforeAsString() {
        return Optional.ofNullable(notBefore)
                .map(nb -> {
                    return nb.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                })
                .orElse(null);
    }

    /**
     * Gets the earliest instant in time that this secret may be used for authenticating a device.
     * 
     * @return The instant.
     */
    @JsonIgnore
    public Instant getNotBefore() {
        return Optional.ofNullable(notBefore)
                .map(nb -> nb.toInstant())
                .orElse(null);
    }

    /**
     * Sets the earliest instant in time that this secret may be used for authenticating a device.
     * 
     * @param notBefore The instant as an ISO string.
     * @see DateTimeFormatter#ISO_OFFSET_DATE_TIME
     */
    @JsonSetter(FIELD_SECRETS_NOT_BEFORE)
    public void setNotBefore(final String notBefore) {
        try {
            this.notBefore = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(notBefore, OffsetDateTime::from);
        } catch (DateTimeParseException e) {
            this.notBefore = null;
        }
    }

    /**
     * Sets the earliest instant in time that this secret may be used for authenticating a device.
     * 
     * @param notBefore The instant.
     */
    @JsonIgnore
    public void setNotBefore(final Instant notBefore) {
        if (notBefore == null) {
            this.notBefore = null;
        } else {
            this.notBefore = OffsetDateTime.ofInstant(notBefore, ZoneId.systemDefault());;
        }
    }

    /**
     * Gets the latest instant in time that this secret may be used for authenticating a device.
     * 
     * @return The instant as an ISO string.
     * @see DateTimeFormatter#ISO_OFFSET_DATE_TIME
     */
    @JsonGetter(FIELD_SECRETS_NOT_AFTER)
    public String getNotAfterAsString() {
        return Optional.ofNullable(notAfter)
                .map(na -> {
                    return na.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                })
                .orElse(null);
    }

    /**
     * Gets the latest instant in time that this secret may be used for authenticating a device.
     * 
     * @return The instant.
     */
    @JsonIgnore
    public Instant getNotAfter() {
        return Optional.ofNullable(notAfter)
                .map(na -> na.toInstant())
                .orElse(null);
    }

    /**
     * Sets the latest instant in time that this secret may be used for authenticating a device.
     * 
     * @param notAfter The instant as an ISO string.
     * @see DateTimeFormatter#ISO_OFFSET_DATE_TIME
     */
    @JsonSetter(FIELD_SECRETS_NOT_AFTER)
    public void setNotAfter(final String notAfter) {
        try {
            this.notAfter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(notAfter, OffsetDateTime::from);
        } catch (DateTimeParseException e) {
            this.notAfter = null;
        }
    }

    /**
     * Sets the latest instant in time that this secret may be used for authenticating a device.
     * 
     * @param notAfter The instant.
     */
    @JsonIgnore
    public void setNotAfter(final Instant notAfter) {
        if (notAfter == null) {
            this.notAfter = null;
        } else {
        this.notAfter = OffsetDateTime.ofInstant(notAfter, ZoneId.systemDefault());
        }
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
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
