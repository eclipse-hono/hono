/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.lwm2m.observation;

import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.LwM2mPath;

/**
 * A factory for creating payload to be sent to Hono's API as AMQP 1.0 message payload.
 */
public interface TelemetryPayloadFactory {

    /**
     * Gets the content type of the payload this factory produces.
     * 
     * @return The content type.
     */
    String getContentType();

    /**
     * Creates a new payload object for a <em>leshan</em> notification.
     * 
     * @param resourcePath The observed resource's object, object instance and resource ID.
     * @param objectModel The LWM2M Object definition corresponding to the observed resource's object ID.
     * @param node The value(s) from the notification.
     * @return The byte representation of the payload.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    byte[] getPayload(LwM2mPath resourcePath, ObjectModel objectModel, LwM2mNode node);
}
