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

package org.eclipse.hono.client.notification;

import java.util.function.Function;

/**
 * A function that returns the messaging address to be used by a {@link NotificationReceiver} for a given class of
 * notifications.
 *
 * @param <T> The class of the notification.
 */
public interface NotificationAddressProvider<T extends Notification> extends Function<Class<T>, String> {

}
