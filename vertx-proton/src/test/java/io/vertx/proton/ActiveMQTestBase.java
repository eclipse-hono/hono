package io.vertx.proton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.security.AuthenticationUser;
import org.apache.activemq.security.SimpleAuthenticationPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import io.vertx.core.logging.Logger;

public abstract class ActiveMQTestBase {

    public static final String USERNAME_ADMIN = "system";
    public static final String PASSWORD_ADMIN = "manager";

    public static final String USERNAME_USER = "user";
    public static final String PASSWORD_USER = "userpass";

    public static final String USERNAME_GUEST = "guest";
    public static final String PASSWORD_GUEST = "guestpass";

    private static final Logger LOG = io.vertx.core.logging.LoggerFactory.getLogger(ActiveMQTestBase.class);

    private static final int PORT = Integer.getInteger("activemq.test.amqp.port", 0);
    private static final String AMQP_CONNECTOR_NAME = "amqp";
    private static final String DATA_PARENT_DIR = "target" + File.separator + "activemq-data";

    @Rule
    public TestName name = new TestName();

    protected BrokerService brokerService;

    @Before
    public void setUp() throws Exception {
        LOG.trace("========== setUp " + getTestNameWithClass() + " ==========");
        startBroker();
    }

    @After
    public void tearDown() throws Exception {
        LOG.trace("========== tearDown " + getTestNameWithClass() + " ==========");
        stopBroker();
    }

    protected String getTestName() {
        return name.getMethodName();
    }

    protected String getTestNameWithClass() {
        return getClass().getSimpleName() + "." + name.getMethodName();
    }

    protected boolean isAnonymousAccessAllowed() {
        return true;
    }

    protected String getAmqpTransformer() {
        return "jms";
    }

    protected int getSocketBufferSize() {
        return 64 * 1024;
    }

    protected int getIOBufferSize() {
        return 8 * 1024;
    }

    public int getBrokerAmqpConnectorPort() {
        if (brokerService == null || !brokerService.isStarted()) {
            throw new IllegalStateException("Broker must be started first.");
        }

        try {
            return brokerService.getTransportConnectorByName(AMQP_CONNECTOR_NAME).getPublishableConnectURI().getPort();
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    public void startBroker() throws Exception {
        if (brokerService != null && brokerService.isStarted()) {
            throw new IllegalStateException("Broker already started.");
        }

        brokerService = createBroker("localhost", true);
        brokerService.start();
        brokerService.waitUntilStarted();
    }

    public void stopBroker() throws Exception {
        stopBroker(brokerService);
    }

    public void restartBroker() throws Exception {
        stopBroker(brokerService);
        brokerService = restartBroker(brokerService);
    }

    protected BrokerService createBroker(String name, boolean deleteMessagesOnStartup) throws Exception {
        return createBroker(name, deleteMessagesOnStartup, Collections.<String, Integer> emptyMap());
    }

    protected BrokerService createBroker(String name, boolean deleteMessagesOnStartup, Map<String, Integer> portMap) throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setBrokerName(name);
        brokerService.setDeleteAllMessagesOnStartup(deleteMessagesOnStartup);
        brokerService.setUseJmx(true);
        brokerService.getManagementContext().setCreateConnector(false);
        brokerService.setDataDirectory(DATA_PARENT_DIR + File.separator + "data"+ File.separator + name);
        brokerService.setPersistent(false);
        brokerService.setSchedulerSupport(false);
        brokerService.setAdvisorySupport(false);

        ArrayList<BrokerPlugin> plugins = new ArrayList<BrokerPlugin>();
        BrokerPlugin authenticationPlugin = configureAuthentication();
        if (authenticationPlugin != null) {
            plugins.add(authenticationPlugin);
        }

        if (!plugins.isEmpty()) {
            brokerService.setPlugins(plugins.toArray(new BrokerPlugin[0]));
        }

        addAdditionalConnectors(brokerService, portMap);

        return brokerService;
    }


    public void stopBroker(BrokerService broker) throws Exception {
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    public BrokerService restartBroker(BrokerService brokerService) throws Exception {
        String name = brokerService.getBrokerName();
        Map<String, Integer> portMap = new HashMap<String, Integer>();
        for (TransportConnector connector : brokerService.getTransportConnectors()) {
            portMap.put(connector.getName(), connector.getPublishableConnectURI().getPort());
        }

        stopBroker(brokerService);
        BrokerService broker = createBroker(name, false, portMap);
        broker.start();
        broker.waitUntilStarted();
        return broker;
    }

    // Subclasses can override to add/restrict to their own connectors.
    protected void addAdditionalConnectors(BrokerService brokerService, Map<String, Integer> portMap) throws Exception {
        int port = PORT;
        if (portMap.containsKey(AMQP_CONNECTOR_NAME)) {
            port = portMap.get(AMQP_CONNECTOR_NAME);
        }
        TransportConnector connector = brokerService.addConnector(
            "amqp://0.0.0.0:" + port +
            "?transport.transformer=" + getAmqpTransformer() +
            "&transport.socketBufferSize=" + getSocketBufferSize() +
            "&ioBufferSize=" + getIOBufferSize());
        connector.setName(AMQP_CONNECTOR_NAME);

        port = connector.getPublishableConnectURI().getPort();
        LOG.debug("Using amqp port: {}", port);
    }

    // Subclasses can override with their own authentication config
    protected BrokerPlugin configureAuthentication() throws Exception {
        List<AuthenticationUser> users = new ArrayList<AuthenticationUser>();
        users.add(new AuthenticationUser(USERNAME_ADMIN, PASSWORD_ADMIN, "users,admins"));
        users.add(new AuthenticationUser(USERNAME_USER, PASSWORD_USER, "users"));
        users.add(new AuthenticationUser(USERNAME_GUEST, PASSWORD_GUEST, "guests"));
        SimpleAuthenticationPlugin authenticationPlugin = new SimpleAuthenticationPlugin(users);
        authenticationPlugin.setAnonymousAccessAllowed(isAnonymousAccessAllowed());

        return authenticationPlugin;
    }
}
