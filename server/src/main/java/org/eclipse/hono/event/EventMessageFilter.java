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
 *
 */
package org.eclipse.hono.event;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.BaseMessageFilter;
import org.eclipse.hono.util.ResourceIdentifier;

/**
 * A filter for verifying the format of event messages.
 */
public final class EventMessageFilter extends BaseMessageFilter {

    private EventMessageFilter() {
        // prevent instantiation
    }

    /**
     * Checks whether a given event message contains all required properties.
     * <p>
     * For successful verification, the message must contain a <em>device_id</em> in its set
     * of <em>application</em> properties. If the link target contains a <em>deviceId</em> segment
     * then its value must match that of the property as well.
     * </p>
     * <p>
     * After successful verification the following properties are added to the message's <em>annotations</em>:
     * <ul>
     * <li><em>device_id</em> - the ID of the device that reported the data.</li>
     * <li><em>tenant_id</em> - the ID of the tenant as indicated by the link target's second segment.</li>
     * <li><em>resource_id</em> - the full resource path including the endpoint, the tenant and the device ID.</li>
     * </ul>
     * 
     * @param linkTarget the link target address to match the event message's properties against.
     * @param msg the message to verify.
     * @return {@code true} if the given message complies with the <em>Event</em> API specification, {@code false}
     *         otherwise.
     */
     public static boolean verify(final ResourceIdentifier linkTarget, final Message msg) {
         return verifyStandardProperties(linkTarget, msg);
    }
}
