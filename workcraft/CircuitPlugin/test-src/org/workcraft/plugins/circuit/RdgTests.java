package org.workcraft.plugins.circuit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.workcraft.Framework;
import org.workcraft.dom.visual.VisualPage;
import org.workcraft.exceptions.DeserialisationException;
import org.workcraft.plugins.circuit.refinement.RdgToPetriConverter;
import org.workcraft.plugins.circuit.refinement.RefinementDependencyGraph;
import org.workcraft.plugins.petri.VisualPetri;
import org.workcraft.utils.DirectedGraphUtils;
import org.workcraft.utils.Hierarchy;
import org.workcraft.utils.PackageUtils;
import org.workcraft.workspace.WorkspaceEntry;

import java.awt.*;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

class RdgTests {

    @BeforeAll
    static void init() {
        final Framework framework = Framework.getInstance();
        framework.init();
    }

    @Test
    void testAcyclicRdg() throws DeserialisationException {
        String workName = PackageUtils.getPackagePath(getClass(), "rdg-acyclic/top.circuit.work");
        testRdg(workName, 4, 6, 4, true);
    }

    @Test
    void testCyclicRdg() throws DeserialisationException {
        String workName = PackageUtils.getPackagePath(getClass(), "rdg-cyclic/top.circuit.work");
        testRdg(workName, 4, 6, 5, false);
    }

    private void testRdg(String workName, int vertexCount, int edgeCount, int metaEdgeCount, boolean noCycles)
            throws DeserialisationException {

        final Framework framework = Framework.getInstance();
        final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        URL srcUrl = classLoader.getResource(workName);

        WorkspaceEntry srcWe = framework.loadWork(srcUrl.getFile());

        RefinementDependencyGraph rdg = new RefinementDependencyGraph(srcWe);
        framework.closeWork(srcWe);

        Assertions.assertEquals(vertexCount, rdg.getVertices().size());
        Assertions.assertEquals(edgeCount, rdg.getVertices().stream()
                .map(rdg::getInstanceDependencyMap)
                .mapToInt(Map::size)
                .sum());

        Map<File, Set<File>> graph = rdg.getSimpleGraph();
        Assertions.assertEquals(vertexCount, graph.size());
        Assertions.assertEquals(metaEdgeCount, rdg.getVertices().stream()
                .mapToInt(f -> rdg.getDependencies(f).size())
                .sum());

        RdgToPetriConverter converter = new RdgToPetriConverter(rdg);
        VisualPetri petri = converter.getPetri();
        Assertions.assertEquals(vertexCount, Hierarchy.getDescendantsOfType(petri.getRoot(), VisualPage.class).size());
        Assertions.assertEquals(vertexCount, petri.getVisualTransitions().size());

        int placeCount = noCycles ? edgeCount + 1 : edgeCount;
        Assertions.assertEquals(placeCount, petri.getVisualPlaces().size());

        Assertions.assertEquals(edgeCount, petri.getVisualPlaces().stream()
                .filter(place -> petri.getPreset(place).size() == 1)
                .count());

        Set<List<File>> simpleCycles = DirectedGraphUtils.findSimpleCycles(graph);
        Assertions.assertEquals(noCycles, simpleCycles.isEmpty());
        if (!simpleCycles.isEmpty()) {
            converter.highlightCycle(simpleCycles.iterator().next(), Color.RED);
            boolean hasColoredPlaces = petri.getVisualPlaces().stream()
                    .anyMatch(place -> Color.RED.equals(place.getForegroundColor()));

            boolean hasColoredTransitions = petri.getVisualTransitions().stream()
                    .anyMatch(place -> Color.RED.equals(place.getForegroundColor()));

            Assertions.assertEquals(noCycles, !hasColoredPlaces && !hasColoredTransitions);
        }
    }

}
