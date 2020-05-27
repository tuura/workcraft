package org.workcraft.plugins.circuit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.workcraft.Framework;
import org.workcraft.exceptions.DeserialisationException;
import org.workcraft.exceptions.SerialisationException;
import org.workcraft.plugins.builtin.interop.*;
import org.workcraft.plugins.builtin.settings.DebugCommonSettings;
import org.workcraft.plugins.circuit.interop.VerilogFormat;
import org.workcraft.utils.FileUtils;
import org.workcraft.utils.PackageUtils;
import org.workcraft.workspace.ModelEntry;
import org.workcraft.workspace.WorkspaceEntry;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class ExportTests {

    @BeforeAll
    public static void init() {
        final Framework framework = Framework.getInstance();
        framework.init();
        DebugCommonSettings.setShortExportHeader(true);
    }

    @Test
    public void testBufferExport() throws DeserialisationException, IOException, SerialisationException {
        String vHeader = String.format(
                "// Verilog netlist generated by Workcraft 3%n" +
                        "module buffer (out, in);%n" +
                        "    input in;%n" +
                        "    output out;%n");

        String svgHeader = String.format(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>%n" +
                        "<!DOCTYPE svg PUBLIC '-//W3C//DTD SVG 1.0//EN'%n" +
                        "          'http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd'>");

        String pngHeader = (char) 0xFFFD + "PNG";

        String pdfHeader = "%PDF-1.4";

        String epsHeader = "%!PS-Adobe-3.0 EPSF-3.0";

        String psHeader = "%!PS-Adobe-3.0";

        String workName = PackageUtils.getPackagePath(getClass(), "buffer-tm.circuit.work");
        testExport(workName, vHeader, svgHeader, pngHeader, pdfHeader, epsHeader, psHeader);
    }

    private void testExport(String workName, String vHeader,
            String svgHeader, String pngHeader, String pdfHeader, String epsHeader, String psHeader)
                    throws DeserialisationException, IOException, SerialisationException {

        final Framework framework = Framework.getInstance();
        final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        URL url = classLoader.getResource(workName);
        WorkspaceEntry we = framework.loadWork(url.getFile());
        ModelEntry me = we.getModelEntry();
        File directory = FileUtils.createTempDirectory(FileUtils.getTempPrefix(workName));

        File vFile = new File(directory, "export.v");
        framework.exportModel(me, vFile, VerilogFormat.getInstance());
        Assertions.assertEquals(vHeader, FileUtils.readHeaderUtf8(vFile, vHeader.length()));

        File svgFile = new File(directory, "export.svg");
        framework.exportModel(me, svgFile, SvgFormat.getInstance());
        Assertions.assertEquals(svgHeader, FileUtils.readHeaderUtf8(svgFile, svgHeader.length()));

        File pngFile = new File(directory, "export.png");
        framework.exportModel(me, pngFile, PngFormat.getInstance());
        Assertions.assertEquals(pngHeader, FileUtils.readHeaderUtf8(pngFile, pngHeader.length()));

        File pdfFile = new File(directory, "export.pdf");
        framework.exportModel(me, pdfFile, PdfFormat.getInstance());
        Assertions.assertEquals(pdfHeader, FileUtils.readHeaderUtf8(pdfFile, pdfHeader.length()));

        File epsFile = new File(directory, "export.eps");
        framework.exportModel(me, epsFile, EpsFormat.getInstance());
        Assertions.assertEquals(epsHeader, FileUtils.readHeaderUtf8(epsFile, epsHeader.length()));

        File psFile = new File(directory, "export.ps");
        framework.exportModel(me, psFile, PsFormat.getInstance());
        Assertions.assertEquals(psHeader, FileUtils.readHeaderUtf8(psFile, psHeader.length()));

        framework.closeWork(we);
        FileUtils.deleteOnExitRecursively(directory);
    }

}
