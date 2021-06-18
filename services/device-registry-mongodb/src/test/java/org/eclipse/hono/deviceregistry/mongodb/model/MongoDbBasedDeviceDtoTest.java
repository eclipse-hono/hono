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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * Tests verifying behavior of {@link MongoDbBasedDeviceDto}.
 *
 */
public class MongoDbBasedDeviceDtoTest {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedDeviceDto.class);

    /**
     * Verifies that a DTO created for inserting a new entry is serialized to JSON as expected in order to verify that
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

        LOG.debug("DTO for create:{}{}", System.lineSeparator(), json.encodePrettily());

        assertThat(json).isNotNull();
        // size should match number of assertions below, to make sure no unexpected properties are serialized
        assertThat(json.getMap()).hasSize(7);

        assertThat(json.getString(BaseDto.FIELD_TENANT_ID)).isEqualTo(tenantId);
        assertThat(json.getString(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID)).isEqualTo(deviceId);
        // make sure that the creation date set on the new device is not the one contained in the DeviceStatus
        final var createdAt = json.getInstant(BaseDto.FIELD_CREATED);
        assertThat(createdAt).isBefore(futureInstant);
        assertThat(json.getString(BaseDto.FIELD_VERSION)).isEqualTo(version);
        assertThat(json.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED))
                .isEqualTo(deviceDto.getDeviceStatus().isAutoProvisioned());
        assertThat(json.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT))
                .isEqualTo(deviceDto.getDeviceStatus().isAutoProvisioningNotificationSent());
        assertThat(json.getJsonObject(MongoDbBasedDeviceDto.FIELD_DEVICE)).isNotNull();
    }


    /**
     * Verifies that the a DTO created for updating an entry is serialized to JSON as expected in order to verify that
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

        LOG.debug("DTO for update:{}{}", System.lineSeparator(), json.encodePrettily());

        assertThat(json).isNotNull();
        // size should match number of assertions below, to make sure no unexpected properties are serialized
        assertThat(json.getMap()).hasSize(7);

        assertThat(json.getString(BaseDto.FIELD_TENANT_ID)).isEqualTo(tenantId);
        assertThat(json.getString(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID)).isEqualTo(deviceId);
        assertThat(json.getInstant(BaseDto.FIELD_UPDATED_ON)).isBefore(futureInstant);
        assertThat(json.getString(BaseDto.FIELD_VERSION)).isEqualTo(version);
        assertThat(json.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED))
                .isEqualTo(deviceDto.getDeviceStatus().isAutoProvisioned());
        assertThat(json.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT))
                .isEqualTo(deviceDto.getDeviceStatus().isAutoProvisioningNotificationSent());
        assertThat(json.getJsonObject(MongoDbBasedDeviceDto.FIELD_DEVICE)).isNotNull();
    }

    /**
     * Verifies that a document from the devices collection can be unmarshalled into a DTO.
     */
    @Test
    public void testDecodeForRead() {

        final var json = new JsonObject();
        json.put(MongoDbBasedDeviceDto.FIELD_DEVICE, new JsonObject());
        json.put(MongoDbBasedDeviceDto.FIELD_CREATED, "2020-09-01T00:15:23Z");
        json.put(MongoDbBasedDeviceDto.FIELD_UPDATED_ON, "2021-01-12T10:10:17Z");
        json.put(MongoDbBasedDeviceDto.FIELD_TENANT_ID, "ACME Inc.");
        json.put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, "bumlux");
        json.put(RegistryManagementConstants.FIELD_AUTO_PROVISIONED, true);
        json.put(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT, false);

        final var created = Instant.parse("2020-09-01T00:15:23Z");
        final var updated = Instant.parse("2021-01-12T10:10:17Z");

        final var dto = MongoDbBasedDeviceDto.forRead("ACME Inc.", "bumlux", json);
        assertThat(dto.getData()).isNotNull();
        assertThat(dto.getTenantId()).isEqualTo("ACME Inc.");
        assertThat(dto.getDeviceId()).isEqualTo("bumlux");
        assertThat(dto.isAutoProvisioned()).isTrue();
        assertThat(dto.isAutoProvisioningNotificationSent()).isFalse();
        assertThat(dto.getCreationTime()).isEqualTo(created);
        assertThat(dto.getUpdatedOn()).isEqualTo(updated);
        // device's status property is set as part of MongoDbBasedDeviceDto.getDeviceWithStatus()
        assertThat(dto.getData().getStatus()).isNull();

        final var device = dto.getDeviceWithStatus();
        assertThat(device.getStatus().isAutoProvisioned()).isTrue();
        assertThat(device.getStatus().isAutoProvisioningNotificationSent()).isFalse();
        assertThat(device.getStatus().getCreationTime()).isEqualTo(created);
        assertThat(device.getStatus().getLastUpdate()).isEqualTo(updated);
    }
}
