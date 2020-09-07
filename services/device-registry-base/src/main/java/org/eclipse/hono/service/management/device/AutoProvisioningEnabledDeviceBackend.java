/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.device;

import java.security.cert.X509Certificate;

import org.eclipse.hono.service.management.OperationResult;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * Interface that adds automatic device provisioning to the DeviceBackend.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/dev/concepts/device-provisioning/#automatic-device-provisioning">
 *      Automatic Device Provisioning</a>
 */
public interface AutoProvisioningEnabledDeviceBackend extends DeviceBackend {

    /**
     * Registers a device together with a set of credentials for the given client certificate.
     *
     * @param tenantId The tenant to which the device belongs.
     * @param clientCertificate The X.509 certificate of the device to be provisioned.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A (succeeded) future containing the result of the operation. The <em>status</em> will be
     *         <ul>
     *         <li><em>201 CREATED</em> if the device has successfully been provisioned.</li>
     *         <li><em>4XX</em> if the provisioning failed. The payload may contain an error description.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    default Future<OperationResult<String>> provisionDevice(
            final String tenantId,
            final X509Certificate clientCertificate,
            final Span span) {

        return AutoProvisioning.provisionDevice(
                this,
                this,
                tenantId,
                clientCertificate,
                span);

    }
}
