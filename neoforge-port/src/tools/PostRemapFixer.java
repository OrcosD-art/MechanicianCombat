import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Applies the small number of runtime-facing fixes that cannot be expressed by
 * the generated class mapping: CustomPacketPayload type(), plus Mixin
 * annotation strings that still contain intermediary member names.
 */
public final class PostRemapFixer {
	private static final String CUSTOM_PAYLOAD =
			"net/minecraft/network/protocol/common/custom/CustomPacketPayload";
	private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String SHADOW_DESC = "Lorg/spongepowered/asm/mixin/Shadow;";

	private final Map<String, String> classNames = new HashMap<>();
	private final Map<String, Map<String, String>> members = new HashMap<>();
	private final Map<String, Map<String, String>> postRemapMembers = new HashMap<>();
	private final Map<String, ClassInfo> classInfo = new HashMap<>();
	private int inheritedMemberFixes;

	private static final class ClassInfo {
		private final String superName;
		private final List<String> interfaces;
		private final Set<String> methods;
		private final Set<String> fields;
		private final boolean interfaceType;

		private ClassInfo(String superName, List<String> interfaces,
				Set<String> methods, Set<String> fields, boolean interfaceType) {
			this.superName = superName;
			this.interfaces = interfaces;
			this.methods = methods;
			this.fields = fields;
			this.interfaceType = interfaceType;
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 4) {
			System.err.println("Usage: PostRemapFixer <input.jar> <output.jar> <runtime-annotations.tsv> <post-remap-members.tsv>");
			System.err.println("       [runtime-classpath.jar ...]");
			System.exit(2);
		}
		PostRemapFixer fixer = new PostRemapFixer();
		fixer.readMappings(Path.of(args[2]));
		fixer.readPostRemapMembers(Path.of(args[3]));
		fixer.readClassHierarchy(Path.of(args[0]));
		for (int i = 4; i < args.length; i++) {
			fixer.readClassHierarchy(Path.of(args[i]));
		}
		fixer.rewriteJar(Path.of(args[0]), Path.of(args[1]));
		System.out.printf("Inherited member references normalized: %d%n",
				fixer.inheritedMemberFixes);
	}

