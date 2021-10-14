/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.notification.deviceregistry;

import org.eclipse.hono.notification.deviceregistry.credentials.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.device.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.tenant.TenantChangeNotification;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;

/**
 * Type resolver for notifications.
 * <p>
 * This type resolver knows the types {@link TenantChangeNotification}, {@link DeviceChangeNotification} and
 * {@link CredentialsChangeNotification}.
 */
public class NotificationTypeResolver extends TypeIdResolverBase {

    private JavaType baseType;

    @Override
    public void init(final JavaType baseType) {
        this.baseType = baseType;
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.NAME;
    }

    @Override
    public String idFromValue(final Object obj) {
        return idFromValueAndType(obj, obj.getClass());
    }

    @Override
    public String idFromValueAndType(final Object obj, final Class<?> subType) {

        if (obj instanceof AbstractDeviceRegistryNotification) {
            return ((AbstractDeviceRegistryNotification) obj).getType();
        }
        return null;
    }

    @Override
    public JavaType typeFromId(final DatabindContext context, final String id) {
        switch (id) {
        case TenantChangeNotification.TYPE:
            return context.constructSpecializedType(this.baseType, TenantChangeNotification.class);
        case DeviceChangeNotification.TYPE:
            return context.constructSpecializedType(this.baseType, DeviceChangeNotification.class);
        case CredentialsChangeNotification.TYPE:
            return context.constructSpecializedType(this.baseType, CredentialsChangeNotification.class);
        default:
            return null;
        }
    }
}
