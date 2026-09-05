package com.yu1745.chemicaladdon.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlcProgramTest {
	@Test void assemblyReadsComputesAndCommitsOnlyWrittenOutputs(){PlcMachine m=new PlcMachine();PlcAssemblyProgram p=PlcAssemblyProgram.compile("GT R0 I0 7\nHYST R1 I1 5 10\nMOV O2 R0\nMOV O3 R1");m.beginScan(input(9,3));p.scan(m);m.finishScan();assertEquals(15,m.output(2));assertEquals(15,m.output(3));m.beginScan(input(0,12));p.scan(m);m.finishScan();assertEquals(0,m.output(2));assertEquals(0,m.output(3));assertEquals(0,m.output(4));}
	@Test void assemblyWatchdogStopsBackwardLoop(){PlcMachine m=new PlcMachine();PlcAssemblyProgram p=PlcAssemblyProgram.compile("loop:\nJMP loop");m.beginScan(new int[64]);p.scan(m);assertEquals(PlcFault.WATCHDOG,m.fault());assertEquals(0,m.output(0));}
	@Test void assemblySupportsMapAndPulse(){PlcMachine m=new PlcMachine();PlcAssemblyProgram p=PlcAssemblyProgram.compile("MAP R0 I0 0 15 0 15\nPULSE R1 I1 2\nMOV O0 R0\nMOV O1 R1");m.beginScan(input(8,15));p.scan(m);m.finishScan();assertEquals(8,m.output(0));assertEquals(15,m.output(1));m.beginScan(input(8,0));p.scan(m);m.finishScan();assertEquals(15,m.output(1));m.beginScan(input(8,0));p.scan(m);assertEquals(0,m.output(1));}
	@Test void javascriptUsesRestrictedApiAndPersistentRegisters(){PlcMachine m=new PlcMachine();PlcJavaScriptProgram p=PlcJavaScriptProgram.compile("function scan(plc){plc.setRegister(0,plc.register(0)+1);plc.output(4,plc.input(2)+plc.register(0));}");m.beginScan(channel(2,5));p.scan(m);assertEquals(6,m.output(4));m.finishScan();m.beginScan(new int[64]);p.scan(m);assertEquals(2,m.output(4));}
	@Test void javascriptCannotSeeJavaAndWatchdogKillsLoop(){PlcMachine blocked=new PlcMachine();PlcJavaScriptProgram access=PlcJavaScriptProgram.compile("function scan(plc){plc.output(0, java.lang.System ? 15 : 1);}");blocked.beginScan(new int[64]);access.scan(blocked);assertEquals(PlcFault.RUNTIME_ERROR,blocked.fault());PlcMachine loop=new PlcMachine();PlcJavaScriptProgram infinite=PlcJavaScriptProgram.compile("function scan(plc){while(true){}} ");loop.beginScan(new int[64]);infinite.scan(loop);assertEquals(PlcFault.WATCHDOG,loop.fault());}
	@Test void programLimitsAreEnforced(){assertThrows(IllegalArgumentException.class,()->PlcAssemblyProgram.compile("MOV R0 1\n".repeat(65)));assertThrows(IllegalArgumentException.class,()->PlcJavaScriptProgram.compile(" ".repeat(8193)));}
	private static int[] input(int a,int b){int[]x=new int[64];x[0]=a;x[1]=b;return x;}
	private static int[] channel(int c,int v){int[]x=new int[64];x[c]=v;return x;}
}
