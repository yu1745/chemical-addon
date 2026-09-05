package com.yu1745.chemicaladdon.composition.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class EngineReadingsPeTest {
	@Test void publishedSnapshotCarriesTickDriversRealPe(){TickDriver.Step step=new TickDriver.Step(true,Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),6.5,9.25,.5);EngineReadings.publish(step,null);assertEquals(9.25,EngineReadings.peek().pe,1e-9);}
}
