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

import javax.swing.*;

import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.gui.JLabeledTextField;
import org.eclipse.hono.client.impl.AbstractHonoClient;
import org.eclipse.hono.jmeter.HonoReceiverSampler;

/**
 * Swing UI for receiver sampler
 */
public class HonoReceiverSamplerUI extends HonoSamplerUI {

    private final JCheckBox         useSenderTime = new JCheckBox("Use sender time");
    private final JLabeledTextField prefetch      = new JLabeledTextField("Prefetch");

    public HonoReceiverSamplerUI() {
        super("Qpid Dispatch Router");
        addDefaultOptions();
        addOption(prefetch);
        addOption(useSenderTime);
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
        sampler.setUseSenderTime(useSenderTime.isSelected());
    }

    @Override
    public void configure(final TestElement element) {
        super.configure(element);
        HonoReceiverSampler sampler = (HonoReceiverSampler) element;
        prefetch.setText(sampler.getPrefetch());
        useSenderTime.setSelected(sampler.isUseSenderTime());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        prefetch.setText("50");
        useSenderTime.setSelected(false);
    }

}
