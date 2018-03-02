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

import java.awt.BorderLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;

/**
 * Base class for implementing GUI components for Hono samplers.
 */
public abstract class HonoSamplerUI extends AbstractSamplerGui {

    private static final long serialVersionUID = 5233638873794953551L;

    private final JPanel optsPanel;

    /**
     * Creates a new GUI component.
     */
    public HonoSamplerUI() {

        setLayout(new BorderLayout());
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);
        optsPanel = new VerticalPanel();
        add(optsPanel, BorderLayout.CENTER);
    }

    /**
     * Adds a component for configuring an option to the GUI.
     * <p>
     * The component will be added to the bottom of the existing options panel.
     * 
     * @param component The component to add.
     */
    protected final void addOption(final JComponent component) {
        optsPanel.add(component);
    }

    @Override
    public String getLabelResource() {
        return null;
    }
}
