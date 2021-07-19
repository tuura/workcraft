package org.workcraft.plugins.circuit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.workcraft.Framework;
import org.workcraft.dom.visual.VisualPage;
import org.workcraft.exceptions.DeserialisationException;
import org.workcraft.plugins.circuit.commands.SquashComponentTransformationCommand;
import org.workcraft.utils.Hierarchy;
import org.workcraft.utils.PackageUtils;
import org.workcraft.utils.WorkspaceUtils;
import org.workcraft.workspace.WorkspaceEntry;

import java.net.URL;
import java.util.Arrays;

class SquashTests {

    @BeforeAll
    static void init() {
        final Framework framework = Framework.getInstance();
        framework.init();
    }

    @Test
    void testAcyclicRdg() throws DeserialisationException {
        String workName = PackageUtils.getPackagePath(getClass(), "rdg-acyclic/top.circuit.work");

        final Framework framework = Framework.getInstance();
        final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        URL url = classLoader.getResource(workName);

        WorkspaceEntry we = framework.loadWork(url.getFile());
        VisualCircuit circuit = WorkspaceUtils.getAs(we, VisualCircuit.class);
        SquashComponentTransformationCommand command = new SquashComponentTransformationCommand();

        circuit.selectAll();
        command.run(we);
        Assertions.assertEquals(3, Hierarchy.getDescendantsOfType(circuit.getRoot(), VisualPage.class).size());

        VisualFunctionComponent g20 = circuit.getVisualComponentByMathReference("g2.g0", VisualFunctionComponent.class);
        circuit.addToSelection(Arrays.asList(g20));
        command.run(we);

        Assertions.assertEquals(4, Hierarchy.getDescendantsOfType(circuit.getRoot(), VisualPage.class).size());
        Assertions.assertEquals(3, circuit.getVisualFunctionComponents().size());
        Assertions.assertEquals(2, circuit.getVisualPorts().size());
        Assertions.assertEquals(8, circuit.getVisualFunctionContacts().size());
        framework.closeWork(we);
    }

}
