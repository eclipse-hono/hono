/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.client.api.model;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Represents a {@code Message}.
 */
public interface Message {
    String getTopic();

    Map<String, String> getHeaders();

    ByteBuffer getPayload();
}
