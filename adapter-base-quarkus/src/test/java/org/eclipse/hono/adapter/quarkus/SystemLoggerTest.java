/**
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
 */


package org.eclipse.hono.adapter.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.cert.TrustAnchor;
import java.util.List;

import org.eclipse.hono.service.auth.device.DeviceCertificateValidator;
import org.eclipse.hono.test.VertxTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying proper initialization of the {@code System.Logger} used
 * for logging security events.
 * <p>
 * The tests fail when using the JBoss (embedded) LogManager as Quarkus does.
 * To run the tests with the JBoss LogManager, the following system variable
 * when running the JVM needs to be set:
 * <pre>
 * -Djava.util.logging.manager=org.jboss.logmanager.LogManager
 * </pre>
 * In order to do so the <em>jboss-logmanager</em> Maven profile can be used:
 * <pre>
 * mvn test -Pjboss-logmanager
 * </pre>
 * <p>
 * The JBoss LogManager seems to erroneously configure the logger to log at ALL levels.
 * The problem with that is the following:
 * <p>
 * When running on OpenJDK 11 (as Hono does by default), the protocol adapters implicitly
 * use the {@code sun.security.provider.certpath.PKIXCertPathValidator} class for
 * validating a device's client certificate against the trust anchor defined for the
 * device's tenant. That class' <em>validate</em> method invokes {@code
 * jdk.internal.event.EventHelper.isLoggingSecurity()} to determine if the validation
 * result should be logged via the <em>jdk.event.security</em> {@code System.Logger}.
 * <p>
 * With the logger being initialized incorrectly, the method will try to log the event
 * and <a href="https://bugs.openjdk.java.net/browse/JDK-8255348">bug JDK-8255348</a>
 * will then cause a NullPointerException being thrown and, consequently, the certificate
 * validation to fail. This problem is illustrated by the
 * {@link #testValidateSucceedsForTrustAnchorBasedOnPublicKey(Vertx, VertxTestContext)}
 * test case.
 * <p>
 * One could argue that this will go away when the bug in OpenJDK is fixed. While that
 * is true, logging all security related events, regardless of logging configuration,
 * would still be incorrect.
 */
@ExtendWith(VertxExtension.class)
class SystemLoggerTest {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SystemLoggerTest.class);

    /**
     * Checks if the JVM has been configured to allow access to classes and private fields
     * of the java.base/jdk.internal.event module using reflection.
     *
     * @return {@code true} if the JVM has been started with option
     *         {@code --add-opens java.base/jdk.internal.event=ALL-UNNAMED}.
     */
    boolean reflectionOnJdkInternalEventAllowed() {

        try {
            final Class<?> clazz = Class.forName("jdk.internal.event.EventHelper");
            final Field loggerHandlerField = clazz.getDeclaredField("LOGGER_HANDLE");
            loggerHandlerField.setAccessible(true);
            loggerHandlerField.get(clazz);
            return true;
        } catch (final Exception e) {
            LOG.info("access to jdk.internal.event.EventHelper via reflection is not possible, "
                    + "run JVM with option --add-opens java.base/jdk.internal.event=ALL-UNNAMED "
                    + "to enable tests");
            return false;
        }
    }

    /**
     * Verifies that the <em>jdk.event.security</em> {@code System.Logger} logs
     * at level INFO and above only.
     *
     * @throws Exception if the {@code jdk.internal.event.EventHandler.isLoggingSecurity()} method
     *                   cannot be accessed via reflection.
     */
    @Test
    @EnabledIf("reflectionOnJdkInternalEventAllowed")
    void testSystemSecurityLoggerLevel() throws Exception {

        final Class<?> clazz = Class.forName("jdk.internal.event.EventHelper");
        // we first need to invoke the isLoggingSecurity() method in order
        // to (lazily) initialize its LOGGER_HANDLE field with a System.Logger for
        // jdk.event.security
        final Method method = clazz.getMethod("isLoggingSecurity", (Class<?>[]) null);
        final Object result = method.invoke(clazz, (Object[]) null);
        assertThat(result)
            .as("EventHelper.isLoggingSecurity() returns false by default")
            .isEqualTo(Boolean.FALSE);

        // now we can retrieve the Logger from the LOGGER_HANDLE field
        final Field loggerHandlerField = clazz.getDeclaredField("LOGGER_HANDLE");
        loggerHandlerField.setAccessible(true);
        final VarHandle handle = (VarHandle) loggerHandlerField.get(clazz);
        final Logger securityLogger = (Logger) handle.get();

        LOG.info("EventHandler.isLoggingSecurity() uses java.lang.System.Logger [type: {}, TRACE: {}, DEBUG: {}, INFO: {}, WARNING: {}, ERROR: {}]",
                securityLogger.getClass().getName(),
                securityLogger.isLoggable(Level.TRACE),
                securityLogger.isLoggable(Level.DEBUG),
                securityLogger.isLoggable(Level.INFO),
                securityLogger.isLoggable(Level.WARNING),
                securityLogger.isLoggable(Level.ERROR));

        assertThat(securityLogger.isLoggable(Level.TRACE))
            .as("security System.Logger does not log at TRACE level")
            .isFalse();
        assertThat(securityLogger.isLoggable(Level.DEBUG))
            .as("security System.Logger does not log at DEBUG level")
            .isFalse();
        assertThat(securityLogger.isLoggable(Level.INFO))
            .as("security System.Logger logs at INFO level")
            .isTrue();
        assertThat(securityLogger.isLoggable(Level.WARNING))
            .as("security System.Logger logs at WARNING level")
            .isTrue();
        assertThat(securityLogger.isLoggable(Level.ERROR))
            .as("security System.Logger logs at ERROR level")
            .isTrue();
    }

    /**
     * Verifies that the {@link DeviceCertificateValidator} succeeds to verify a certificate chain
     * using a trust anchor that has been created with a name and public key
     * instead of a certificate.
     *
     * @param vertx The vert.x instance to use.
     * @param ctx The vert.x test context.
     */
    @Test
    void testValidateSucceedsForTrustAnchorBasedOnPublicKey(final Vertx vertx, final VertxTestContext ctx) {

        final DeviceCertificateValidator validator = new DeviceCertificateValidator();
        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create("iot.eclipse.org");
        VertxTools.getCertificate(vertx, deviceCert.certificatePath())
            .compose(cert -> {
                final TrustAnchor ca = new TrustAnchor(cert.getSubjectX500Principal(), cert.getPublicKey(), null);
                return validator.validate(List.of(cert), ca);
            })
            .onComplete(ctx.completing());
    }
}
