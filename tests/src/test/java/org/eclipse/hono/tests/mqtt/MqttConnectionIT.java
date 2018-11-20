/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.mqtt;

import io.vertx.core.Future;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttConnectionException;

/**
 * Integration tests for checking connection to the MQTT adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class MqttConnectionIT extends MqttTestBase {

    /**
     * Verifies that the adapter opens a connection to registered devices with credentials.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectSucceedsForRegisteredDevice(final TestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);

        connectToAdapter(tenant, deviceId, password, null).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the adapter rejects connection attempts from unknown devices
     * for which neither registration information nor credentials are on record.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForNonExistingDevice(final TestContext ctx) {

        // GIVEN an adapter
        // WHEN an unknown device tries to connect
        connectToAdapter(IntegrationTestSupport.getUsername("non-existing", Constants.DEFAULT_TENANT), "secret")
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * using wrong credentials.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForWrongCredentials(final TestContext ctx) {

        // GIVEN a registered device
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.registry
        .addDeviceForTenant(tenant, deviceId, password)
        // WHEN the device tries to connect using a wrong password
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "wrong password"))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices belonging
     * to a tenant for which the MQTT adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledAdapter(final TestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final JsonObject adapterDetailsMqtt = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .put(TenantConstants.FIELD_ENABLED, Boolean.FALSE);
        tenant.addAdapterConfiguration(adapterDetailsMqtt);

     // WHEN a device that belongs to the tenant tries to connect to the adapter
        connectToAdapter(tenant, deviceId, password, null).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused with a NOT_AUTHORIZED code
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices for which
     * credentials exist but for which no registration assertion can be retrieved.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDeletedDevices(final TestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.registry
            .addDeviceForTenant(tenant, deviceId, password)
            .compose(device -> helper.registry.deregisterDevice(tenantId, deviceId))
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .setHandler(ctx.asyncAssertFailure(t -> {
                // THEN the connection is refused with a NOT_AUTHORIZED code
                ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                        ((MqttConnectionException) t).code());
            }));
    }


    /**
     * Verifies that the adapter rejects connection attempts from devices that belong to a disabled tenant.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledTenant(final TestContext ctx) {

        // Given a disabled tenant for which the MQTT adapter is enabled
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final JsonObject adapterDetailsMqtt = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);

        final TenantObject tenant = TenantObject.from(tenantId, false);
        tenant.addAdapterConfiguration(adapterDetailsMqtt);

        helper.registry.addDeviceForTenant(tenant, deviceId, password)
                .recover(t -> Future.failedFuture(t)).compose(ok -> {
                    final Future<MqttConnAckMessage> result = Future.future();
                    final MqttClientOptions options = new MqttClientOptions()
                            .setUsername(IntegrationTestSupport.getUsername(deviceId, tenantId))
                            .setPassword(password);
                    mqttClient = MqttClient.create(VERTX, options);
                    // WHEN a device that belongs to the disabled tenant tries to connect to the adapter
                    mqttClient.connect(IntegrationTestSupport.MQTT_PORT, IntegrationTestSupport.MQTT_HOST, result);
                    return result;
                }).setHandler(ctx.asyncAssertFailure(t -> {
                    ctx.assertTrue(t instanceof MqttConnectionException);
                    // THEN the connection is refused with a NOT_AUTHORIZED code
                    ctx.assertEquals(((MqttConnectionException) t).code(),
                            MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
                }));
    }
}
