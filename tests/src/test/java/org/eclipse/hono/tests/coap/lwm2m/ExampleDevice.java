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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TimeZone;

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

import io.vertx.core.Handler;

/**
 * An example implementation of the standard LwM2M Firmware object.
 */
public class ExampleDevice extends BaseInstanceEnabler {

    private static final Logger LOG = LoggerFactory.getLogger(ExampleDevice.class);

    private static final Random RANDOM = new Random();
    private static final List<Integer> SUPPORTED_RESOURCES = List.of(0, 1, 2, 3, 4, 5, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21);

    private Handler<Void> rebootHandler;
    private Handler<Void> resetHandler;
    private String utcOffset = new SimpleDateFormat("X").format(Calendar.getInstance().getTime());
    private String timeZone = TimeZone.getDefault().getID();

    /**
     * Creates a new device.
     */
    public ExampleDevice() {
    }

    /**
     * Sets the handler to invoke when the device's <em>reboot</em> resource is being executed.
     *
     * @param handler The handler.
     */
    public void setRebootHandler(final Handler<Void> handler) {
        this.rebootHandler = handler;
    }

    /**
     * Sets the handler to invoke when the device's <em>reset</em> resource is being executed.
     *
     * @param handler The handler.
     */
    public void setResetHandler(final Handler<Void> handler) {
        this.resetHandler = handler;
    }

    @Override
    public ObserveResponse observe(final ServerIdentity identity) {
        LOG.debug("Observe on Device");
        return super.observe(identity);
    }

    @Override
    public ReadResponse read(final ServerIdentity identity, final int resourceid) {
        LOG.debug("Read on Device resource /{}/{}/{}", getModel().id, getId(), resourceid);
        switch (resourceid) {
        case 0:
            return ReadResponse.success(resourceid, getManufacturer());
        case 1:
            return ReadResponse.success(resourceid, getModelNumber());
        case 2:
            return ReadResponse.success(resourceid, getSerialNumber());
        case 3:
            return ReadResponse.success(resourceid, getFirmwareVersion());
        case 9:
            return ReadResponse.success(resourceid, getBatteryLevel());
        case 10:
            return ReadResponse.success(resourceid, getMemoryFree());
        case 11:
            final Map<Integer, Long> errorCodes = new HashMap<>();
            errorCodes.put(0, getErrorCode());
            return ReadResponse.success(resourceid, errorCodes, Type.INTEGER);
        case 13:
            return ReadResponse.success(resourceid, getCurrentTime());
        case 14:
            return ReadResponse.success(resourceid, getUtcOffset());
        case 15:
            return ReadResponse.success(resourceid, getTimezone());
        case 16:
            return ReadResponse.success(resourceid, getSupportedBinding());
        case 17:
            return ReadResponse.success(resourceid, getDeviceType());
        case 18:
            return ReadResponse.success(resourceid, getHardwareVersion());
        case 19:
            return ReadResponse.success(resourceid, getSoftwareVersion());
        case 20:
            return ReadResponse.success(resourceid, getBatteryStatus());
        case 21:
            return ReadResponse.success(resourceid, getMemoryTotal());
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
        LOG.debug("Execute on Device resource /{}/{}/{} {}", getModel().id, getId(), resourceid,
                withParams != null ? withParams : "");

        switch (resourceid) {
        case 4:
            Optional.ofNullable(rebootHandler).ifPresent(h -> h.handle(null));
            LOG.info("rebooting device");
//            new Timer("Reboot Lwm2mClient").schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    getLwM2mClient().stop(true);
//                    try {
//                        Thread.sleep(500);
//                    } catch (InterruptedException e) {
//                    }
//                    getLwM2mClient().start();
//                }
//            }, 500);
            break;
        case 5:
            LOG.info("resetting device");
            Optional.ofNullable(resetHandler).ifPresent(h -> h.handle(null));
            break;
        default:
            // do nothing
        }
        return ExecuteResponse.success();
    }

    @Override
    public WriteResponse write(final ServerIdentity identity, final int resourceid, final LwM2mResource value) {
        LOG.debug("Write on Device resource /{}/{}/{}", getModel().id, getId(), resourceid);

        switch (resourceid) {
        case 13:
            return WriteResponse.notFound();
        case 14:
            setUtcOffset((String) value.getValue());
            fireResourcesChange(resourceid);
            return WriteResponse.success();
        case 15:
            setTimezone((String) value.getValue());
            fireResourcesChange(resourceid);
            return WriteResponse.success();
        default:
            return super.write(identity, resourceid, value);
        }
    }

    private String getManufacturer() {
        return "Leshan Demo Device";
    }

    private String getModelNumber() {
        return "Model 500";
    }

    private String getSerialNumber() {
        return "LT-500-000-0001";
    }

    private String getFirmwareVersion() {
        return "1.0.0";
    }

    private long getErrorCode() {
        return 0;
    }

    private int getBatteryLevel() {
        return RANDOM.nextInt(101);
    }

    private long getMemoryFree() {
        return Runtime.getRuntime().freeMemory() / 1024;
    }

    private Date getCurrentTime() {
        return new Date();
    }

    private String getUtcOffset() {
        return utcOffset;
    }

    private void setUtcOffset(final String t) {
        utcOffset = t;
    }

    private String getTimezone() {
        return timeZone;
    }

    private void setTimezone(final String t) {
        timeZone = t;
    }

    private String getSupportedBinding() {
        return "U";
    }

    private String getDeviceType() {
        return "Demo";
    }

    private String getHardwareVersion() {
        return "1.0.1";
    }

    private String getSoftwareVersion() {
        return "1.0.2";
    }

    private int getBatteryStatus() {
        return RANDOM.nextInt(7);
    }

    private long getMemoryTotal() {
        return Runtime.getRuntime().totalMemory() / 1024;
    }

    @Override
    public List<Integer> getAvailableResourceIds(final ObjectModel model) {
        return SUPPORTED_RESOURCES;
    }
}
