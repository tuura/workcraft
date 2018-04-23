we = load('vme-tm.circuit.work');

setConfigVar("CircuitSettings.gateLibrary", "libraries/workcraft.lib");
setConfigVar("CircuitSettings.substitutionLibrary", "");
exportCircuitVerilog(we, 'vme-tm.circuit.v');

exportSvg(we, 'vme-tm.circuit.svg');
exportPng(we, 'vme-tm.circuit.png');
exportPdf(we, 'vme-tm.circuit.pdf');
exportEps(we, 'vme-tm.circuit.eps');
exportPs(we, 'vme-tm.circuit.ps');

exit();
