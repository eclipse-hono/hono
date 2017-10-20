/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.deviceregistry;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.RegistrationConstants.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests {@link FileBasedRegistrationService}.
 */
@RunWith(VertxUnitRunner.class)
public class FileBasedRegistrationServiceTest
{
   private static final String TENANT = "tenant";
   private static final String DEVICE = "device";
   private FileBasedRegistrationConfigProperties props;
   private FileBasedRegistrationService registrationService;
   Vertx vertx;
   EventBus eventBus;

   /**
    * Sets up the fixture.
    */
   @Before
   public void setUp() {
      vertx = mock(Vertx.class);
      Context ctx = mock(Context.class);
      eventBus = mock(EventBus.class);
      when(vertx.eventBus()).thenReturn(eventBus);

      props = new FileBasedRegistrationConfigProperties();
      registrationService = new FileBasedRegistrationService();
      registrationService.setConfig(props);
      registrationService.init(vertx, ctx);
   }

   /**
    * Removes all devices from the registry.
    */
   @After
   public void clear() {
       registrationService.clear();
   }

   /**
    * Verifies that the registry enforces the maximum devices per tenant limit.
    */
   @Test
   public void testAddDeviceFailsIfDeviceLimitIsReached() {

       // GIVEN a registry whose devices-per-tenant limit has been reached
       props.setMaxDevicesPerTenant(1);
       registrationService.addDevice(TENANT, DEVICE, null);

       // WHEN registering an additional device for the tenant
       RegistrationResult result = registrationService.addDevice(TENANT, "newDevice", null);

       // THEN the result contains a FORBIDDEN status code and the device has not been added to the registry
       assertThat(result.getStatus(), is(HTTP_FORBIDDEN));
       assertThat(registrationService.getDevice(TENANT, "newDevice").getStatus(), is(HTTP_NOT_FOUND));
   }

   /**
    * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
    */
   @Test
   public void testUpdateDeviceFailsIfModificationIsDisabled() {

       // GIVEN a registry that has been configured to not allow modification of entries
       // which contains a device
       props.setModificationEnabled(false);
       registrationService.addDevice(TENANT, DEVICE, null);

       // WHEN trying to update the device
       RegistrationResult result = registrationService.updateDevice(TENANT, DEVICE, new JsonObject().put("updated", true));

       // THEN the result contains a FORBIDDEN status code and the device has not been updated
       assertThat(result.getStatus(), is(HTTP_FORBIDDEN));
       assertFalse(registrationService.getDevice(TENANT, DEVICE).getPayload().containsKey("updated"));
   }

   /**
    * Verifies that the <em>modificationEnabled</em> property prevents removing an existing entry.
    */
   @Test
   public void testRemoveDeviceFailsIfModificationIsDisabled() {

       // GIVEN a registry that has been configured to not allow modification of entries
       // which contains a device
       props.setModificationEnabled(false);
       registrationService.addDevice(TENANT, DEVICE, null);

       // WHEN trying to remove the device
       RegistrationResult result = registrationService.removeDevice(TENANT, DEVICE);

       // THEN the result contains a FORBIDDEN status code and the device has not been removed
       assertThat(result.getStatus(), is(HTTP_FORBIDDEN));
       assertThat(registrationService.getDevice(TENANT, DEVICE).getStatus(), is(HTTP_OK));
   }

   @Test
   public void testGetUnknownDeviceReturnsNotFound() {
       processMessageAndExpectResponse(mockMsg(ACTION_GET), getServiceReplyAsJson(HTTP_NOT_FOUND, TENANT, DEVICE));
   }

   @Test
   public void testDeregisterUnknownDeviceReturnsNotFound() {
       processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getServiceReplyAsJson(HTTP_NOT_FOUND, TENANT, DEVICE));
   }

   @Test
   public void testProcessRegisterMessageDetectsUnsupportedAction() {
       processMessageAndExpectResponse(mockMsg("bumlux"), getServiceReplyAsJson(HTTP_BAD_REQUEST, TENANT, DEVICE));
   }

   @Test
   public void testDuplicateRegistrationFails() {
       processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getServiceReplyAsJson(HTTP_CREATED, TENANT, DEVICE));
       processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getServiceReplyAsJson(HTTP_CONFLICT, TENANT, DEVICE));
   }

   @Test
   public void testGetSucceedsForRegisteredDevice() {
       processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getServiceReplyAsJson(HTTP_CREATED, TENANT, DEVICE));
       processMessageAndExpectResponse(mockMsg(ACTION_GET), getServiceReplyAsJson(HTTP_OK, TENANT, DEVICE, expectedMessage(DEVICE)));
   }

   @Test
   public void testGetFailsForDeregisteredDevice() {
       processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getServiceReplyAsJson(HTTP_CREATED, TENANT, DEVICE));
       processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getServiceReplyAsJson(HTTP_NO_CONTENT, TENANT, DEVICE));
       processMessageAndExpectResponse(mockMsg(ACTION_GET), getServiceReplyAsJson(HTTP_NOT_FOUND, TENANT, DEVICE));
   }

   @Test
   public void testPeriodicSafeJobIsNotScheduledIfSavingIfDisabled(final TestContext ctx) throws Exception {

       FileSystem fs = mock(FileSystem.class);
       when(fs.existsBlocking(anyString())).thenReturn(Boolean.FALSE);
       when(vertx.fileSystem()).thenReturn(fs);

       Future<Void> startupTracker = Future.future();
       startupTracker.setHandler(ctx.asyncAssertSuccess(done -> {
           verify(vertx, never()).setPeriodic(anyLong(), any(Handler.class));
       }));

       props.setSaveToFile(false);
       registrationService.doStart(startupTracker);
   }

   @Test
   public void testContentsNotSavedOnShutdownIfSavingIfDisabled(final TestContext ctx) throws Exception {

       FileSystem fs = mock(FileSystem.class);
       when(fs.existsBlocking(anyString())).thenReturn(Boolean.FALSE);
       when(vertx.fileSystem()).thenReturn(fs);

       Future<Void> shutdownTracker = Future.future();
       shutdownTracker.setHandler(ctx.asyncAssertSuccess(done -> {
           verify(vertx, times(1)).fileSystem();
       }));

       props.setSaveToFile(false);

       Future<Void> startupTracker = Future.future();
       registrationService.doStart(startupTracker);
       startupTracker.compose(started -> {
           registrationService.addDevice(TENANT, DEVICE, new JsonObject());
           registrationService.doStop(shutdownTracker);
       }, shutdownTracker);
   }

   private static JsonObject expectedMessage(final String id) {
       return new JsonObject()
               .put(FIELD_DEVICE_ID, id)
               .put(FIELD_DATA, new JsonObject().put(FIELD_ENABLED, Boolean.TRUE));
   }

   private static Message<JsonObject> mockMsg(final String action) {
       return mockMsg(action, TENANT);
   }

   @SuppressWarnings("unchecked")
   private static Message<JsonObject> mockMsg(final String action, final String tenant) {
       final JsonObject registrationJson = getServiceRequestAsJson(action, tenant, DEVICE);
       final Message<JsonObject> message = mock(Message.class);
       when(message.body()).thenReturn(registrationJson);
       return message;
   }

   private void processMessageAndExpectResponse(final Message<JsonObject> request, final JsonObject expectedResponse) {
       registrationService.processRegistrationMessage(request);
       verify(request).reply(expectedResponse);
   }
}
