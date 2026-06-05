package videogoose.betterbuilding;

import api.mod.StarMod;
import videogoose.betterbuilding.util.BbFiles;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Ships reference content as worked examples. On first run, writes a couple of
 * standalone palettes into {@code BetterBuilding/palettes/} if they are missing.
 *
 * <p>Block references use real StarMade block names (resolved case-insensitively
 * via ElementKeyMap). The grey/crystal pair is the Milestone 1 reskin example:
 * a grey-armor hull re-skins to crystal with wedges/corners preserved.
 */
final class SampleContent {

	private SampleContent() {}

	private static final String GREY_PALETTE =
			"{\n" +
			"  \"name\": \"Grey Standard\",\n" +
			"  \"slots\": {\n" +
			"    \"PRIMARY_HULL\":   { \"block\": \"Grey Advanced Armor\" },\n" +
			"    \"SECONDARY_HULL\": { \"block\": \"Grey Standard Armor\" },\n" +
			"    \"ACCENT\":         { \"block\": \"Blue Standard Armor\" },\n" +
			"    \"GLASS\":          { \"block\": \"Glass\" },\n" +
			"    \"LIGHT\":          { \"block\": \"White Light Bar\" },\n" +
			"    \"SYSTEM\":         { \"block\": \"Reactor Conduit\" }\n" +
			"  }\n" +
			"}\n";

	private static final String CRYSTAL_PALETTE =
			"{\n" +
			"  \"name\": \"Crystal\",\n" +
			"  \"slots\": {\n" +
			"    \"PRIMARY_HULL\":   { \"block\": \"Teal Crystal Armor\" },\n" +
			"    \"SECONDARY_HULL\": { \"block\": \"Blue Crystal Armor\" },\n" +
			"    \"ACCENT\":         { \"block\": \"Orange Crystal Armor\" },\n" +
			"    \"GLASS\":          { \"block\": \"Glass\" },\n" +
			"    \"LIGHT\":          { \"block\": \"Blue Light Bar\" },\n" +
			"    \"SYSTEM\":         { \"block\": \"Reactor Conduit\" }\n" +
			"  }\n" +
			"}\n";

	static void installDefaults(StarMod mod) {
		writeIfAbsent(mod, new File(BbFiles.palettes(), "grey.json"), GREY_PALETTE);
		writeIfAbsent(mod, new File(BbFiles.palettes(), "crystal.json"), CRYSTAL_PALETTE);
	}

	private static void writeIfAbsent(StarMod mod, File file, String content) {
		if (file.exists()) return;
		try {
			Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
			mod.logInfo("Installed sample content: " + file.getPath());
		} catch (Exception e) {
			mod.logException("Failed to install sample content " + file.getPath(), e);
		}
	}
}
