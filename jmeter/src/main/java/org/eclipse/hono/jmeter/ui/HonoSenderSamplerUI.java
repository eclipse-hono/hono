/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.jmeter.ui;

import java.util.stream.Stream;

import javax.swing.JCheckBox;

import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.gui.JLabeledChoice;
import org.apache.jorphan.gui.JLabeledTextArea;
import org.apache.jorphan.gui.JLabeledTextField;
import org.eclipse.hono.jmeter.HonoSampler;
import org.eclipse.hono.jmeter.HonoSenderSampler;

/**
 * Swing UI for sender sampler.
 */
public class HonoSenderSamplerUI extends HonoSamplerUI {

    private static final long serialVersionUID = -2578458579696056223L;

    private final JLabeledTextField  deviceId;
    private final JCheckBox          setSenderTime;
    private final JCheckBox          waitForCredits;
    private final JLabeledTextField  contentType;
    private final JLabeledTextArea   data;
    private final JLabeledTextField  assertion;
    private final JLabeledTextField  waitForReceivers;
    private final JLabeledTextField  waitForReceiversTimeout;
    private final ServerOptionsPanel registrationServiceOptions;
    private final JLabeledTextField  tenant;
    private final JLabeledTextField  container;
    private final JLabeledChoice     endpoint;
    private final ServerOptionsPanel honoServerOptions;

    /**
     * Creates a new UI that provides means to configure
     * the southbound Telemetry &amp; Event API endpoint to connect to
     * for sending messages and an (optional) Device Registration service
     * endpoint for retrieving registration assertions.
     */
    public HonoSenderSamplerUI() {

        honoServerOptions = new ServerOptionsPanel("Telemetry & Event Endpoint");
        tenant = new JLabeledTextField("Tenant");
        container = new JLabeledTextField("Name");
        endpoint = new JLabeledChoice("Endpoint",
                Stream.of(HonoSampler.Endpoint.values()).map(HonoSampler.Endpoint::name).toArray(String[]::new));
        endpoint.setToolTipText("<html>The name of the endpoint to send the AMQP message to.</html>");
        deviceId = new JLabeledTextField("Device ID");
        deviceId.setToolTipText("<html>The device identifier to put into the <em>device_id</em> application property of the AMQP message to send.</html>");
        registrationServiceOptions = new ServerOptionsPanel("Device Registration Service");
        assertion = new JLabeledTextField("Registration Assertion");
        contentType = new JLabeledTextField("Content type");
        data = new JLabeledTextArea("Message data");
        waitForCredits = new JCheckBox("Wait for credits");
        setSenderTime = new JCheckBox("Set sender time in property");
        setSenderTime.setToolTipText(new StringBuilder()
                .append("<html>")
                .append("When checked, the messages being sent will contain a timestamp (millis since epoch start) ")
                .append("in the <em>timeStamp</em> application property.")
                .append("</html>")
                .toString());
        waitForReceivers = new JLabeledTextField(
                "Number of receivers to wait for (e.g. from other threads)");
        waitForReceiversTimeout = new JLabeledTextField(
                "Max time (millis) to wait for receivers");

        addOption(honoServerOptions);
        addOption(tenant);
        addOption(container);
        addOption(endpoint);
        addOption(deviceId);
        addOption(contentType);
        addOption(data);
        addOption(assertion);
        addOption(registrationServiceOptions);
        addOption(waitForCredits);
        addOption(setSenderTime);
        addOption(waitForReceivers);
        addOption(waitForReceiversTimeout);
    }

    @Override
    public String getStaticLabel() {
        return "Hono Sender Sampler";
    }

    @Override
    public TestElement createTestElement() {
        HonoSenderSampler sampler = new HonoSenderSampler();
        modifyTestElement(sampler);
        return sampler;
    }

    @Override
    public void modifyTestElement(final TestElement testElement) {

        super.configureTestElement(testElement);
        final HonoSenderSampler sampler = (HonoSenderSampler) testElement;
        sampler.modifyServerOptions(honoServerOptions);
        sampler.setEndpoint(HonoSampler.Endpoint.valueOf(endpoint.getText()));
        sampler.setTenant(tenant.getText());
        sampler.setContainer(container.getText());
        sampler.setDeviceId(deviceId.getText());
        sampler.setSetSenderTime(setSenderTime.isSelected());
        sampler.setWaitForCredits(waitForCredits.isSelected());
        sampler.setWaitForReceivers(waitForReceivers.getText());
        sampler.setWaitForReceiversTimeout(waitForReceiversTimeout.getText());
        sampler.setContentType(contentType.getText());
        sampler.setData(data.getText());
        sampler.setRegistrationAssertion(assertion.getText());
        // device registration service
        sampler.modifyRegistrationServiceOptions(registrationServiceOptions);
    }

    @Override
    public void configure(final TestElement element) {
        super.configure(element);
        final HonoSenderSampler sampler = (HonoSenderSampler) element;
        sampler.configureServerOptions(honoServerOptions);
        endpoint.setText(sampler.getEndpoint());
        tenant.setText(sampler.getTenant());
        container.setText(sampler.getContainer());
        deviceId.setText(sampler.getDeviceId());
        waitForReceivers.setText(sampler.getWaitForReceivers());
        waitForReceiversTimeout.setText(sampler.getWaitForReceiversTimeout());
        setSenderTime.setSelected(sampler.isSetSenderTime());
        waitForCredits.setSelected(sampler.isWaitForCredits());
        contentType.setText(sampler.getContentType());
        data.setText(sampler.getData());
        assertion.setText(sampler.getRegistrationAssertion());
        // device registration service
        sampler.configureRegistrationServiceOptions(registrationServiceOptions);
    }

    @Override
    public void clearGui() {
        super.clearGui();
        honoServerOptions.clearGui();
        endpoint.setSelectedIndex(0);
        tenant.setText("");
        container.setText("");
        deviceId.setText("");
        setSenderTime.setSelected(true);
        contentType.setText("text/plain");
        data.setText("");
        assertion.setText("");
        waitForCredits.setSelected(true);
        waitForReceivers.setText("0");
        waitForReceiversTimeout.setText("5000");
        // device registration service
        registrationServiceOptions.clearGui();
    }
}
