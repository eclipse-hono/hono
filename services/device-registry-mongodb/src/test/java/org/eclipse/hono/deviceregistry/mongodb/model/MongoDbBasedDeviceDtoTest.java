/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.mongodb.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceStatus;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;


class MongoDbBasedDeviceDtoTest {

    /**
     * Tests that a DTO created for inserting a new entry is serialized to JSON as expected in order to verify that
     * the document to be persisted is written as expected.
     */
    @Test
    public void testEncodeForCreate() {
        final String tenantId = "barfoo";
        final String deviceId = "foobar";
        final var futureInstant = Instant.now().plusSeconds(3600);
        final DeviceStatus deviceStatus = new DeviceStatus()
                // make sure that these values do not interfere with the corresponding properties of DeviceDto's parent classes
                .setAutoProvisioned(true)
                .setCreationTime(futureInstant);
        final Device device = new Device();
        device.setStatus(deviceStatus);
        final String version = "spam";

        final var deviceDto = MongoDbBasedDeviceDto.forCreation(MongoDbBasedDeviceDto::new, tenantId, deviceId, true, device, version);
        final var json = JsonObject.mapFrom(deviceDto);

        assertThat(json).isNotNull();
        // size should match number of assertions below, to make sure no unexpected properties are serialized
        assertThat(json.getMap()).hasSize(7);

        assertThat(json.getString(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo(tenantId);
        assertThat(json.getString(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID)).isEqualTo(deviceId);
        // make sure that the creation date set on the new device is not the one contained in the DeviceStatus
        final var createdAt = json.getInstant(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE);
        assertThat(createdAt).isBefore(futureInstant);
        assertThat(json.getString(BaseDto.FIELD_VERSION)).isEqualTo(version);
        assertThat(json.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED))
                .isEqualTo(deviceDto.getDeviceStatus().isAutoProvisioned());
        assertThat(json.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT))
                .isEqualTo(deviceDto.getDeviceStatus().isAutoProvisioningNotificationSent());
        assertThat(json.getJsonObject(MongoDbBasedDeviceDto.FIELD_DEVICE)).isNotNull();
    }


    /**
     * Tests that the a DTO created for updating an entry is serialized to JSON as expected in order to verify that
     * the document to be persisted is written as expected.
     */
    @Test
    public void testEncodeForUpdate() {
        final String tenantId = "barfoo";
        final String deviceId = "foobar";
        final var futureInstant = Instant.now().plusSeconds(3600);
        final DeviceStatus deviceStatus = new DeviceStatus()
                // make sure that these values do not interfere with the corresponding properties of DeviceDto's parent classes
                .setAutoProvisioningNotificationSent(false)
                .setLastUpdate(futureInstant);
        final Device device = new Device();
        device.setStatus(deviceStatus);
        final String version = "spam";

        final var deviceDto = MongoDbBasedDeviceDto.forUpdate(MongoDbBasedDeviceDto::new, tenantId, deviceId, true, device, version);
        final var json = JsonObject.mapFrom(deviceDto);

        assertThat(json).isNotNull();
        // size should match number of assertions below, to make sure no unexpected properties are serialized
        assertThat(json.getMap()).hasSize(7);

        assertThat(json.getString(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo(tenantId);
        assertThat(json.getString(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID)).isEqualTo(deviceId);
        assertThat(json.getInstant(BaseDto.FIELD_UPDATED_ON)).isBefore(futureInstant);
        assertThat(json.getString(BaseDto.FIELD_VERSION)).isEqualTo(version);
        assertThat(json.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED))
                .isEqualTo(deviceDto.getDeviceStatus().isAutoProvisioned());
        assertThat(json.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT))
                .isEqualTo(deviceDto.getDeviceStatus().isAutoProvisioningNotificationSent());
        assertThat(json.getJsonObject(MongoDbBasedDeviceDto.FIELD_DEVICE)).isNotNull();
    }
}
