/**
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
package org.eclipse.hono.tests.coap.lwm2m;

import java.util.List;
import java.util.Map;

import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.servers.ServerIdentity;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel.Type;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An example implementation of the standard LwM2M Firmware object.
 */
public class ExampleFirmware extends BaseInstanceEnabler {

    private static final Logger LOG = LoggerFactory.getLogger(ExampleFirmware.class);

    private static final List<Integer> SUPPORTED_RESOURCES = List.of(1, 2, 3, 5, 8, 9);

    private Map<Integer, Long> supportedDownloadProtocols = Map.of(0, 3L); // only support https
    private int supportedDeliveryMethod = 0; // only support pull
    private String packageUri = "";
    private int state = 0;
    private int updateResult = 0;

    /**
     * Creates a new Firmware object.
     */
    public ExampleFirmware() {
    }

    @Override
    public ObserveResponse observe(final ServerIdentity identity) {
        LOG.debug("Observe on Firmware");
        return super.observe(identity);
    }

    @Override
    public ReadResponse read(final ServerIdentity identity, final int resourceid) {
        LOG.debug("Read on Firmware resource /{}/{}/{}", getModel().id, getId(), resourceid);
        switch (resourceid) {
        case 1:
            return ReadResponse.success(resourceid, getPackageUri());
        case 3:
            return ReadResponse.success(resourceid, getState());
        case 5:
            return ReadResponse.success(resourceid, getUpdateResult());
        case 8:
            return ReadResponse.success(resourceid, supportedDownloadProtocols, Type.INTEGER);
        case 9:
            return ReadResponse.success(resourceid, supportedDeliveryMethod);
        default:
            return super.read(identity, resourceid);
        }
    }

    @Override
    public ExecuteResponse execute(final ServerIdentity identity, final int resourceid, final String params) {
        String withParams = null;
        if (params != null && params.length() != 0) {
            withParams = " with params " + params;
        }
        LOG.debug("Execute on Firmware resource /{}/{}/{} {}", getModel().id, getId(), resourceid,
                withParams != null ? withParams : "");

        switch (resourceid) {
        case 2: // update
            LOG.info("starting firmware update");
            break;
        default:
            return super.execute(identity, resourceid, params);
        }
        return ExecuteResponse.success();
    }

    @Override
    public WriteResponse write(final ServerIdentity identity, final int resourceid, final LwM2mResource value) {
        LOG.debug("Write on Firmware resource /{}/{}/{}", getModel().id, getId(), resourceid);

        switch (resourceid) {
        case 1:
            setPackageUri((String) value.getValue());
            return WriteResponse.success();
        default:
            return super.write(identity, resourceid, value);
        }
    }

    private void setPackageUri(final String packageUri) {
        this.packageUri = packageUri;
    }

    private String getPackageUri() {
        return packageUri;
    }

    void setState(final int newState) {
        this.state = newState;
    }

    private int getState() {
        return state;
    }

    void setUpdateResult(final int result) {
        this.updateResult = result;
    }

    private int getUpdateResult() {
        return updateResult;
    }

    @Override
    public List<Integer> getAvailableResourceIds(final ObjectModel model) {
        return SUPPORTED_RESOURCES;
    }
}
