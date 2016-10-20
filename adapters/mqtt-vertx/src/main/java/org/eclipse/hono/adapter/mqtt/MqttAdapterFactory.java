/**
 * Copyright (c) 2016 Red Hat
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 */

package org.eclipse.hono.adapter.mqtt;

/**
 * A factory for creating {@link VertxBasedMqttProtocolAdapter} instances.
 */
public interface MqttAdapterFactory {

    VertxBasedMqttProtocolAdapter getMqttAdapter();
}
