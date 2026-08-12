package com.yu1745.chemicaladdon;

import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.simibubi.create.foundation.utility.FilesHelper;
import com.tterrag.registrate.providers.ProviderType;

import net.minecraftforge.data.event.GatherDataEvent;

/**
 * Registrate datagen entry point (mirrors Create's {@code CreateDatagen}).
 *
 * Registrate's own {@code GatherDataEvent} listener (wired by
 * {@code REGISTRATE.registerEventListeners}) already emits blockstate / model /
 * bucket-model / en_us-lang JSON for everything registered via Registrate
 * builders. This listener runs at LOWEST priority (after Registrate's) and only
 * adds what Registrate cannot produce: the hand-authored English EXTRA keys
 * (goggles.*, status.*, assemble.*, gui.*, itemGroup) sourced from
 * {@code lang/default/extra.json} (see tools/gen_species.py).
 */
public class ChemicalDataGen {

	public static void gatherData(GatherDataEvent event) {
		addExtraLang();
	}

	private static void addExtraLang() {
		ChemicalAddon.registrate().addDataGenerator(ProviderType.LANG, provider -> {
			String path = "assets/chemicaladdon/lang/default/extra.json";
			JsonElement jsonElement = FilesHelper.loadJsonResource(path);
			if (jsonElement == null || !jsonElement.isJsonObject()) {
				throw new IllegalStateException("Could not find default lang file: " + path);
			}
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
				provider.add(entry.getKey(), entry.getValue().getAsString());
			}
		});
	}
}