	private void readPostRemapMembers(Path path) throws Exception {
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String[] parts = line.split("\t", -1);
			if (parts.length != 5) {
				continue;
			}
			postRemapMembers.computeIfAbsent(parts[1],
							ignored -> new HashMap<>())
					.put(parts[0] + '\u0000' + parts[2] + '\u0000' + parts[3],
							parts[4]);
		}
	}

	private void readMappings(Path path) throws Exception {
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String[] parts = line.split("\t", -1);
			if (parts.length < 3) {
				continue;
			}
			if ("C".equals(parts[0])) {
				classNames.put(parts[1], parts[2]);
			} else {
				members.computeIfAbsent(parts[1], ignored -> new HashMap<>())
						.put(parts[2], parts[3]);
			}
		}
	}

	private void readClassHierarchy(Path path) throws Exception {
		try (ZipFile zip = new ZipFile(path.toFile())) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
					continue;
				}
				try (InputStream stream = zip.getInputStream(entry)) {
					readClassHierarchy(stream.readAllBytes());
				}
			}
		}
	}

	private void readClassHierarchy(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node,
				ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		Set<String> methods = new HashSet<>();
		for (MethodNode method : node.methods) {
			methods.add(method.name + method.desc);
		}
		Set<String> fields = new HashSet<>();
		for (FieldNode field : node.fields) {
			fields.add(field.name + ':' + field.desc);
		}
		classInfo.put(node.name, new ClassInfo(node.superName,
				node.interfaces == null ? List.of() : List.copyOf(node.interfaces),
				methods, fields,
				(node.access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0));
	}

	private void rewriteJar(Path input, Path output) throws Exception {
		try (ZipFile zip = new ZipFile(input.toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output))) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				try (InputStream stream = zip.getInputStream(entry)) {
					byte[] bytes = stream.readAllBytes();
					if (entry.getName().endsWith(".class")) {
						bytes = rewriteClass(bytes);
					}
					ZipEntry rewritten = new ZipEntry(entry.getName());
					rewritten.setTime(0L);
					out.putNextEntry(rewritten);
					out.write(bytes);
					out.closeEntry();
				}
			}
		}
	}

	private byte[] rewriteClass(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		List<String> targets = mixinTargets(node);
		Map<String, String> shadowFields = new HashMap<>();
		Map<String, String> shadowMethods = new HashMap<>();
		for (FieldNode field : node.fields) {
			if (hasAnnotation(field.visibleAnnotations, field.invisibleAnnotations,
					SHADOW_DESC)) {
				String mapped = lookupMember(targets, field.name);
				if (mapped != null && !mapped.equals(field.name)
						&& mapped.indexOf('(') < 0) {
					shadowFields.put(field.name, mapped);
				}
			}
		}
		for (MethodNode method : node.methods) {
			if (hasAnnotation(method.visibleAnnotations, method.invisibleAnnotations,
					SHADOW_DESC)) {
				String mapped = lookupMember(targets, method.name);
				if (mapped != null && !mapped.startsWith(method.name + "(")) {
					int paren = mapped.indexOf('(');
					shadowMethods.put(method.name,
							paren < 0 ? mapped : mapped.substring(0, paren));
				}
			}
		}
		Map<String, String> overriddenMethods = new HashMap<>();
		for (MethodNode method : node.methods) {
			if ("<init>".equals(method.name) || "<clinit>".equals(method.name)
					|| hasAnnotation(method.visibleAnnotations,
					method.invisibleAnnotations, SHADOW_DESC)) {
				continue;
			}
			MemberResolution resolution = findInheritedMember(node,
					"M", method.name, method.desc);
			if (resolution != null && !resolution.name.equals(method.name)) {
				overriddenMethods.put(method.name + method.desc, resolution.name);
			}
		}
		if (!overriddenMethods.isEmpty()) {
			for (MethodNode method : node.methods) {
				String renamed = overriddenMethods.get(method.name + method.desc);
				if (renamed != null) {
					method.name = renamed;
				}
			}
		}
		if (!shadowFields.isEmpty() || !shadowMethods.isEmpty()) {
			for (FieldNode field : node.fields) {
				field.name = shadowFields.getOrDefault(field.name, field.name);
			}
			for (MethodNode method : node.methods) {
				String renamed = shadowMethods.get(method.name);
				if (renamed != null) {
					method.name = renamed;
				}
				for (var instruction : method.instructions) {
					if (instruction instanceof FieldInsnNode access
							&& node.name.equals(access.owner)
							&& shadowFields.containsKey(access.name)) {
						access.name = shadowFields.get(access.name);
					}
					if (instruction instanceof MethodInsnNode call
							&& node.name.equals(call.owner)
							&& shadowMethods.containsKey(call.name)) {
						call.name = shadowMethods.get(call.name);
					}
				}
			}
		}
		if (!overriddenMethods.isEmpty()) {
			for (MethodNode method : node.methods) {
				for (var instruction : method.instructions) {
					if (instruction instanceof MethodInsnNode call
							&& node.name.equals(call.owner)) {
						String renamed = overriddenMethods.get(
								call.name + call.desc);
						if (renamed != null) {
							call.name = renamed;
						}
					}
				}
			}
		}
		for (MethodNode method : node.methods) {
			if ("com/tk/gun/entity/SoulAnchorEntity".equals(node.name)
					&& "getGravity".equals(method.name)
					&& "()D".equals(method.desc)) {
				method.name = "getDefaultGravity";
			}
			if ("getId".equals(method.name)
					&& method.desc.endsWith(
					"Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;")) {
				method.name = "type";
			}
			rewriteAnnotations(method.visibleAnnotations, targets);
			rewriteAnnotations(method.invisibleAnnotations, targets);
			for (var instruction : method.instructions) {
				if (instruction instanceof MethodInsnNode call
						&& CUSTOM_PAYLOAD.equals(call.owner)
						&& "getId".equals(call.name)) {
					call.name = "type";
				}
				if (instruction instanceof MethodInsnNode call
						&& "net/minecraft/resources/ResourceLocation".equals(call.owner)
						&& "of".equals(call.name)
						&& "(Ljava/lang/String;Ljava/lang/String;)"
						.equals(call.desc.substring(0, call.desc.indexOf(')') + 1))) {
					call.name = "fromNamespaceAndPath";
				}
				if (instruction instanceof MethodInsnNode call) {
					fixMember(call, true);
				}
				if (instruction instanceof FieldInsnNode access) {
					fixMember(access, false);
				}
			}
		}
		for (FieldNode field : node.fields) {
			rewriteAnnotations(field.visibleAnnotations, targets);
			rewriteAnnotations(field.invisibleAnnotations, targets);
		}
		rewriteAnnotations(node.visibleAnnotations, targets);
		rewriteAnnotations(node.invisibleAnnotations, targets);
		ClassNode classRemapped = new ClassNode();
		node.accept(new ClassRemapper(classRemapped,
				new SimpleRemapper(classNames)));
		ClassWriter writer = new ClassWriter(0);
		classRemapped.accept(writer);
		return writer.toByteArray();
	}

	private void fixMember(Object instruction, boolean method) {
		String owner;
		String name;
		String descriptor;
		boolean invokespecial = false;
		if (instruction instanceof MethodInsnNode call) {
			owner = call.owner;
			name = call.name;
			descriptor = call.desc;
			invokespecial = call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESPECIAL;
		} else {
			FieldInsnNode access = (FieldInsnNode) instruction;
			owner = access.owner;
			name = access.name;
			descriptor = access.desc;
		}
		if (method && ("<init>".equals(name) || "<clinit>".equals(name))) {
			return;
		}
		String kind = method ? "M" : "F";
		String mapped = owner.startsWith("net/minecraft/")
				? findExactPostRemapMember(owner, kind, name, descriptor)
				: null;
		String resolvedOwner = owner;
		if (mapped == null) {
			MemberResolution resolution = findInheritedMember(owner,
					kind, name, descriptor);
			if (resolution == null) {
				if (!owner.startsWith("net/minecraft/")) {
					return;
				}
				mapped = findPostRemapMember(owner, kind, name, descriptor);
				if (mapped == null) {
					return;
				}
			} else {
				mapped = resolution.name;
				resolvedOwner = resolution.owner;
				inheritedMemberFixes++;
			}
		}
		if (instruction instanceof MethodInsnNode call) {
			if (!resolvedOwner.equals(owner) && !invokespecial) {
				call.owner = resolvedOwner;
			}
			call.name = mapped;
			ClassInfo ownerInfo = classInfo.get(call.owner);
			if (ownerInfo != null) {
				call.itf = ownerInfo.interfaceType;
				if (ownerInfo.interfaceType
						&& call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL) {
					call.setOpcode(org.objectweb.asm.Opcodes.INVOKEINTERFACE);
				}
			}
		} else {
			((FieldInsnNode) instruction).owner = resolvedOwner;
			((FieldInsnNode) instruction).name = mapped;
		}
	}

	private record MemberResolution(String owner, String name) {
	}

	private MemberResolution findInheritedMember(ClassNode node, String kind,
			String name, String descriptor) {
		ArrayDeque<String> pending = new ArrayDeque<>();
		if (node.superName != null) {
			pending.add(node.superName);
		}
		if (node.interfaces != null) {
			pending.addAll(node.interfaces);
		}
		return findInheritedMember(pending, kind, name, descriptor);
	}

	private MemberResolution findInheritedMember(String startOwner, String kind,
			String name, String descriptor) {
		ArrayDeque<String> pending = new ArrayDeque<>();
		pending.add(startOwner);
		return findInheritedMember(pending, kind, name, descriptor);
	}

	private MemberResolution findInheritedMember(ArrayDeque<String> pending,
			String kind, String name, String descriptor) {
		Set<String> visited = new HashSet<>();
		while (!pending.isEmpty()) {
			String owner = pending.removeFirst();
			if (owner == null || !visited.add(owner)) {
				continue;
			}
			String mapped = findExactPostRemapMember(owner, kind, name, descriptor);
			if (mapped != null) {
				return new MemberResolution(owner, mapped);
			}
			ClassInfo info = classInfo.get(owner);
			if (info == null) {
				continue;
			}
			String member = "M".equals(kind)
					? name + descriptor
					: name + ':' + descriptor;
			if (("M".equals(kind) ? info.methods : info.fields).contains(member)) {
				return new MemberResolution(owner, name);
			}
			if (info.superName != null) {
				pending.addLast(info.superName);
			}
			pending.addAll(info.interfaces);
		}
		return null;
	}

	private String findExactPostRemapMember(String owner, String kind, String name,
			String descriptor) {
		Map<String, String> ownerMembers = postRemapMembers.get(owner);
		return ownerMembers == null ? null
				: ownerMembers.get(kind + '\u0000' + name + '\u0000' + descriptor);
	}

	private String findPostRemapMember(String owner, String kind, String name,
			String descriptor) {
		String key = kind + '\u0000' + name + '\u0000' + descriptor;
		Map<String, String> ownerMembers = postRemapMembers.get(owner);
		if (ownerMembers != null) {
			String mapped = ownerMembers.get(key);
			if (mapped != null) {
				return mapped;
			}
		}
		String unique = null;
		for (Map<String, String> candidates : postRemapMembers.values()) {
			String mapped = candidates.get(key);
			if (mapped == null) {
				continue;
			}
			if (unique != null && !unique.equals(mapped)) {
				return null;
			}
			unique = mapped;
		}
		return unique;
	}

	private static boolean hasAnnotation(List<AnnotationNode> visible,
			List<AnnotationNode> invisible, String descriptor) {
		for (AnnotationNode annotation : visible == null ? List.<AnnotationNode>of()
				: visible) {
			if (descriptor.equals(annotation.desc)) {
				return true;
			}
		}
		for (AnnotationNode annotation : invisible == null ? List.<AnnotationNode>of()
				: invisible) {
			if (descriptor.equals(annotation.desc)) {
				return true;
			}
		}
		return false;
	}

	private List<String> mixinTargets(ClassNode node) {
		List<String> targets = new ArrayList<>();
		for (AnnotationNode annotation : annotations(node.visibleAnnotations,
				node.invisibleAnnotations)) {
			if (!MIXIN_DESC.equals(annotation.desc) || annotation.values == null) {
				continue;
			}
			for (int i = 0; i < annotation.values.size(); i += 2) {
				String key = (String) annotation.values.get(i);
				Object value = annotation.values.get(i + 1);
				if ("value".equals(key)) {
					if (value instanceof Type type) {
						targets.add(type.getInternalName());
					} else if (value instanceof List<?> list) {
						for (Object item : list) {
							if (item instanceof Type type) {
								targets.add(type.getInternalName());
							}
						}
					}
				} else if ("targets".equals(key)) {
					if (value instanceof String text) {
						targets.add(classNames.getOrDefault(text, text));
					} else if (value instanceof List<?> list) {
						for (Object item : list) {
							if (item instanceof String text) {
								targets.add(classNames.getOrDefault(text, text));
							}
						}
					}
				}
			}
		}
		return targets;
	}

	private List<AnnotationNode> annotations(List<AnnotationNode> visible,
			List<AnnotationNode> invisible) {
		List<AnnotationNode> result = new ArrayList<>();
		if (visible != null) {
			result.addAll(visible);
		}
		if (invisible != null) {
			result.addAll(invisible);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private void rewriteAnnotations(List<AnnotationNode> annotations,
			List<String> targets) {
		for (AnnotationNode annotation : annotations(annotations, null)) {
			if (annotation.values == null) {
				continue;
			}
			for (int i = 1; i < annotation.values.size(); i += 2) {
				annotation.values.set(i,
						rewriteValue(annotation.values.get(i), targets));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Object rewriteValue(Object value, List<String> targets) {
		if (value instanceof String text) {
			return rewriteString(text, targets);
		}
		if (value instanceof AnnotationNode annotation) {
			if (annotation.values != null) {
				for (int i = 1; i < annotation.values.size(); i += 2) {
					annotation.values.set(i,
							rewriteValue(annotation.values.get(i), targets));
				}
			}
			return annotation;
		}
		if (value instanceof List<?> list) {
			List<Object> copy = (List<Object>) list;
			for (int i = 0; i < copy.size(); i++) {
				copy.set(i, rewriteValue(copy.get(i), targets));
			}
			return copy;
		}
		return value;
	}

	private String rewriteString(String text, List<String> targets) {
		int descriptorStart = text.indexOf('(');
		if ((text.startsWith("<init>(") || text.startsWith("<clinit>("))
				&& descriptorStart >= 0) {
			return text.substring(0, descriptorStart)
					+ remapDescriptor(text.substring(descriptorStart));
		}
		if (text.startsWith("L") && text.indexOf(';') > 1) {
			int ownerEnd = text.indexOf(';');
			String sourceOwner = text.substring(1, ownerEnd);
			String sourceMember = text.substring(ownerEnd + 1);
			String targetOwner = classNames.getOrDefault(sourceOwner, sourceOwner);
			if (sourceMember.startsWith("<init>(")
					|| sourceMember.startsWith("<clinit>(")) {
				int parenthesis = sourceMember.indexOf('(');
				return "L" + targetOwner + ";" + sourceMember.substring(0, parenthesis)
						+ remapDescriptor(sourceMember.substring(parenthesis));
			}
			if (sourceMember.indexOf('(') < 0 && sourceMember.indexOf(':') > 0) {
				int colon = sourceMember.indexOf(':');
				String fieldName = sourceMember.substring(0, colon);
				String descriptor = remapDescriptor(sourceMember.substring(colon + 1));
				String targetField = lookupMember(List.of(targetOwner), fieldName);
				if (targetField == null) {
					targetField = fieldName;
				}
				return "L" + targetOwner + ";" + targetField + ":" + descriptor;
			}
			Map<String, String> ownerMembers = members.get(targetOwner);
			if (ownerMembers != null) {
				String targetMember = ownerMembers.get(sourceMember);
				if (targetMember != null) {
					return "L" + targetOwner + ";" + targetMember;
				}
			}
			for (Map<String, String> candidate : members.values()) {
				String targetMember = candidate.get(sourceMember);
				if (targetMember != null) {
					return "L" + targetOwner + ";" + targetMember;
				}
			}
		}
		String mappedMember = lookupMember(targets, text);
		if (mappedMember != null) {
			return mappedMember;
		}
		String mappedClass = classNames.get(text);
		if (mappedClass != null) {
			return mappedClass;
		}
		return text;
	}

	private String remapDescriptor(String descriptor) {
		StringBuilder result = new StringBuilder(descriptor.length());
		for (int i = 0; i < descriptor.length(); ) {
			char character = descriptor.charAt(i);
			if (character != 'L') {
				result.append(character);
				i++;
				continue;
			}
			int end = descriptor.indexOf(';', i);
			if (end < 0) {
				result.append(descriptor.substring(i));
				break;
			}
			String owner = descriptor.substring(i + 1, end);
			result.append('L').append(classNames.getOrDefault(owner, owner)).append(';');
			i = end + 1;
		}
		return result.toString();
	}

	private String lookupMember(List<String> targets, String text) {
		for (String owner : targets) {
			Map<String, String> ownerMembers = members.get(owner);
			if (ownerMembers == null) {
				continue;
			}
			String exact = ownerMembers.get(text);
			if (exact != null) {
				return exact;
			}
			if (text.indexOf('(') < 0) {
				String prefix = text + "(";
				for (Map.Entry<String, String> entry : ownerMembers.entrySet()) {
					if (entry.getKey().startsWith(prefix)) {
						int targetParen = entry.getValue().indexOf('(');
						return targetParen < 0 ? entry.getValue()
								: entry.getValue().substring(0, targetParen);
					}
				}
			}
			for (Map.Entry<String, String> entry : ownerMembers.entrySet()) {
				if (text.contains(entry.getKey())) {
					text = text.replace(entry.getKey(), entry.getValue());
				}
			}
		}
		for (Map<String, String> ownerMembers : members.values()) {
			String exact = ownerMembers.get(text);
			if (exact != null) {
				return exact;
			}
			if (text.indexOf('(') < 0) {
				String prefix = text + "(";
				for (Map.Entry<String, String> entry : ownerMembers.entrySet()) {
					if (entry.getKey().startsWith(prefix)) {
						int targetParen = entry.getValue().indexOf('(');
						return targetParen < 0 ? entry.getValue()
								: entry.getValue().substring(0, targetParen);
					}
				}
			}
		}
		return null;
	}
}
