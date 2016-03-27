package org.workcraft.plugins.cpog.tasks;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.workcraft.Framework;
import org.workcraft.dom.VisualModelDescriptor;
import org.workcraft.dom.math.MathModel;
import org.workcraft.exceptions.VisualModelInstantiationException;
import org.workcraft.gui.MainWindow;
import org.workcraft.gui.ToolboxPanel;
import org.workcraft.gui.graph.GraphEditorPanel;
import org.workcraft.gui.workspace.Path;
import org.workcraft.plugins.cpog.CpogDescriptor;
import org.workcraft.plugins.cpog.VisualCPOG;
import org.workcraft.plugins.cpog.tools.CpogSelectionTool;
import org.workcraft.plugins.shared.tasks.ExternalProcessResult;
import org.workcraft.tasks.DummyProgressMonitor;
import org.workcraft.tasks.Result;
import org.workcraft.tasks.Result.Outcome;
import org.workcraft.util.FileUtils;
import org.workcraft.workspace.ModelEntry;
import org.workcraft.workspace.Workspace;
import org.workcraft.workspace.WorkspaceEntry;

public class PGMinerResultHandler extends DummyProgressMonitor<ExternalProcessResult> {

    private VisualCPOG visualCpog;
    private WorkspaceEntry we;
    private boolean createNewWindow;
    private File outputFile;

    public PGMinerResultHandler(VisualCPOG visualCpog, WorkspaceEntry we, boolean createNewWindow, File outputFile) {
        this.visualCpog = visualCpog;
        this.we = we;
        this.createNewWindow = createNewWindow;
        this.outputFile = outputFile;
    }

    public void finished(final Result<? extends ExternalProcessResult> result, String description) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    final Framework framework = Framework.getInstance();
                    MainWindow mainWindow = framework.getMainWindow();
                    final GraphEditorPanel editor = framework.getMainWindow().getCurrentEditor();
                    final ToolboxPanel toolbox = editor.getToolBox();
                    final CpogSelectionTool tool = toolbox.getToolInstance(CpogSelectionTool.class);
                    if (result.getOutcome() == Outcome.FAILED) {
                        JOptionPane.showMessageDialog(mainWindow, "PGMiner could not run", "Concurrency extraction failed", JOptionPane.ERROR_MESSAGE);
                    } else {
                        if (createNewWindow) {
                            CpogDescriptor cpogModel = new CpogDescriptor();
                            MathModel mathModel = cpogModel.createMathModel();
                            Path<String> path = we.getWorkspacePath();
                            VisualModelDescriptor v = cpogModel.getVisualModelDescriptor();
                            try {
                                if (v == null) {
                                    throw new VisualModelInstantiationException("visual model is not defined for \"" + cpogModel.getDisplayName() + "\".");
                                }
                                visualCpog = (VisualCPOG) v.create(mathModel);
                                final Workspace workspace = framework.getWorkspace();
                                final Path<String> directory = workspace.getPath(we).getParent();
                                final String name = FileUtils.getFileNameWithoutExtension(new File(path.getNode()));
                                final ModelEntry me = new ModelEntry(cpogModel, visualCpog);
                                workspace.add(directory, name, me, true, true);
                            } catch (VisualModelInstantiationException e) {
                                e.printStackTrace();
                            }
                        }

                        we.captureMemento();


                        if (tool.insertCpogFromFile(outputFile)) {
                            we.saveMemento();
                        } else {
                            we.cancelMemento();
                        }

                    }
                }
            });
        } catch (InvocationTargetException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}

