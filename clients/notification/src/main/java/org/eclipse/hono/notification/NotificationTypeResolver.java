/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.notification;

import java.util.List;

import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;

/**
 * Type resolver for notifications.
 * <p>
 * This type resolver knows the types {@link TenantChangeNotification}, {@link DeviceChangeNotification},
 * {@link CredentialsChangeNotification} and {@link AllDevicesOfTenantDeletedNotification}.
 */
public final class NotificationTypeResolver extends TypeIdResolverBase {

    private static final List<NotificationType<?>> SUPPORTED_TYPES = List.of(
            TenantChangeNotification.TYPE,
            DeviceChangeNotification.TYPE,
            CredentialsChangeNotification.TYPE,
            AllDevicesOfTenantDeletedNotification.TYPE);

    private JavaType baseType;

    /**
     * Creates a resolver.
     */
    public NotificationTypeResolver() {
        super();
    }

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

        if (obj instanceof AbstractNotification) {
            return ((AbstractNotification) obj).getType().getTypeName();
        }
        return null;
    }

    @Override
    public JavaType typeFromId(final DatabindContext context, final String id) {
        for (final NotificationType<?> type : SUPPORTED_TYPES) {
            if (type.getTypeName().equals(id)) {
                return context.constructSpecializedType(this.baseType, type.getClazz());
            }
        }
        return null;
    }
}
