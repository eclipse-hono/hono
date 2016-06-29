/**
 * Copyright (c) 2016 Red Hat and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat
 */
package org.eclipse.hono.tests.client;

import org.apache.activemq.broker.BrokerService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations="classpath:application-activemq.properties")
@ActiveProfiles("activemq")
public class HonoActiveMQTest extends HonoTestSupport {


   static BrokerService broker;

   @BeforeClass
   public static void before() throws Exception {
      broker = new BrokerService();
      broker.addConnector("amqp://localhost:5671");
      broker.setPersistent(false);
      broker.start();
      broker.waitUntilStarted();
   }

   @AfterClass
   public static void after() throws Exception {
      if (broker != null) {
         broker.stop();
         broker.waitUntilStopped();
      }
   }
}
