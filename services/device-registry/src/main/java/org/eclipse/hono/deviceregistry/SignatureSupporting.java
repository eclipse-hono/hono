/**
 * Copyright (c) 2017 Red Hat Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial creation
 */

package org.eclipse.hono.deviceregistry;

import org.eclipse.hono.config.SignatureSupportingConfigProperties;

/**
 * An interface for providing {@link SignatureSupportingConfigProperties}
 * <p>
 * This interface is intended for implementors of configurations which support
 * {@link SignatureSupportingConfigProperties}.
 */
public interface SignatureSupporting {

    /**
     * Gets the properties for determining key material for creating registration assertion tokens.
     *
     * @return The properties.
     */
    public SignatureSupportingConfigProperties getSigning();

}
