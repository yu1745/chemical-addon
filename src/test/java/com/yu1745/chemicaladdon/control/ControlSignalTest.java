package com.yu1745.chemicaladdon.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ControlSignalTest {
	@Test void linearUsesLiveZeroAndReservesZeroForInvalid(){assertEquals(0,ControlSignal.analog(1,5,5));assertEquals(1,ControlSignal.analog(-10,0,100));assertEquals(8,ControlSignal.analog(50,0,100));assertEquals(15,ControlSignal.analog(999,0,100));}
	@Test void logarithmicMapsDecades(){assertEquals(0,ControlSignal.logarithmic(10,0,100));assertEquals(1,ControlSignal.logarithmic(.1,.1,100));assertEquals(15,ControlSignal.logarithmic(100,.1,100));assertEquals(8,ControlSignal.logarithmic(Math.sqrt(10),.1,100));}
}
