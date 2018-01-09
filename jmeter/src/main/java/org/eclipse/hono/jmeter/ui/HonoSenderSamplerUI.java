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

import javax.swing.JCheckBox;

import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.gui.JLabeledTextArea;
import org.apache.jorphan.gui.JLabeledTextField;
import org.eclipse.hono.jmeter.HonoSenderSampler;

/**
 * Swing UI for sender sampler
 */
public class HonoSenderSamplerUI extends HonoSamplerUI {

    private final JLabeledTextField      deviceId                = new JLabeledTextField("DeviceID");
    private final JCheckBox              setSenderTime           = new JCheckBox("Set sender time in property");
    private final JCheckBox              waitForCredits          = new JCheckBox("Wait for credits");
    private final JLabeledTextField      contentType             = new JLabeledTextField("Content type");
    private final JLabeledTextArea       data                    = new JLabeledTextArea("Message data");
    private final JLabeledTextField      waitForReceivers        = new JLabeledTextField(
            "Wait on n active receivers in VM (e.g. from other threads)");
    private final JLabeledTextField      waitForReceiversTimeout = new JLabeledTextField(
            "Timeout for the wait on receivers (millis)");
    private final HonoServerOptionsPanel registryServerOptions   = new HonoServerOptionsPanel("Hono Device Registry");

    public HonoSenderSamplerUI() {
        super("Hono Server");
        addOption(registryServerOptions);
        addDefaultOptions();
        addOption(deviceId);
        addOption(waitForCredits);
        addOption(setSenderTime);
        addOption(waitForReceivers);
        addOption(waitForReceiversTimeout);
        addOption(contentType);
        addOption(data);
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
        super.modifyTestElement(testElement);
        HonoSenderSampler sampler = (HonoSenderSampler) testElement;
        sampler.setDeviceId(deviceId.getText());
        sampler.setSetSenderTime(setSenderTime.isSelected());
        sampler.setWaitForCredits(waitForCredits.isSelected());
        sampler.setWaitForReceivers(waitForReceivers.getText());
        sampler.setWaitForReceiversTimeout(waitForReceiversTimeout.getText());
        sampler.setContentType(contentType.getText());
        sampler.setData(data.getText());
        // registry server
        sampler.setRegistryHost(registryServerOptions.getHost().getText());
        sampler.setRegistryPort(registryServerOptions.getPort().getText());
        sampler.setRegistryUser(registryServerOptions.getUser().getText());
        sampler.setRegistryPwd(registryServerOptions.getPwd().getText());
        sampler.setRegistryTrustStorePath(registryServerOptions.getTrustStorePath().getText());
    }

    @Override
    public void configure(final TestElement element) {
        super.configure(element);
        HonoSenderSampler sampler = (HonoSenderSampler) element;
        deviceId.setText(sampler.getDeviceId());
        waitForReceivers.setText(sampler.getWaitForReceivers());
        waitForReceiversTimeout.setText(sampler.getWaitForReceiversTimeout());
        setSenderTime.setSelected(sampler.isSetSenderTime());
        waitForCredits.setSelected(sampler.isWaitForCredits());
        contentType.setText(sampler.getContentType());
        data.setText(sampler.getData());
        // registry server
        registryServerOptions.getHost().setText(sampler.getRegistryHost());
        registryServerOptions.getPort().setText(sampler.getRegistryPort());
        registryServerOptions.getUser().setText(sampler.getRegistryUser());
        registryServerOptions.getPwd().setText(sampler.getRegistryPwd());
        registryServerOptions.getTrustStorePath().setText(sampler.getRegistryTrustStorePath());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        deviceId.setText("");
        setSenderTime.setSelected(true);
        contentType.setText("text/plain");
        data.setText("");
        waitForCredits.setSelected(true);
        waitForReceivers.setText("0");
        waitForReceiversTimeout.setText("5000");
        // registry server
        registryServerOptions.clearGui();
    }

}
