/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.notification;

import java.util.Objects;

/**
 * The type of an {@link AbstractNotification}.
 *
 * @param <T> The {@link AbstractNotification} class.
 */
public final class NotificationType<T extends AbstractNotification> {

    private final String typeName;
    private final Class<T> clazz;
    private final String address;

    /**
     * Creates a new NotificationType.
     *
     * @param typeName The name of this type.
     * @param clazz The class of notifications having this type.
     * @param address The (significant part of the) address used when sending notifications of this type.
     * @throws NullPointerException If any of the parameters is {@code null}.
     */
    public NotificationType(final String typeName, final Class<T> clazz, final String address) {
        this.typeName = Objects.requireNonNull(typeName);
        this.clazz = Objects.requireNonNull(clazz);
        this.address = Objects.requireNonNull(address);
    }

    /**
     * The name of this type. The name is unique to this notification type.
     *
     * @return The type name.
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * The class of notifications of this type.
     *
     * @return The class.
     */
    public Class<T> getClazz() {
        return clazz;
    }

    /**
     * The address used when sending notifications of this type.
     * <p>
     * Note that this address may not be unique to this notification type.
     *
     * @return The address.
     */
    public String getAddress() {
        return address;
    }

}
