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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * Secret Information.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public abstract class CommonSecret {

    @JsonProperty
    private Boolean enabled;

    @JsonProperty(FIELD_SECRETS_NOT_BEFORE)
    private Instant notBefore;
    @JsonProperty(FIELD_SECRETS_NOT_AFTER)
    private Instant notAfter;
    @JsonProperty
    private String comment;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public void setNotBefore(final Instant notBefore) {
        this.notBefore = notBefore;
    }

    public Instant getNotAfter() {
        return notAfter;
    }

    public void setNotAfter(final Instant notAfter) {
        this.notAfter = notAfter;
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
