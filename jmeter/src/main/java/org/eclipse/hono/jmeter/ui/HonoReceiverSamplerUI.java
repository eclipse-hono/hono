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

package org.eclipse.hono.jmeter.ui;

import java.awt.BorderLayout;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

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
    private final JRadioButton       senderTimeInProperty;
    private final JRadioButton       senderTimeInPayload;
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
        senderTimeInProperty = new JRadioButton("Sender time in property");
        senderTimeInProperty.setToolTipText("<html>If set, the sending time is retrieved from the message's application property <em>timeStamp</em>.</html>");
        senderTimeInPayload = new JRadioButton("Sender time in JSON payload");
        final ButtonGroup group = new ButtonGroup();
        group.add(senderTimeInProperty);
        group.add(senderTimeInPayload);
        senderTimeInProperty.setSelected(true);
        senderTimeVariableName = new JLabeledTextField("JSON value key");
        senderTimeVariableName.setEnabled(false);
        prefetch = new JLabeledTextField("Prefetch");
        reconnectAttempts = new JLabeledTextField("Max reconnect attempts");

        addOption(honoServerOptions);
        addOption(tenant);
        addOption(container);
        addOption(getWrapperPanelToFixAlignment(endpoint));
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
        final HonoReceiverSampler sampler = new HonoReceiverSampler();
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
        final JRadioButton senderTimeButtonToSelect = sampler.isSenderTimeInPayload() ? senderTimeInPayload : senderTimeInProperty;
        senderTimeButtonToSelect.setSelected(true);
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
        senderTimeInProperty.setSelected(true);
        senderTimeVariableName.setText("timeStamp");
    }

    private JPanel createTimeStampPanel() {
        final JPanel timeStampPanel = new VerticalPanel();
        timeStampPanel.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Timestamp used for 'elapsed time' value"));

        final JPanel senderTimeFromPayloadPanel = new JPanel(new BorderLayout(10, 0));
        senderTimeFromPayloadPanel.add(senderTimeInPayload, BorderLayout.WEST);
        senderTimeFromPayloadPanel.add(senderTimeVariableName, BorderLayout.CENTER);

        final JPanel senderTimeOptionsPanel = new JPanel(new BorderLayout());
        senderTimeOptionsPanel.add(senderTimeInProperty, BorderLayout.WEST);
        senderTimeOptionsPanel.add(senderTimeFromPayloadPanel, BorderLayout.CENTER);

        timeStampPanel.add(useSenderTime);
        timeStampPanel.add(senderTimeOptionsPanel);

        useSenderTime.addChangeListener(e -> {
            if (e.getSource() == useSenderTime) {
                senderTimeOptionsPanel.setVisible(useSenderTime.isSelected());
            }
        });
        senderTimeInPayload.addChangeListener(e -> {
            if (e.getSource() == senderTimeInPayload) {
                senderTimeVariableName.setEnabled(senderTimeInPayload.isSelected());
            }
        });
        return timeStampPanel;
    }
}
