package org.workcraft.dom.hierarchy;

import org.workcraft.dom.Container;
import org.workcraft.dom.Node;
import org.workcraft.dom.math.MathModel;
import org.workcraft.dom.visual.VisualComponent;
import org.workcraft.dom.visual.VisualGroup;
import org.workcraft.dom.visual.VisualModel;
import org.workcraft.dom.visual.VisualPage;
import org.workcraft.utils.Hierarchy;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NamespaceHelper {

    private static final String HIERARCHY_SEPARATOR = ".";
    private static final String FLATNAME_SEPARATOR = "_";

    private static final Pattern HEAD_TAIL_PATTERN = Pattern.compile(
            "((.+?" + Pattern.quote(HIERARCHY_SEPARATOR) + ")|(.+?$))" + "(.*)");

    private static final int HEAD_GROUP = 1;
    private static final int TAIL_GROUP = 4;

    public static String convertLegacyHierarchySeparators(String ref) {
        // Use negative lookahead (?![0-9]) to make sure that hierarchy separator is not followed by a number.
        // This is because "/[0-9]" is used in in STG for transition instances.
        return ref.replaceAll("/(?![0-9])", HIERARCHY_SEPARATOR);
    }

    public static String getHierarchySeparator() {
        return HIERARCHY_SEPARATOR;
    }

    public static String flattenReference(String reference) {
        return reference.replaceAll(Pattern.quote(HIERARCHY_SEPARATOR), FLATNAME_SEPARATOR);
    }

    private static LinkedList<String> splitReference(String reference) {
        LinkedList<String> result = new LinkedList<>();
        if ((reference != null) && !reference.isEmpty()) {
            Matcher matcher = HEAD_TAIL_PATTERN.matcher(reference);
            if (matcher.find()) {
                String head = matcher.group(HEAD_GROUP);
                result.add(head);
                String tail = matcher.group(TAIL_GROUP);
                if (tail != null) {
                    result.addAll(splitReference(tail));
                }
            }
        }
        return result;
    }

    public static String getParentReference(String reference) {
        String result = "";
        LinkedList<String> path = splitReference(reference);
        for (int i = 0; i < path.size() - 1; i++) {
            result += path.get(i);
        }
        return result;
    }

    public static String getReferenceHead(String reference) {
        Matcher matcher = HEAD_TAIL_PATTERN.matcher(reference);
        return matcher.find() ? matcher.group(HEAD_GROUP) : reference;
    }

    public static String getReferenceTail(String reference) {
        String result = null;
        Matcher matcher = HEAD_TAIL_PATTERN.matcher(reference);
        if (matcher.find()) {
            result = matcher.group(TAIL_GROUP);
        }
        return (result == null) ? "" : result;
    }

    public static String getReferenceName(String reference) {
        String head = getReferenceHead(reference);
        String tail = getReferenceTail(reference);
        return tail.isEmpty() ? head : getReferenceName(tail);
    }

    public static String getReference(String parentReference, String name) {
        return (isRoot(parentReference) ? "" : parentReference) + name;
    }

    private static boolean isRoot(String parentReference) {
        return (parentReference == null) || parentReference.equals(getHierarchySeparator());
    }

    public static Container getMathContainer(VisualModel visualModel, Container visualContainer) {
        if (visualContainer == null) {
            visualContainer = visualModel.getRoot();
        }

        // Find the closest container that has a referenced math node.
        VisualComponent vis = null;
        if (visualContainer instanceof VisualComponent) {
            vis = (VisualComponent) visualContainer;
        } else {
            vis = Hierarchy.getNearestAncestor(visualContainer, VisualComponent.class);
        }

        // Get appropriate math container, it will be the target container for the math model.
        MathModel mmodel = visualModel.getMathModel();
        Container mathTargetContainer = mmodel.getRoot();
        if (vis != null) {
            mathTargetContainer = (Container) vis.getReferencedComponent();
        }
        return mathTargetContainer;
    }

    public static void copyPageStructure(VisualModel srcModel, VisualModel dstModel) {
        Container dstContainer = dstModel.getRoot();
        Container srcContainer = srcModel.getRoot();
        copyPageStructure(srcModel, srcContainer, dstModel, dstContainer);
    }

    private static void copyPageStructure(VisualModel srcModel, Container srcContainer,
            VisualModel dstModel, Container dstContainer) {

        HashMap<Container, Container> toProcess = new HashMap<>();
        for (Node srcNode: srcContainer.getChildren()) {
            if (srcNode instanceof VisualPage) {
                VisualPage srcPage = (VisualPage) srcNode;
                VisualPage dstPage = dstModel.createVisualPage(dstContainer);
                dstModel.setMathName(dstPage, srcModel.getMathName(srcPage));
                dstPage.copyPosition(srcPage);
                dstPage.copyStyle(srcPage);
                toProcess.put(srcPage, dstPage);
            } else if (srcNode instanceof VisualGroup) {
                VisualGroup srcGroup = (VisualGroup) srcNode;
                toProcess.put(srcGroup, dstContainer);
            }
        }

        for (Entry<Container, Container> en: toProcess.entrySet()) {
            copyPageStructure(srcModel, en.getKey(), dstModel, en.getValue());
        }
    }

    public static void removeEmptyPages(VisualModel model) {
        boolean progress;
        do {
            progress = false;
            for (VisualPage page: Hierarchy.getDescendantsOfType(model.getRoot(), VisualPage.class)) {
                if (page.getChildren().isEmpty()) {
                    Container container = Hierarchy.getNearestContainer(page);
                    model.setCurrentLevel(container);
                    model.select(page);
                    model.deleteSelection();
                    progress = true;
                }
            }
        } while (progress);
        model.selectNone();
        model.setCurrentLevel(model.getRoot());
    }

    public static HashMap<String, Container> getRefToPageMapping(VisualModel model) {
        HashMap<String, Container> result = new HashMap<>();
        Container root = model.getRoot();
        result.put("", root);
        for (VisualPage page: Hierarchy.getDescendantsOfType(root, VisualPage.class)) {
            String ref = model.getMathReference(page);
            result.put(ref, page);
        }
        return result;
    }

    public static boolean isHierarchical(String ref) {
        return (ref != null) && !ref.isEmpty() && ref.substring(1).contains(HIERARCHY_SEPARATOR);
    }

}
