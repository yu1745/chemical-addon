package com.yu1745.chemicaladdon.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.yu1745.chemicaladdon.vessel.ProcessCapability;

class ReactionRequirementsCodecTest {

	@Test
	void everyCapabilityHasAnExplicitSnakeCaseName() {
		for (ProcessCapability capability : ProcessCapability.values()) {
			assertTrue(capability.jsonName().matches("[a-z]+(?:_[a-z]+)*"));
			assertEquals(capability, ProcessCapability.fromJsonName(capability.jsonName()));
			assertEquals(capability, ProcessCapability.fromJsonName("chemicaladdon:" + capability.jsonName()));
		}
	}

	@Test
	void conditionsRoundTripAndEnforceAgitationBounds() {
		JsonObject input = new JsonObject();
		JsonObject temperature = new JsonObject();
		temperature.addProperty("min", 303);
		temperature.addProperty("max", 333);
		input.add("temperature", temperature);
		JsonObject pressure = new JsonObject();
		pressure.addProperty("max", 300);
		input.add("pressureKpa", pressure);
		JsonObject agitation = new JsonObject();
		agitation.addProperty("min", 0.5);
		input.add("agitation", agitation);

		ReactionConditions conditions = ReactionConditions.fromJson(input);
		assertTrue(conditions.matchesTemperature(303));
		assertTrue(!conditions.matchesTemperature(334));
		assertTrue(conditions.matchesPressureKpa(0));
		assertTrue(!conditions.matchesPressureKpa(301));
		assertTrue(conditions.hasAgitation(), "agitation metadata must be retained");
		// B1: agitation bounds are enforced against the snapshot's live normalized reading
		assertTrue(conditions.matchesAgitation(0.5));
		assertTrue(conditions.matchesAgitation(1.0));
		assertTrue(!conditions.matchesAgitation(0.49));
		assertTrue(!conditions.matchesAgitation(0.0), "an unstirred vessel fails a positive lower bound");
		assertEquals(input, conditions.toJson());
	}

}
