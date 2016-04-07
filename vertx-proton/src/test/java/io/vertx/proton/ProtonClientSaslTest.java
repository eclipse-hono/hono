package io.vertx.proton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.sasl.impl.ProtonSaslAnonymousImpl;

@RunWith(VertxUnitRunner.class)
public class ProtonClientSaslTest extends ActiveMQTestBase {

    private static Logger LOG = LoggerFactory.getLogger(ProtonClientSaslTest.class);

    private Vertx vertx;

    private boolean anonymousAccessAllowed = false;

    @Before
    @Override
    public void setUp() throws Exception{
        super.setUp();
        vertx = Vertx.vertx();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            if(vertx != null) {
                vertx.close();
            }
        }
    }

    @Override
    protected boolean isAnonymousAccessAllowed() {
        return anonymousAccessAllowed;
    }

    @Test(timeout = 20000)
    public void testConnectWithValidUserPassSucceeds(TestContext context) throws Exception {
        doConnectWithGivenCredentialsTestImpl(context, USERNAME_GUEST, PASSWORD_GUEST, true);
    }

    @Test(timeout = 20000)
    public void testConnectWithInvalidUserPassFails(TestContext context) throws Exception {
        doConnectWithGivenCredentialsTestImpl(context, USERNAME_GUEST, "wrongpassword", false);
    }

    @Test(timeout = 20000)
    public void testConnectAnonymousWithoutUserPass(TestContext context) throws Exception {
        doConnectWithGivenCredentialsTestImpl(context, null, null, false);
        anonymousAccessAllowed = true;
        restartBroker();
        doConnectWithGivenCredentialsTestImpl(context, null, null, true);
    }

    @Test(timeout = 20000)
    public void testRestrictSaslMechanisms(TestContext context) throws Exception {
        ProtonClientOptions options = new ProtonClientOptions();

        // Try with the wrong password, with anonymous access disabled, expect connect to fail
        doConnectWithGivenCredentialsTestImpl(context, options, USERNAME_GUEST, "wrongpassword", false);

        // Try with the wrong password, with anonymous access enabled, expect connect still to fail
        anonymousAccessAllowed = true;
        restartBroker();
        doConnectWithGivenCredentialsTestImpl(context, options, USERNAME_GUEST, "wrongpassword", false);

        // Now restrict the allows SASL mechanisms to ANONYMOUS, then expect connect to succeed as it wont use the invalid credentials
        options.setAllowedSaslMechanisms(ProtonSaslAnonymousImpl.MECH_NAME);
        doConnectWithGivenCredentialsTestImpl(context, options, USERNAME_GUEST, "wrongpassword", true);
    }

    private void doConnectWithGivenCredentialsTestImpl(TestContext context, String username, String password, boolean expectConnectToSucceed) {
        doConnectWithGivenCredentialsTestImpl(context, new ProtonClientOptions(), username, password, expectConnectToSucceed);
    }

    private void doConnectWithGivenCredentialsTestImpl(TestContext context, ProtonClientOptions options, String username, String password, boolean expectConnectToSucceed) {
        Async async = context.async();

        // Connect the client and open the connection to verify it works
        ProtonClient client = ProtonClient.create(vertx);
        client.connect(options, "localhost", getBrokerAmqpConnectorPort(), username, password, res -> {
            if(expectConnectToSucceed) {
                // Expect connect to succeed
                context.assertTrue(res.succeeded());
                ProtonConnection connection = res.result();

                connection.openHandler(connRes -> {
                    context.assertTrue(connRes.succeeded());
                    LOG.trace("Client connection open");
                    async.complete();
                })
                .open();
            } else {
                // Expect connect to fail
                context.assertFalse(res.succeeded());
                LOG.trace("Connect failed");
                async.complete();
            }
        });

        async.awaitSuccess();
    }
}
