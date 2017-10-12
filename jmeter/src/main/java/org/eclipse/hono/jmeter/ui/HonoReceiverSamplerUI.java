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

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.gui.JLabeledTextField;
import org.eclipse.hono.jmeter.HonoReceiverSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Swing UI for receiver sampler
 */
public class HonoReceiverSamplerUI extends HonoSamplerUI {

    private static final long serialVersionUID = 2577635965483186422L;

    private static final Logger LOGGER = LoggerFactory.getLogger(HonoReceiverSamplerUI.class);

    private final JCheckBox         useSenderTime          = new JCheckBox("Use sender time");
    private final JCheckBox         senderTimeInPayload    = new JCheckBox("Sender time in Payload");
    private final JLabeledTextField senderTimeVariableName = new JLabeledTextField("Sender time variable name");
    private final JLabeledTextField prefetch               = new JLabeledTextField("Prefetch");
    private final JLabeledTextField reconnectAttempts      = new JLabeledTextField("Max reconnect attempts");

    public HonoReceiverSamplerUI() {
        super("Qpid Dispatch Router");
        addDefaultOptions();
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
        super.modifyTestElement(testElement);
        HonoReceiverSampler sampler = (HonoReceiverSampler) testElement;
        sampler.setPrefetch(prefetch.getText());
        sampler.setReconnectAttempts(reconnectAttempts.getText());
        sampler.setUseSenderTime(useSenderTime.isSelected());
        sampler.setSenderTimeInPayload(senderTimeInPayload.isSelected());
        sampler.setSenderTimeVariableName(senderTimeVariableName.getText());
    }

    @Override
    public void configure(final TestElement element) {
        super.configure(element);
        HonoReceiverSampler sampler = (HonoReceiverSampler) element;
        prefetch.setText(sampler.getPrefetch());
        reconnectAttempts.setText(sampler.getReconnectAttempts());
        useSenderTime.setSelected(sampler.isUseSenderTime());
        senderTimeInPayload.setSelected(sampler.isSenderTimeInPayload());
        senderTimeVariableName.setText(sampler.getSenderTimeVariableName());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        LOGGER.debug("clearGui() invoked");
        reconnectAttempts.setText("0");
        prefetch.setText("50");
        useSenderTime.setSelected(false);
        senderTimeInPayload.setSelected(false);
        senderTimeVariableName.setText("timeStamp");
    }

    private JPanel createTimeStampPanel() {
        LOGGER.debug("createTimeStampPanel() invoked");
        JPanel timeStampPanel = new VerticalPanel();
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
