package org.workcraft.dom.visual;

import org.workcraft.dom.Node;
import org.workcraft.observation.TransformObserver;

public interface TransformDispatcher {
    void subscribe(TransformObserver observer, Node observed);
    void unsubscribe(TransformObserver observer, Node observed);
}
