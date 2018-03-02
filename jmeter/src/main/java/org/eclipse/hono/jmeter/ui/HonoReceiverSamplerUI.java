/**
 * Copyright (c) 2016,2017 Bosch Software Innovations GmbH.
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

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.gui.JLabeledChoice;
import org.apache.jorphan.gui.JLabeledTextField;
import org.eclipse.hono.jmeter.HonoReceiverSampler;
import org.eclipse.hono.jmeter.HonoSampler;

/**
 * Swing UI for receiver sampler.
 */
public class HonoReceiverSamplerUI extends HonoSamplerUI {

    private static final long serialVersionUID = 2577635965483186422L;

    private final JCheckBox          useSenderTime;
    private final JCheckBox          senderTimeInPayload;
    private final JLabeledTextField  senderTimeVariableName;
    private final JLabeledTextField  prefetch;
    private final JLabeledTextField  reconnectAttempts;
    private final JLabeledTextField  tenant;
    private final JLabeledTextField  container;
    private final JLabeledChoice     endpoint;
    private final ServerOptionsPanel honoServerOptions;

    /**
     * Creates a new UI that provides means to configure
     * the northbound Telemetry &amp; Event API endpoint to connect to
     * for receiving messages.
     */
    public HonoReceiverSamplerUI() {

        honoServerOptions = new ServerOptionsPanel("Telemetry & Event Endpoint");
        tenant = new JLabeledTextField("Tenant");
        container = new JLabeledTextField("Name");
        endpoint = new JLabeledChoice("Endpoint",
                Stream.of(HonoSampler.Endpoint.values()).map(HonoSampler.Endpoint::name).toArray(String[]::new));
        endpoint.setToolTipText("<html>The name of the endpoint to send the AMQP message to.</html>");
        useSenderTime = new JCheckBox("Use sender time");
        useSenderTime.setToolTipText(new StringBuilder()
                .append("<html>")
                .append("When checked, the sending time of received messages will be determined from information")
                .append(" contained in the messages as follows:")
                .append("<ul>")
                .append("<li>If <em>Sender time in Payload</em> is unchecked, the sending time is retrieved from the ")
                .append("message's application property <em>timeStamp</em>.</li>")
                .append("<li>Otherwise, it will be retrieved from the message's JSON payload using the property name ")
                .append("set in the <em>Sender time variable name</em>.</li>")
                .append("</ul>")
                .append("</html>")
                .toString());
        senderTimeInPayload = new JCheckBox("Sender time in Payload");
        senderTimeVariableName = new JLabeledTextField("Sender time variable name");
        prefetch = new JLabeledTextField("Prefetch");
        reconnectAttempts = new JLabeledTextField("Max reconnect attempts");

        addOption(honoServerOptions);
        addOption(tenant);
        addOption(container);
        addOption(endpoint);
        addOption(prefetch);
        addOption(reconnectAttempts);
        addOption(createTimeStampPanel());
    }

    @Override
    public String getStaticLabel() {
        return "Hono Receiver Sampler";
    }

    @Override
    public TestElement createTestElement() {
        HonoReceiverSampler sampler = new HonoReceiverSampler();
        modifyTestElement(sampler);
        return sampler;
    }

    @Override
    public void modifyTestElement(final TestElement testElement) {

        super.configureTestElement(testElement);
        final HonoReceiverSampler sampler = (HonoReceiverSampler) testElement;
        sampler.modifyServerOptions(honoServerOptions);
        sampler.setEndpoint(HonoSampler.Endpoint.valueOf(endpoint.getText()));
        sampler.setTenant(tenant.getText());
        sampler.setContainer(container.getText());

        sampler.setPrefetch(prefetch.getText());
        sampler.setReconnectAttempts(reconnectAttempts.getText());
        sampler.setUseSenderTime(useSenderTime.isSelected());
        sampler.setSenderTimeInPayload(senderTimeInPayload.isSelected());
        sampler.setSenderTimeVariableName(senderTimeVariableName.getText());
    }

    @Override
    public void configure(final TestElement element) {
        super.configure(element);
        final HonoReceiverSampler sampler = (HonoReceiverSampler) element;
        sampler.configureServerOptions(honoServerOptions);
        endpoint.setText(sampler.getEndpoint());
        tenant.setText(sampler.getTenant());
        container.setText(sampler.getContainer());
        prefetch.setText(sampler.getPrefetch());
        reconnectAttempts.setText(sampler.getReconnectAttempts());
        useSenderTime.setSelected(sampler.isUseSenderTime());
        senderTimeInPayload.setSelected(sampler.isSenderTimeInPayload());
        senderTimeVariableName.setText(sampler.getSenderTimeVariableName());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        honoServerOptions.clearGui();
        endpoint.setSelectedIndex(0);
        tenant.setText("");
        container.setText("");
        reconnectAttempts.setText("0");
        prefetch.setText("50");
        useSenderTime.setSelected(false);
        senderTimeInPayload.setSelected(false);
        senderTimeVariableName.setText("timeStamp");
    }

    private JPanel createTimeStampPanel() {
        final JPanel timeStampPanel = new VerticalPanel();
        timeStampPanel.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Timestamp used for sampling"));
        timeStampPanel.add(useSenderTime);
        timeStampPanel.add(senderTimeInPayload);
        timeStampPanel.add(senderTimeVariableName);
        useSenderTime.addChangeListener(e -> {
            if (e.getSource() == this.useSenderTime) {
                if (this.useSenderTime.isSelected()) {
                    senderTimeInPayload.setVisible(true);
                    senderTimeVariableName.setVisible(true);
                } else {
                    senderTimeInPayload.setVisible(false);
                    senderTimeVariableName.setVisible(false);
                }
            }
        });
        return timeStampPanel;
    }
}
