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
import java.awt.Color;
import java.awt.Insets;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.gui.JLabeledTextArea;
import org.apache.jorphan.gui.JLabeledTextField;
import org.eclipse.hono.jmeter.HonoCommanderSampler;

/**
 * Swing UI for Commander sampler.
 */
public class HonoCommanderSamplerUI extends HonoSamplerUI {

    private final ServerOptionsPanel honoServerOptions;
    private final JLabeledTextField tenant;
    private final JLabeledTextField reconnectAttempts;
    private final JLabeledTextField commandTimeOut;
    private final JLabeledTextArea commandPayload;
    private final JLabeledTextArea command;
    private final JComboBox triggerType;
    private final JTextArea triggerTypeDescription;

    /**
     * Creates a new UI that provides means to configure the Command &amp; Control endpoint to connect to for sending
     * commands and receiving command responses.
     */
    public HonoCommanderSamplerUI() {
        honoServerOptions = new ServerOptionsPanel("Hono connection options");
        reconnectAttempts = new JLabeledTextField("Max reconnect attempts");
        tenant = new JLabeledTextField("Tenant");
        command = new JLabeledTextArea("Command");
        commandPayload = new JLabeledTextArea("Command payload");
        commandTimeOut = new JLabeledTextField("Command Timeout In Milliseconds");
        triggerType = new JComboBox(new String[] { "device", "sampler" });
        triggerTypeDescription = new JTextArea();
        addOption(honoServerOptions);
        addOption(reconnectAttempts);
        addOption(tenant);
        addOption(command);
        addOption(commandPayload);
        addOption(commandTimeOut);
        addOption(getTriggerTypePanel());
    }

    @Override
    public String getStaticLabel() {
        return "Hono Commander Sampler";
    }

    @Override
    public TestElement createTestElement() {
        final HonoCommanderSampler sampler = new HonoCommanderSampler();
        modifyTestElement(sampler);
        return sampler;
    }

    @Override
    public void modifyTestElement(final TestElement testElement) {
        super.configureTestElement(testElement);
        final HonoCommanderSampler sampler = (HonoCommanderSampler) testElement;
        sampler.modifyServerOptions(honoServerOptions);
        sampler.setReconnectAttempts(reconnectAttempts.getText());
        sampler.setTenant(tenant.getText());
        sampler.setCommandTimeout(commandTimeOut.getText());
        sampler.setCommand(command.getText());
        sampler.setCommandPayload(commandPayload.getText());
        sampler.setTriggerType(((String) triggerType.getSelectedItem()));
    }

    @Override
    public void configure(final TestElement testElement) {
        super.configure(testElement);
        final HonoCommanderSampler sampler = (HonoCommanderSampler) testElement;
        sampler.configureServerOptions(honoServerOptions);
        reconnectAttempts.setText(sampler.getReconnectAttempts());
        tenant.setText(sampler.getTenant());
        commandTimeOut.setText(sampler.getCommandTimeout());
        command.setText(sampler.getCommand());
        commandPayload.setText(sampler.getCommandPayload());
        triggerType.setSelectedItem(sampler.getTriggerType());
        triggerTypeDescription.setText(getDescriptionTextForSelectedTrigger());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        honoServerOptions.clearGui();
        reconnectAttempts.setText("");
        tenant.setText("");
        commandTimeOut.setText("");
        command.setText("");
        commandPayload.setText("");
        triggerType.setSelectedIndex(0);
        triggerTypeDescription.setText(getDescriptionTextForSelectedTrigger());
    }

    private JPanel getTriggerTypePanel() {
        final JPanel triggerOuterPanel = new VerticalPanel(50, 0.0F);
        triggerOuterPanel.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Commands triggered by"));
        final JPanel triggerPanel = new JPanel(new BorderLayout(10, 50));
        setDescriptionTextAreaDesign(triggerTypeDescription, triggerPanel);
        triggerPanel.add(triggerType, BorderLayout.LINE_START);
        triggerPanel.add(triggerTypeDescription, BorderLayout.CENTER);
        triggerType.addActionListener(e -> triggerTypeDescription.setText(getDescriptionTextForSelectedTrigger()));
        triggerOuterPanel.add(triggerPanel);
        return triggerOuterPanel;
    }

    private void setDescriptionTextAreaDesign(final JTextArea jTextArea, final JPanel parentPanel) {
        jTextArea.setLineWrap(true);
        jTextArea.setWrapStyleWord(true);
        jTextArea.setEditable(false);
        jTextArea.setMargin(new Insets(15, 15, 15, 15));
        jTextArea.setBackground(parentPanel.getBackground());
        jTextArea.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
    }

    private String getDescriptionTextForSelectedTrigger() {
        if (Objects.requireNonNull(triggerType.getSelectedItem()).equals("device")) {
            return "Devices notify hono that they are ready to receive commands. Immediately after receiving this notification, the sampler sends a command (one command per notification). During each sampling interval, no. of commands sent, errors etc are captured by the sampler. \n\nExample Scenario(s): \n1. To send commands using HTTP, the devices always initiate by sending messages with hono-ttd value. \n2. Devices connected for a short duration using mqtt. After subscription, devices expect commands from application if any, then unsubscribe.";
        } else {
            return "It is assumed that the devices are ready to receive commands throughout the test time. One Command per sampling interval is sent by the sampler to all subscribed devices. \n\nExample scenario:\nUsing Mqtt, devices subscribe for commands and stay connected for a long time. An Application can send any number of commands and receive responses during this time.\n";
        }
    }
}
