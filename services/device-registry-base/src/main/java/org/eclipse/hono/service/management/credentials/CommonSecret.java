/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Strings;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * This class encapsulates common secrets shared across all credential types used in Hono.
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public abstract class CommonSecret {

    @JsonProperty(RegistryManagementConstants.FIELD_ENABLED)
    private Boolean enabled;

    @JsonProperty(RegistryManagementConstants.FIELD_ID)
    private String id;

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE)
    @HonoTimestamp
    private Instant notBefore;
    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER)
    @HonoTimestamp
    private Instant notAfter;
    @JsonProperty(RegistryManagementConstants.FIELD_COMMENT)
    private String comment;

    @JsonIgnore
    public final boolean isEnabled() {
        return Optional.ofNullable(enabled).orElse(true);
    }

    /**
     * Sets the enabled property to indicate to protocol adapters whether to use this secret during authentication.
     *
     * @param enabled  Whether this secret type should be used to authenticate devices.
     * @return         a reference to this for fluent use.
     */
    @JsonIgnore
    public final CommonSecret setEnabled(final Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Gets the identifier of the secret.
     * <p>
     * This id may be assigned by the device registry.
     * The id must be unique within the credentials set containing it.
     *
     * @return The identifier or {@code null} if not assigned.
     */
    public final String getId() {
        return id;
    }

    /**
     * Sets the identifier of the secret.
     * <p>
     * This id may be assigned by the device registry.
     * The id must be unique within the credentials set containing it.
     *
     * @param id The identifier.
     * @return   a reference to this for fluent use.
     */
    public final CommonSecret setId(final String id) {
        this.id = id;
        return this;
    }

    /**
     * Gets the earliest instant in time that this secret may be used for authenticating a device.
     *
     * @return The instant.
     */
    public final Instant getNotBefore() {
        return notBefore;
    }

    /**
     * Gets the latest instant in time that this secret may be used for authenticating a device.
     *
     * @return The end date/time in which this secret is valid.
     */
    public final Instant getNotAfter() {
        return notAfter;
    }

    /**
     * Sets the earliest instant in time that this secret may be used for authenticating a device.
     *
     * @param notBefore The start date/time in which this secret is valid.
     * @return          a reference to this for fluent use.
     */
    public final CommonSecret setNotBefore(final Instant notBefore) {
        this.notBefore = notBefore;
        return this;
    }

    /**
     * Sets the latest instant in time that this secret may be used for authenticating a device.
     *
     * @param notAfter The end date/time in which this secret is valid.
     * @return         a reference to this for fluent use.
     */
    public final CommonSecret setNotAfter(final Instant notAfter) {
        this.notAfter = notAfter;
        return this;
    }

    public final String getComment() {
        return comment;
    }

    /**
     * Sets the human readable comment describing this secret.
     *
     * @param comment The human readable description for this secret.
     * @return        a reference to this for fluent use.
     */
    public final CommonSecret setComment(final String comment) {
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
     * Checks if this secret is valid.
     * <p>
     * Verifies that the notBefore instant is not after the
     * notAfter instant, if both values are not {@code null},
     * and then invokes {@link #checkValidityOfSpecificProperties()}.
     *
     * @throws IllegalStateException if the secret is not valid.
     */
    public final void checkValidity() {
        if (this.notBefore != null && this.notAfter != null) {
            if (this.notBefore.isAfter(this.notAfter)) {
                throw new IllegalStateException("'not-before' must not be after 'not-after'");
            }
        }
        checkValidityOfSpecificProperties();
    }

    /**
     * Checks if this secret's non-common properties are valid.
     * <p>
     * Subclasses should override this method in order to verify that
     * the values of properties other than notBefore and notAfter are valid.
     *
     * @throws IllegalStateException if any of the secret's properties are not valid.
     */
    protected void checkValidityOfSpecificProperties() {
        // empty default implementation
    }

    /**
     * Merges another secret's properties into this one's.
     *
     * @param otherSecret The secret to be merged.
     * @throws NullPointerException if the given secret is {@code null}.
     * @throws IllegalStateException if this secret's id property is {@code null} or empty.
     * @throws IllegalArgumentException if the other secret cannot be merged into this one.
     */
    void merge(final CommonSecret otherSecret) {

        Objects.requireNonNull(otherSecret);

        // we only merge if this secret has an ID and thus is being used for
        // updating the other secret which is supposed to have the same ID and type
        // in this case

        if (Strings.isNullOrEmpty(this.getId())) {

            throw new IllegalStateException("this secret has no ID and therefore cannot be used to update another secret");

        } else if (!this.getClass().equals(otherSecret.getClass())) {

            throw new IllegalArgumentException("other secret must be a " + this.getClass() +
                    " but is a " + otherSecret.getClass());

        } else if (!this.getId().equals(otherSecret.getId())) {

            throw new IllegalArgumentException("other secret must have same ID as this secret");

        } else {

            // merge other secret's properties into this one's
            mergeProperties(otherSecret);
        }
    }

    /**
     * Merges another secret's properties into this one's.
     *
     * @param otherSecret The secret to be merged into this one.
     */
    protected abstract void mergeProperties(CommonSecret otherSecret);
}
