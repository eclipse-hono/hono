/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.base.tenant;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

public class TenantHandle {

    private final String name;
    private final String id;

    protected TenantHandle(final String name, final String id) {
        this.name = name;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    protected ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("name", this.name)
                .add("id", this.id);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    public static TenantHandle of(final String name, final String id) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(id);
        return new TenantHandle(name, id);
    }

}
