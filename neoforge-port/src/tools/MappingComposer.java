import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Composes Mojang, intermediary and Yarn mappings into direct two-namespace
 * Tiny v2 mappings. This keeps the existing Fabric-era source vocabulary while
 * allowing the final bytecode to run on NeoForge's Mojang-named runtime.
 */
public final class MappingComposer {
	private record Member(String name, String descriptor) {
	}

	private record MojangMember(String name, String descriptor) {
	}

	private static final Pattern CLASS_LINE =
			Pattern.compile("^(\\S+) -> (\\S+):$");
	private static final Pattern MEMBER_LINE =
			Pattern.compile("^\\s*(?:\\d+:\\d+:)?(\\S+) (\\S+) -> (\\S+)$");

	private final Map<String, String> mojangToObf = new HashMap<>();
	private final Map<String, String> obfToMojang = new HashMap<>();
	private final Map<String, String> obfToIntermediary = new HashMap<>();
	private final Map<String, String> intermediaryToYarn = new HashMap<>();
	private final Map<String, Member> intermediaryToYarnMembers = new HashMap<>();
	private final Map<String, Member> obfToIntermediaryMembers = new HashMap<>();
	private final Map<String, MojangMember> mojangMembers = new HashMap<>();

	private static String memberKey(String owner, String name, String descriptor) {
		return owner + '\u0000' + name + '\u0000' + descriptor;
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.println("Usage: MappingComposer <mojang.txt> <intermediary.tiny> <yarn.tiny> <output-dir>");
			System.exit(2);
		}
		MappingComposer composer = new MappingComposer();
		composer.readMojang(Path.of(args[0]));
		composer.readIntermediary(Path.of(args[1]));
		composer.readYarn(Path.of(args[2]));
		Path output = Path.of(args[3]);
		Files.createDirectories(output);
		composer.write(composer.compose(false), output.resolve("mojang-yarn.tiny"), false);
		composer.write(composer.compose(true), output.resolve("yarn-mojang.tiny"), true);
		composer.writeRuntimeAnnotationMap(output.resolve("runtime-annotations.tsv"),
				output.resolve("post-remap-members.tsv"));
		System.out.printf("Mappings: %d classes, %d Yarn members%n",
				composer.mojangToObf.size(), composer.intermediaryToYarnMembers.size());
		System.out.printf("Members: Mojang=%d obf=%d%n",
				composer.mojangMembers.size(), composer.obfToIntermediaryMembers.size());
	}

	private void writeRuntimeAnnotationMap(Path output, Path postRemapOutput)
			throws IOException {
		List<String> postRemapLines = new java.util.ArrayList<>();
		try (BufferedWriter writer = Files.newBufferedWriter(output,
				StandardCharsets.UTF_8)) {
			for (Map.Entry<String, String> entry : obfToMojang.entrySet()) {
				String obfOwner = entry.getKey();
				String intermediaryOwner = obfToIntermediary.get(obfOwner);
				if (intermediaryOwner == null) {
					continue;
				}
				String yarnOwner = intermediaryToYarn.get(intermediaryOwner);
				if (yarnOwner != null) {
					writer.write("C\t");
					writer.write(yarnOwner);
					writer.write('\t');
					writer.write(entry.getValue());
					writer.write('\n');
				}
				writer.write("C\t");
				writer.write(intermediaryOwner);
				writer.write('\t');
				writer.write(entry.getValue());
				writer.write('\n');
			}
			for (Map.Entry<String, MojangMember> entry : mojangMembers.entrySet()) {
				String[] keyParts = entry.getKey().split("\u0000", -1);
				String mojangOwner = keyParts[0];
				String obfOwner = mojangToObf.get(mojangOwner);
				if (obfOwner == null) {
					continue;
				}
				MojangMember mojangMember = entry.getValue();
				Member intermediaryMember = obfToIntermediaryMembers.get(
						memberKey(obfOwner, mojangMember.name(),
								mojangMember.descriptor()));
				if (intermediaryMember == null) {
					continue;
				}
				String intermediaryOwner = obfToIntermediary.get(obfOwner);
				boolean method = keyParts[2].startsWith("(");
				String intermediaryDescriptor = remapDescriptor(
						intermediaryMember.descriptor(), obfToIntermediary);
				Member yarnMember = intermediaryToYarnMembers.get(
						memberKey(intermediaryOwner, intermediaryMember.name(),
								intermediaryDescriptor));
				if (yarnMember != null) {
					postRemapLines.add((method ? "M\t" : "F\t") + mojangOwner + '\t'
							+ yarnMember.name() + '\t' + keyParts[2] + '\t'
							+ keyParts[1]);
				}
				writer.write(method ? "M\t" : "F\t");
				writer.write(mojangOwner);
				writer.write('\t');
				writer.write(intermediaryMember.name());
				if (method) {
					writer.write(intermediaryDescriptor);
				}
				writer.write('\t');
				writer.write(keyParts[1]);
				if (method) {
					writer.write(keyParts[2]);
				}
				writer.write('\n');
			}
		}
		Files.write(postRemapOutput, postRemapLines, StandardCharsets.UTF_8);
	}

	private void readMojang(Path path) throws IOException {
		java.util.List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		for (String raw : lines) {
			if (raw.isBlank() || raw.startsWith("#")) {
				continue;
			}
			Matcher cm = CLASS_LINE.matcher(raw);
			if (cm.matches()) {
				String ownerMojang = cm.group(1).replace('.', '/');
				String ownerObf = cm.group(2).replace('.', '/');
				mojangToObf.put(ownerMojang, ownerObf);
				obfToMojang.put(ownerObf, ownerMojang);
			}
		}
		String ownerMojang = null;
		for (String raw : lines) {
			if (raw.isBlank() || raw.startsWith("#")) {
				continue;
			}
			Matcher cm = CLASS_LINE.matcher(raw);
			if (cm.matches()) {
				ownerMojang = cm.group(1).replace('.', '/');
				continue;
			}
			Matcher mm = MEMBER_LINE.matcher(raw);
			if (ownerMojang == null || !mm.matches()) {
				continue;
			}
			String descriptor = proguardDescriptor(mm.group(1), mm.group(2));
			if (descriptor == null) {
				continue;
			}
			String obfDescriptor = remapDescriptor(descriptor, mojangToObf);
			String memberName = mm.group(2);
			int paren = memberName.indexOf('(');
			if (paren >= 0) {
				memberName = memberName.substring(0, paren);
			}
			mojangMembers.put(memberKey(ownerMojang, memberName, descriptor),
					new MojangMember(mm.group(3), obfDescriptor));
		}
	}

	private static String proguardDescriptor(String type, String signature) {
		int paren = signature.indexOf('(');
		if (paren < 0) {
			return javaTypeDescriptor(type);
		}
		int close = signature.indexOf(')', paren);
		if (close < 0) {
			return null;
		}
		StringBuilder descriptor = new StringBuilder("(");
		String arguments = signature.substring(paren + 1, close);
		if (!arguments.isBlank()) {
			for (String argument : arguments.split(",")) {
				String mapped = javaTypeDescriptor(argument.trim());
				if (mapped == null) {
					return null;
				}
				descriptor.append(mapped);
			}
		}
		descriptor.append(')');
		String returnType = javaTypeDescriptor(type);
		return returnType == null ? null : descriptor.append(returnType).toString();
	}

	private static String javaTypeDescriptor(String type) {
		return switch (type) {
			case "void" -> "V";
			case "boolean" -> "Z";
			case "byte" -> "B";
			case "char" -> "C";
			case "short" -> "S";
			case "int" -> "I";
			case "long" -> "J";
			case "float" -> "F";
			case "double" -> "D";
			default -> type.endsWith("[]")
					? "[" + javaTypeDescriptor(type.substring(0, type.length() - 2))
					: "L" + type.replace('.', '/') + ";";
		};
	}

	private void readIntermediary(Path path) throws IOException {
		String currentObf = null;
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String line = raw.stripLeading();
			if (line.startsWith("c\t")) {
				String[] parts = line.split("\t", -1);
				if (parts.length < 3) {
					continue;
				}
				currentObf = parts[1];
				obfToIntermediary.put(currentObf, parts[2]);
			} else if ((line.startsWith("m\t") || line.startsWith("f\t"))
					&& currentObf != null) {
				String[] parts = line.split("\t", -1);
				if (parts.length < 4) {
					continue;
				}
				String targetDescriptor = parts.length >= 5 ? parts[3] : parts[1];
				String targetName = parts.length >= 5 ? parts[4] : parts[3];
				obfToIntermediaryMembers.put(
						memberKey(currentObf, parts[2], parts[1]),
						new Member(targetName, targetDescriptor));
			}
		}
	}

	private void readYarn(Path path) throws IOException {
		String currentIntermediary = null;
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String line = raw.stripLeading();
			if (line.startsWith("c\t")) {
				String[] parts = line.split("\t", -1);
				if (parts.length < 3) {
					continue;
				}
				currentIntermediary = parts[1];
				intermediaryToYarn.put(currentIntermediary, parts[2]);
			} else if ((line.startsWith("m\t") || line.startsWith("f\t"))
					&& currentIntermediary != null) {
				String[] parts = line.split("\t", -1);
				if (parts.length < 4) {
					continue;
				}
				String targetDescriptor = parts.length >= 5 ? parts[3] : parts[1];
				String targetName = parts.length >= 5 ? parts[4] : parts[3];
				intermediaryToYarnMembers.put(
						memberKey(currentIntermediary, parts[2], parts[1]),
						new Member(targetName, targetDescriptor));
			}
		}
	}

	private static String remapDescriptor(String descriptor, Map<String, String> classNames) {
		StringBuilder result = new StringBuilder(descriptor.length() + 16);
		for (int i = 0; i < descriptor.length(); ) {
			char c = descriptor.charAt(i);
			if (c == 'L') {
				int end = descriptor.indexOf(';', i);
				if (end < 0) {
					result.append(descriptor.substring(i));
					break;
				}
				String name = descriptor.substring(i + 1, end);
				result.append('L').append(classNames.getOrDefault(name, name)).append(';');
				i = end + 1;
			} else {
				result.append(c);
				i++;
			}
		}
		return result.toString();
	}

	private Map<String, Map<String, Member>> compose(boolean yarnToMojang) {
		Map<String, Map<String, Member>> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> classEntry : mojangToObf.entrySet()) {
			String mojangOwner = classEntry.getKey();
			String intermediaryOwner = obfToIntermediary.get(classEntry.getValue());
			if (intermediaryOwner == null) {
				continue;
			}
			String yarnOwner = intermediaryToYarn.get(intermediaryOwner);
			if (yarnOwner == null) {
				continue;
			}
			String key = yarnToMojang
					? yarnOwner + '\u0000' + mojangOwner
					: mojangOwner + '\u0000' + yarnOwner;
			result.put(key, new LinkedHashMap<>());
		}

		for (Map.Entry<String, MojangMember> entry : mojangMembers.entrySet()) {
			String[] keyParts = entry.getKey().split("\u0000", -1);
			String mojangOwner = keyParts[0];
			String descriptor = keyParts[2];
			String obfOwner = mojangToObf.get(mojangOwner);
			if (obfOwner == null) {
				continue;
			}
			MojangMember mojangMember = entry.getValue();
			if (("net/minecraft/world/entity/Entity".equals(mojangOwner)
					&& "level".equals(keyParts[1]))
					|| ("net/minecraft/server/MinecraftServer".equals(mojangOwner)
					&& java.util.Set.of("getTickCount", "getPlayerList",
					"getLevel", "getAllLevels", "playerList", "tickCount")
					.contains(keyParts[1]))) {
				System.out.printf("Entity.level mojangMember=%s desc=%s obfDescriptor=%s%n",
						mojangMember.name(), descriptor, mojangMember.descriptor());
			}
			Member intermediaryMember = obfToIntermediaryMembers.get(
					memberKey(obfOwner, mojangMember.name(), mojangMember.descriptor()));
			if (("net/minecraft/world/entity/Entity".equals(mojangOwner)
					&& "level".equals(keyParts[1]))
					|| ("net/minecraft/server/MinecraftServer".equals(mojangOwner)
					&& java.util.Set.of("getTickCount", "getPlayerList",
					"getLevel", "getAllLevels", "playerList", "tickCount")
					.contains(keyParts[1]))) {
				System.out.printf("  intermediary=%s%n", intermediaryMember);
			}
			if (intermediaryMember == null) {
				continue;
			}
			String intermediaryOwner = obfToIntermediary.get(obfOwner);
			String intermediaryDescriptor = remapDescriptor(
					intermediaryMember.descriptor(), obfToIntermediary);
			Member yarnMember = intermediaryToYarnMembers.get(
					memberKey(intermediaryOwner, intermediaryMember.name(),
							intermediaryDescriptor));
			if (("net/minecraft/world/entity/Entity".equals(mojangOwner)
					&& "level".equals(keyParts[1]))
					|| ("net/minecraft/server/MinecraftServer".equals(mojangOwner)
					&& java.util.Set.of("getTickCount", "getPlayerList",
					"getLevel", "getAllLevels", "playerList", "tickCount")
					.contains(keyParts[1]))) {
				System.out.printf("  yarnMember=%s interOwner=%s%n",
						yarnMember, intermediaryOwner);
			}
			if (yarnMember == null) {
				continue;
			}
			String yarnOwner = intermediaryToYarn.get(intermediaryOwner);
			Map<String, Member> ownerMembers = result.get(yarnToMojang
					? yarnOwner + '\u0000' + mojangOwner
					: mojangOwner + '\u0000' + yarnOwner);
			if (ownerMembers == null) {
				continue;
			}
			if (yarnToMojang) {
				String yarnDescriptor = remapDescriptor(
						yarnMember.descriptor(), intermediaryToYarn);
				ownerMembers.put(memberKey(yarnMember.name(), yarnDescriptor, ""),
						new Member(keyParts[1], descriptor));
			} else {
				ownerMembers.put(memberKey(keyParts[1], descriptor, ""),
						new Member(yarnMember.name(), yarnMember.descriptor()));
			}
		}
		addOverride(result, yarnToMojang,
				"net/minecraft/network/protocol/common/custom/CustomPacketPayload",
				"net/minecraft/network/packet/CustomPayload",
				"()Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;",
				"()Lnet/minecraft/network/packet/CustomPayload$Id;",
				"type", "getId");
		removeMapping(result, yarnToMojang,
				"net/minecraft/world/level/dimension/DimensionType",
				"net/minecraft/world/dimension/DimensionType",
				"MIN_Y", "MIN_HEIGHT", "I");
		removeMapping(result, yarnToMojang,
				"net/minecraft/client/renderer/chunk/SectionRenderDispatcher$CompiledSection",
				"net/minecraft/client/render/chunk/ChunkBuilder$ChunkData",
				"UNCOMPILED", "EMPTY",
				"Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$CompiledSection;");
		removeMapping(result, yarnToMojang,
				"net/minecraft/client/gui/Gui", "net/minecraft/client/gui/hud/InGameHud",
				"renderItemHotbar", "renderHotbar",
				"(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V");
		removeMapping(result, yarnToMojang,
				"net/minecraft/world/entity/animal/horse/AbstractHorse",
				"net/minecraft/entity/passive/AbstractHorseEntity",
				"getBodyArmorAccess", "getInventory",
				"()Lnet/minecraft/world/Container;");
		removeMapping(result, yarnToMojang,
				"net/minecraft/world/entity/npc/Villager", "net/minecraft/entity/passive/VillagerEntity",
				"wantsMoreFood", "canBreed", "()Z");
		removeMapping(result, yarnToMojang,
				"net/minecraft/world/entity/npc/Villager", "net/minecraft/entity/passive/VillagerEntity",
				"canRestock", "canRestock", "()Z");
		removeMapping(result, yarnToMojang,
				"net/minecraft/world/entity/npc/Villager", "net/minecraft/entity/passive/VillagerEntity",
				"allowedToRestock", "canRestock", "()Z");
		return result;
	}

	private static void removeMapping(Map<String, Map<String, Member>> result,
			boolean yarnToMojang, String mojangOwner, String yarnOwner,
			String mojangName, String yarnName, String mojangDescriptor) {
		Map<String, Member> members = result.get(yarnToMojang
				? yarnOwner + '\u0000' + mojangOwner
				: mojangOwner + '\u0000' + yarnOwner);
		if (members == null) {
			return;
		}
		members.keySet().removeIf(key -> {
			String[] parts = key.split("\u0000", -1);
			if (!(yarnToMojang ? yarnName : mojangName).equals(parts[0])) {
				return false;
			}
			if (!yarnToMojang) {
				return mojangDescriptor.equals(parts[1]);
			}
			Member target = members.get(key);
			return target != null && mojangDescriptor.equals(target.descriptor());
		});
	}

	private static void addOverride(Map<String, Map<String, Member>> result,
			boolean yarnToMojang, String mojangOwner, String yarnOwner,
			String mojangDescriptor, String yarnDescriptor,
			String mojangName, String yarnName) {
		Map<String, Member> members = result.get(yarnToMojang
				? yarnOwner + '\u0000' + mojangOwner
				: mojangOwner + '\u0000' + yarnOwner);
		if (members == null) {
			return;
		}
		if (yarnToMojang) {
			members.put(memberKey(yarnName, yarnDescriptor, ""),
					new Member(mojangName, mojangDescriptor));
		} else {
			members.put(memberKey(mojangName, mojangDescriptor, ""),
					new Member(yarnName, yarnDescriptor));
		}
	}

	private void write(Map<String, Map<String, Member>> mappings, Path output,
			boolean yarnToMojang) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			writer.write(yarnToMojang
					? "tiny\t2\t0\tnamed\tofficial\n"
					: "tiny\t2\t0\tofficial\tnamed\n");
			for (Map.Entry<String, Map<String, Member>> classEntry : mappings.entrySet()) {
				String[] owners = classEntry.getKey().split("\u0000", -1);
				writer.write("c\t");
				writer.write(owners[0]);
				writer.write('\t');
				writer.write(owners[1]);
				writer.write('\n');
				for (Map.Entry<String, Member> entry : classEntry.getValue().entrySet()) {
					String[] source = entry.getKey().split("\u0000", -1);
					Member target = entry.getValue();
					writer.write(source[1].startsWith("(") ? "\tm\t" : "\tf\t");
					writer.write(source[1]);
					writer.write('\t');
					writer.write(source[0]);
					writer.write('\t');
					writer.write(target.name());
					writer.write('\n');
				}
			}
		}
	}
}
