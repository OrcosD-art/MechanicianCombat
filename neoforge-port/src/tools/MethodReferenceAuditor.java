import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
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

/** Reports references to missing declared/inherited members in Minecraft classes. */
public final class MethodReferenceAuditor {
	private static final class ClassInfo {
		String superName;
		final List<String> interfaces = new ArrayList<>();
		final Set<String> methods = new HashSet<>();
		final Set<String> fields = new HashSet<>();
	}

	private record Reference(String owner, String name, String descriptor) {
	}

	private final Map<String, ClassInfo> classes = new HashMap<>();
	private final List<Reference> methodReferences = new ArrayList<>();
	private final List<Reference> fieldReferences = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		MethodReferenceAuditor auditor = new MethodReferenceAuditor();
		Path input = Path.of(args[0]);
		for (int i = 1; i < args.length; i++) {
			auditor.load(Path.of(args[i]), false);
		}
		auditor.load(input, true);
		auditor.report();
	}

	private void load(Path path, boolean collectReferences) throws Exception {
		try (ZipFile zip = new ZipFile(path.toFile())) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (!entry.getName().endsWith(".class")) {
					continue;
				}
				try (InputStream stream = zip.getInputStream(entry)) {
					new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
						private String className;

						@Override
						public void visit(int version, int access, String name, String signature,
								String superName, String[] interfaces) {
							className = name;
							ClassInfo info = classes.computeIfAbsent(name,
									ignored -> new ClassInfo());
							info.superName = superName;
							if (interfaces != null) {
								for (String interfaceName : interfaces) {
									info.interfaces.add(interfaceName);
								}
							}
						}

						@Override
						public MethodVisitor visitMethod(int access, String name, String descriptor,
								String signature, String[] exceptions) {
							classes.computeIfAbsent(className, ignored -> new ClassInfo())
									.methods.add(name + descriptor);
							if (!collectReferences) {
								return null;
							}
							return new MethodVisitor(Opcodes.ASM9) {
								@Override
								public void visitMethodInsn(int opcode, String owner, String name,
										String descriptor, boolean isInterface) {
									if (owner.startsWith("net/minecraft/")) {
										methodReferences.add(new Reference(owner, name, descriptor));
									}
								}

								@Override
								public void visitFieldInsn(int opcode, String owner, String name,
										String descriptor) {
									if (owner.startsWith("net/minecraft/")) {
										fieldReferences.add(new Reference(owner, name, descriptor));
									}
								}
							};
						}

						@Override
						public FieldVisitor visitField(int access, String name, String descriptor,
								String signature, Object value) {
							classes.computeIfAbsent(className, ignored -> new ClassInfo())
									.fields.add(name + ":" + descriptor);
							return null;
						}
					}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				}
			}
		}
	}

	private void report() {
		Set<Reference> missingMethods = new HashSet<>();
		for (Reference reference : methodReferences) {
			if (!hasMethod(reference.owner, reference.name() + reference.descriptor())) {
				missingMethods.add(reference);
			}
		}
		Set<Reference> missingFields = new HashSet<>();
		for (Reference reference : fieldReferences) {
			if (!hasField(reference.owner, reference.name() + ":" + reference.descriptor())) {
				missingFields.add(reference);
			}
		}
		missingMethods.stream().sorted((a, b) -> {
			int owner = a.owner().compareTo(b.owner());
			return owner != 0 ? owner : a.name().compareTo(b.name());
		}).forEach(reference -> System.out.println("M\t" + reference.owner() + "\t"
				+ reference.name() + reference.descriptor()));
		missingFields.stream().sorted((a, b) -> {
			int owner = a.owner().compareTo(b.owner());
			return owner != 0 ? owner : a.name().compareTo(b.name());
		}).forEach(reference -> System.out.println("F\t" + reference.owner() + "\t"
				+ reference.name() + ":" + reference.descriptor()));
		System.out.printf("Missing method references: %d, fields: %d%n",
				missingMethods.size(), missingFields.size());
	}

	private boolean hasMethod(String owner, String method) {
		Set<String> visited = new HashSet<>();
		ArrayDeque<String> queue = new ArrayDeque<>();
		queue.add(owner);
		while (!queue.isEmpty()) {
			String current = queue.removeFirst();
			if (!visited.add(current)) {
				continue;
			}
			ClassInfo info = classes.get(current);
			if (info == null) {
				continue;
			}
			if (info.methods.contains(method)) {
				return true;
			}
			if (info.superName != null) {
				queue.add(info.superName);
			}
			queue.addAll(info.interfaces);
		}
		return false;
	}

	private boolean hasField(String owner, String field) {
		Set<String> visited = new HashSet<>();
		ArrayDeque<String> queue = new ArrayDeque<>();
		queue.add(owner);
		while (!queue.isEmpty()) {
			String current = queue.removeFirst();
			if (!visited.add(current)) {
				continue;
			}
			ClassInfo info = classes.get(current);
			if (info == null) {
				continue;
			}
			if (info.fields.contains(field)) {
				return true;
			}
			if (info.superName != null) {
				queue.add(info.superName);
			}
			queue.addAll(info.interfaces);
		}
		return false;
	}
}
