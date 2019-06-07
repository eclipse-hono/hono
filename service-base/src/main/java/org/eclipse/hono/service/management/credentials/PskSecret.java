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

import static org.eclipse.hono.util.CredentialsConstants.FIELD_SECRETS_KEY;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * Secret Information.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PskSecret extends CommonSecret {

    @JsonProperty(FIELD_SECRETS_KEY)
    private byte[] key;

    public byte[] getKey() {
        return this.key;
    }

    public void setKey(final byte[] key) {
        this.key = key;
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("key", this.key);
    }

    @Override
    public void checkValidity() {
        super.checkValidity();
        if (this.key == null || this.key.length <= 0) {
            throw new IllegalStateException(String.format("'%s' must be set", FIELD_SECRETS_KEY));
        }
    }

}
