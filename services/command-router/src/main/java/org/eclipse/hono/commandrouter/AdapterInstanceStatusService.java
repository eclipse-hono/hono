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

package org.eclipse.hono.commandrouter;

import org.eclipse.hono.deviceconnection.infinispan.client.AdapterInstanceStatusProvider;
import org.eclipse.hono.util.Lifecycle;

/**
 * A service for determining the status of adapter instances.
 */
public interface AdapterInstanceStatusService extends AdapterInstanceStatusProvider, Lifecycle {
}
