import org.objectweb.asm.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Generates compilable Java stubs for every type referenced by a jar but not contained in it. */
public final class StubGen {

    static final class MethodInfo {
        final String name, desc; final boolean isStatic; final boolean isIface;
        MethodInfo(String n, String d, boolean s, boolean i) { name = n; desc = d; isStatic = s; isIface = i; }
    }

    static final class TypeInfo {
        final String internal;
        boolean iface;
        boolean annotation;
        String superName;                       // internal name or null
        final Set<String> ifaces = new LinkedHashSet<>();
        final Map<String, MethodInfo> methods = new LinkedHashMap<>();
        final Map<String, String> staticFields = new LinkedHashMap<>();
        final Map<String, String> instanceFields = new LinkedHashMap<>();
        final Map<String, String> annMembers = new LinkedHashMap<>();
        TypeInfo(String internal) { this.internal = internal; }
    }

    static final Map<String, TypeInfo> types = new TreeMap<>();
    static final Set<String> own = new HashSet<>();
    /** methods/fields referenced on one of our own classes but not declared there -> resolved later */
    static final Map<String, Set<MethodInfo>> pendingMethods = new HashMap<>();
    static final Map<String, Map<String, String>> pendingStaticFields = new HashMap<>();
    static final Map<String, Map<String, String>> pendingInstanceFields = new HashMap<>();
    static final Map<String, String> ownSuper = new HashMap<>();
    static final Map<String, Set<String>> ownDeclaredMethods = new HashMap<>();
    static final Map<String, Set<String>> ownDeclaredFields = new HashMap<>();

    static boolean isOwn(String internal) { return own.contains(internal); }

    static boolean skip(String internal) {
        return internal.startsWith("java/") || internal.startsWith("javax/") || internal.startsWith("jdk/")
                || internal.startsWith("sun/") || internal.startsWith("[") || isOwn(internal);
    }

    static TypeInfo type(String internal) {
        return types.computeIfAbsent(internal, TypeInfo::new);
    }

    static void noteType(String internal) {
        if (internal == null) return;
        if (internal.startsWith("[")) { noteDesc(internal); return; }
        if (skip(internal)) return;
        type(internal);
    }

    static void noteDesc(String desc) {
        if (desc == null) return;
        if (desc.startsWith("(")) {
            for (Type t : Type.getArgumentTypes(desc)) noteAsmType(t);
            noteAsmType(Type.getReturnType(desc));
        } else {
            noteAsmType(Type.getType(desc));
        }
    }

    static void noteAsmType(Type t) {
        while (t.getSort() == Type.ARRAY) t = t.getElementType();
        if (t.getSort() == Type.OBJECT) noteType(t.getInternalName());
    }

    static void addMethod(String owner, String name, String desc, boolean isStatic, boolean isIface) {
        if (owner.startsWith("[")) return;
        noteDesc(desc);
        if (isOwn(owner)) {
            String key = name + desc;
            if (ownDeclaredMethods.getOrDefault(owner, Set.of()).contains(key)) return;
            pendingMethods.computeIfAbsent(owner, k -> new LinkedHashSet<>())
                    .add(new MethodInfo(name, desc, isStatic, isIface));
            return;
        }
        if (skip(owner)) return;
        TypeInfo ti = type(owner);
        if (isIface) ti.iface = true;
        ti.methods.putIfAbsent(name + desc, new MethodInfo(name, desc, isStatic, isIface));
    }

    static void addField(String owner, String name, String desc, boolean isStatic) {
        if (owner.startsWith("[")) return;
        noteDesc(desc);
        if (isOwn(owner)) {
            if (ownDeclaredFields.getOrDefault(owner, Set.of()).contains(name)) return;
            (isStatic ? pendingStaticFields : pendingInstanceFields)
                    .computeIfAbsent(owner, k -> new LinkedHashMap<>()).put(name, desc);
            return;
        }
        if (skip(owner)) return;
        TypeInfo ti = type(owner);
        (isStatic ? ti.staticFields : ti.instanceFields).putIfAbsent(name, desc);
    }

    public static void main(String[] args) throws Exception {
        Path jar = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]);

        List<byte[]> classes = new ArrayList<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                JarEntry je = e.nextElement();
                if (!je.getName().endsWith(".class")) continue;
                try (InputStream in = jf.getInputStream(je)) { classes.add(in.readAllBytes()); }
                own.add(je.getName().substring(0, je.getName().length() - 6));
            }
        }
        // pass 1: record what our own classes declare
        for (byte[] b : classes) new ClassReader(b).accept(new DeclScanner(), ClassReader.SKIP_CODE);
        // pass 2: collect references
        for (byte[] b : classes) new ClassReader(b).accept(new RefScanner(), 0);
        // resolve members referenced on our own classes -> nearest external supertype
        resolvePending();

        applyOverrides();

        emit(outDir);
        System.out.println("stub types: " + types.size());
    }

    static void resolvePending() {
        for (Map.Entry<String, Set<MethodInfo>> e : pendingMethods.entrySet()) {
            String host = externalHost(e.getKey());
            if (host == null) continue;
            TypeInfo ti = type(host);
            for (MethodInfo mi : e.getValue()) ti.methods.putIfAbsent(mi.name + mi.desc, mi);
        }
        for (Map.Entry<String, Map<String, String>> e : pendingStaticFields.entrySet()) {
            String host = externalHost(e.getKey());
            if (host == null) continue;
            type(host).staticFields.putAll(e.getValue());
        }
        for (Map.Entry<String, Map<String, String>> e : pendingInstanceFields.entrySet()) {
            String host = externalHost(e.getKey());
            if (host == null) continue;
            type(host).instanceFields.putAll(e.getValue());
        }
    }

    /** walk up our own hierarchy to the first external superclass */
    static String externalHost(String ownInternal) {
        String cur = ownSuper.get(ownInternal);
        while (cur != null && isOwn(cur)) cur = ownSuper.get(cur);
        if (cur == null || skip(cur)) return null;
        return cur;
    }

    static final class DeclScanner extends ClassVisitor {
        String name;
        DeclScanner() { super(Opcodes.ASM9); }
        @Override public void visit(int v, int a, String n, String s, String sup, String[] ifs) {
            name = n; ownSuper.put(n, sup);
        }
        @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
            ownDeclaredMethods.computeIfAbsent(name, k -> new HashSet<>()).add(n + d);
            return null;
        }
        @Override public FieldVisitor visitField(int a, String n, String d, String s, Object val) {
            ownDeclaredFields.computeIfAbsent(name, k -> new HashSet<>()).add(n);
            return null;
        }
    }

    static final class AnnScanner extends AnnotationVisitor {
        final String annType;
        AnnScanner(String annType) { super(Opcodes.ASM9); this.annType = annType; }
        @Override public void visit(String name, Object value) {
            if (name == null) return;
            String d;
            if (value instanceof Type t) d = "Ljava/lang/Class;";
            else if (value instanceof String) d = "Ljava/lang/String;";
            else if (value instanceof Boolean) d = "Z";
            else if (value instanceof Integer) d = "I";
            else if (value instanceof Long) d = "J";
            else if (value instanceof Double) d = "D";
            else if (value instanceof Float) d = "F";
            else return;
            TypeInfo ti = type(annType);
            ti.annotation = true;
            ti.annMembers.putIfAbsent(name, d);
        }
        @Override public void visitEnum(String name, String desc, String value) {
            noteDesc(desc);
            if (name == null) return;
            TypeInfo ti = type(annType);
            ti.annotation = true;
            ti.annMembers.putIfAbsent(name, desc);
            addField(Type.getType(desc).getInternalName(), value, desc, true);
        }
        @Override public AnnotationVisitor visitAnnotation(String name, String desc) { noteDesc(desc); return null; }
        @Override public AnnotationVisitor visitArray(String name) { return this; }
    }

    static AnnotationVisitor ann(String desc) {
        noteDesc(desc);
        if (desc == null || !desc.startsWith("L")) return null;
        String internal = Type.getType(desc).getInternalName();
        if (skip(internal)) return null;
        TypeInfo ti = type(internal);
        ti.annotation = true;
        return new AnnScanner(internal);
    }

    static final class RefScanner extends ClassVisitor {
        RefScanner() { super(Opcodes.ASM9); }
        @Override public void visit(int v, int a, String n, String s, String sup, String[] ifs) {
            noteType(sup);
            if (ifs != null) for (String i : ifs) { noteType(i); if (!skip(i)) type(i).iface = true; }
        }
        @Override public AnnotationVisitor visitAnnotation(String d, boolean vis) { return ann(d); }
        @Override public FieldVisitor visitField(int a, String n, String d, String s, Object val) {
            noteDesc(d);
            return new FieldVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String dd, boolean vv) { return ann(dd); }
            };
        }
        @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
            noteDesc(d);
            if (ex != null) for (String e : ex) noteType(e);
            return new MethodVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String dd, boolean vv) { return ann(dd); }
                @Override public AnnotationVisitor visitParameterAnnotation(int p, String dd, boolean vv) { return ann(dd); }
                @Override public void visitTypeInsn(int op, String t) { noteType(t); }
                @Override public void visitMultiANewArrayInsn(String d2, int dims) { noteDesc(d2); }
                @Override public void visitLdcInsn(Object cst) {
                    if (cst instanceof Type t) noteAsmType(t);
                    else if (cst instanceof Handle h) handle(h);
                }
                @Override public void visitTryCatchBlock(Label a1, Label b, Label c, String t) { noteType(t); }
                @Override public void visitLocalVariable(String n2, String d2, String s2, Label a1, Label b, int i) { noteDesc(d2); }
                @Override public void visitFieldInsn(int op, String o, String n2, String d2) {
                    addField(o, n2, d2, op == Opcodes.GETSTATIC || op == Opcodes.PUTSTATIC);
                }
                @Override public void visitMethodInsn(int op, String o, String n2, String d2, boolean itf) {
                    addMethod(o, n2, d2, op == Opcodes.INVOKESTATIC, itf);
                }
                @Override public void visitInvokeDynamicInsn(String n2, String d2, Handle bsm, Object... bsmArgs) {
                    noteDesc(d2);
                    handle(bsm);
                    for (Object o : bsmArgs) {
                        if (o instanceof Handle h) handle(h);
                        else if (o instanceof Type t) { if (t.getSort() == Type.METHOD) noteDesc(t.getDescriptor()); else noteAsmType(t); }
                    }
                }
                void handle(Handle h) {
                    if (h.getTag() == Opcodes.H_GETFIELD || h.getTag() == Opcodes.H_PUTFIELD) addField(h.getOwner(), h.getName(), h.getDesc(), false);
                    else if (h.getTag() == Opcodes.H_GETSTATIC || h.getTag() == Opcodes.H_PUTSTATIC) addField(h.getOwner(), h.getName(), h.getDesc(), true);
                    else addMethod(h.getOwner(), h.getName(), h.getDesc(), h.getTag() == Opcodes.H_INVOKESTATIC, h.isInterface());
                }
            };
        }
    }

    // ---------------- overrides ----------------

    static void applyOverrides() {
        // make sure every type mentioned by an override exists before it is configured
        for (String i : Overrides.INTERFACES) noteType(i);
        for (String i : Overrides.CLASSES) noteType(i);
        for (String i : Overrides.ENUMS) noteType(i);
        Overrides.HIERARCHY.keySet().forEach(StubGen::noteType);
        Overrides.EXTRA_METHODS.keySet().forEach(StubGen::noteType);
        Overrides.EXTRA_FIELDS.keySet().forEach(StubGen::noteType);
        Overrides.SOURCE_METHODS.keySet().forEach(StubGen::noteType);
        for (String i : Overrides.INTERFACES) if (types.containsKey(i)) type(i).iface = true;
        for (String i : Overrides.CLASSES) if (types.containsKey(i)) { type(i).iface = false; type(i).annotation = false; }
        Overrides.HIERARCHY.forEach((child, parents) -> {
            TypeInfo ti = types.get(child);
            if (ti == null) return;
            for (String p : parents) {
                noteType(p);
                TypeInfo pt = types.get(p);
                if (ti.iface) { if (pt != null) pt.iface = true; ti.ifaces.add(p); }
                else if (pt != null && pt.iface) ti.ifaces.add(p);
                else ti.superName = p;
            }
        });
        Overrides.EXTRA_METHODS.forEach((owner, sigs) -> {
            noteType(owner);
            TypeInfo ti = types.get(owner);
            if (ti == null) return;
            for (String sig : sigs) {
                boolean st = sig.startsWith("static ");
                String s = st ? sig.substring(7) : sig;
                int p = s.indexOf('(');
                String name = s.substring(0, p);
                String desc = s.substring(p);
                noteDesc(desc);
                ti.methods.putIfAbsent(name + desc, new MethodInfo(name, desc, st, ti.iface));
            }
        });
        Overrides.EXTRA_FIELDS.forEach((owner, fields) -> {
            noteType(owner);
            TypeInfo ti = types.get(owner);
            if (ti == null) return;
            fields.forEach((n, d) -> { noteDesc(d); ti.staticFields.putIfAbsent(n, d); });
        });
    }

    // ---------------- emission ----------------

    static String src(String internal) {
        return internal.replace('/', '.').replace('$', '.');
    }

    static String typeName(Type t) {
        switch (t.getSort()) {
            case Type.VOID: return "void";
            case Type.BOOLEAN: return "boolean";
            case Type.CHAR: return "char";
            case Type.BYTE: return "byte";
            case Type.SHORT: return "short";
            case Type.INT: return "int";
            case Type.FLOAT: return "float";
            case Type.LONG: return "long";
            case Type.DOUBLE: return "double";
            case Type.ARRAY: return typeName(t.getElementType()) + "[]".repeat(t.getDimensions());
            default: return src(t.getInternalName());
        }
    }

    static String typeName(String desc) { return typeName(Type.getType(desc)); }

    static String defaultValue(Type t) {
        switch (t.getSort()) {
            case Type.VOID: return null;
            case Type.BOOLEAN: return "false";
            case Type.CHAR: return "'\\0'";
            case Type.BYTE: case Type.SHORT: case Type.INT: return "0";
            case Type.FLOAT: return "0.0f";
            case Type.LONG: return "0L";
            case Type.DOUBLE: return "0.0d";
            default: return "null";
        }
    }

    static void emit(Path outDir) throws IOException {
        // group by top-level type
        Map<String, List<String>> byTop = new TreeMap<>();
        for (String internal : types.keySet()) {
            String top = internal.contains("$") ? internal.substring(0, internal.indexOf('$')) : internal;
            byTop.computeIfAbsent(top, k -> new ArrayList<>()).add(internal);
        }
        for (Map.Entry<String, List<String>> e : byTop.entrySet()) {
            String top = e.getKey();
            String pkg = top.contains("/") ? top.substring(0, top.lastIndexOf('/')).replace('/', '.') : "";
            StringBuilder sb = new StringBuilder();
            if (!pkg.isEmpty()) sb.append("package ").append(pkg).append(";\n\n");
            List<String> members = new ArrayList<>(e.getValue());
            members.sort(Comparator.naturalOrder());
            writeType(sb, top, members, "");
            Path f = outDir.resolve(top + ".java");
            Files.createDirectories(f.getParent());
            Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
        }
    }

    static void writeType(StringBuilder sb, String internal, List<String> all, String indent) {
        TypeInfo ti = types.get(internal);
        String simple = internal.contains("$") ? internal.substring(internal.lastIndexOf('$') + 1)
                : internal.substring(internal.lastIndexOf('/') + 1);
        boolean nested = !indent.isEmpty();
        if (ti == null) { // synthetic outer holder
            sb.append(indent).append("public ").append(nested ? "static " : "").append("abstract class ").append(simple).append(" {\n");
            writeNested(sb, internal, all, indent);
            sb.append(indent).append("}\n");
            return;
        }
        if (ti.annotation) {
            sb.append(indent).append("public @interface ").append(simple).append(" {\n");
            for (Map.Entry<String, String> m : ti.annMembers.entrySet()) {
                Type t = Type.getType(m.getValue());
                String def = t.getSort() == Type.OBJECT && !m.getValue().equals("Ljava/lang/String;") && !m.getValue().equals("Ljava/lang/Class;")
                        ? null : defaultValue(t);
                sb.append(indent).append("    ").append(typeName(t)).append(" ").append(m.getKey()).append("()");
                if (t.getSort() == Type.OBJECT) {
                    if (m.getValue().equals("Ljava/lang/String;")) sb.append(" default \"\"");
                    else if (m.getValue().equals("Ljava/lang/Class;")) sb.append(" default Object.class");
                    // enum member: default the first known constant
                    else {
                        String ei = t.getInternalName();
                        TypeInfo et = types.get(ei);
                        if (et != null && !et.staticFields.isEmpty())
                            sb.append(" default ").append(src(ei)).append(".").append(et.staticFields.keySet().iterator().next());
                    }
                } else if (def != null) sb.append(" default ").append(def);
                sb.append(";\n");
            }
            sb.append(indent).append("}\n");
            return;
        }
        if (Overrides.ENUMS.contains(internal)) { writeEnum(sb, ti, simple, indent, internal, all); return; }
        sb.append(indent).append("public ").append(nested ? "static " : "");
        sb.append(ti.iface ? "interface " : "abstract class ").append(simple);
        if (!ti.iface && ti.superName != null && !ti.superName.equals("java/lang/Object")) {
            sb.append(" extends ").append(src(ti.superName));
        }
        if (!ti.ifaces.isEmpty()) {
            sb.append(ti.iface ? " extends " : " implements ");
            sb.append(String.join(", ", ti.ifaces.stream().map(StubGen::src).toList()));
        }
        sb.append(" {\n");
        String ind = indent + "    ";

        for (Map.Entry<String, String> f : ti.staticFields.entrySet()) {
            if (ti.iface) sb.append(ind).append(typeName(f.getValue())).append(" ").append(f.getKey())
                    .append(" = ").append(defaultValue(Type.getType(f.getValue()))).append(";\n");
            else sb.append(ind).append("public static ").append(typeName(f.getValue())).append(" ").append(f.getKey()).append(";\n");
        }
        if (!ti.iface) {
            for (Map.Entry<String, String> f : ti.instanceFields.entrySet())
                sb.append(ind).append("public ").append(typeName(f.getValue())).append(" ").append(f.getKey()).append(";\n");
        }

        boolean hasNoArgCtor = false;
        for (MethodInfo mi : ti.methods.values())
            if (mi.name.equals("<init>") && mi.desc.equals("()V")) hasNoArgCtor = true;
        if (!ti.iface && !hasNoArgCtor) sb.append(ind).append("public ").append(simple).append("() {}\n");

        Set<String> emitted = new HashSet<>();
        for (Map.Entry<String, String> so : Overrides.SOURCE_METHODS.getOrDefault(internal, Map.of()).entrySet()) {
            emitted.add(so.getKey());
            sb.append(ind).append(so.getValue()).append("\n");
        }
        for (MethodInfo mi : ti.methods.values()) {
            Type[] params = Type.getArgumentTypes(mi.desc);
            StringBuilder ps = new StringBuilder();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) ps.append(", ");
                ps.append(typeName(params[i])).append(" a").append(i);
            }
            String erasureKey = mi.name + "|" + Type.getMethodDescriptor(Type.VOID_TYPE, params);
            if (!emitted.add(erasureKey)) continue;
            if (mi.name.equals("<init>")) {
                if (ti.iface) continue;
                sb.append(ind).append("public ").append(simple).append("(").append(ps).append(") {}\n");
            } else if (mi.name.equals("<clinit>")) {
                continue;
            } else {
                Type ret = Type.getReturnType(mi.desc);
                sb.append(ind);
                if (ti.iface) { if (mi.isStatic) sb.append("static "); }
                else sb.append("public ").append(mi.isStatic ? "static " : "");
                sb.append(typeName(ret)).append(" ").append(mi.name).append("(").append(ps).append(")");
                if (ti.iface && !mi.isStatic) { sb.append(";\n"); }
                else {
                    sb.append(" { ");
                    String dv = defaultValue(ret);
                    if (dv != null) sb.append("return ").append(dv).append("; ");
                    sb.append("}\n");
                }
            }
        }
        writeNested(sb, internal, all, indent);
        sb.append(indent).append("}\n");
    }


    static void writeEnum(StringBuilder sb, TypeInfo ti, String simple, String indent, String internal, List<String> all) {
        String self = "L" + internal + ";";
        sb.append(indent).append("public enum ").append(simple).append(" {\n");
        String ind = indent + "    ";
        List<String> consts = new ArrayList<>();
        for (Map.Entry<String, String> f : ti.staticFields.entrySet())
            if (self.equals(f.getValue())) consts.add(f.getKey());
        if (consts.isEmpty()) consts.add("PLACEHOLDER");
        sb.append(ind).append(String.join(", ", consts)).append(";\n");
        for (Map.Entry<String, String> f : ti.staticFields.entrySet())
            if (!self.equals(f.getValue()))
                sb.append(ind).append("public static ").append(typeName(f.getValue())).append(" ").append(f.getKey()).append(";\n");
        Set<String> skipNames = Set.of("values", "valueOf", "name", "ordinal", "<init>", "<clinit>", "compareTo", "getDeclaringClass");
        Set<String> emitted = new HashSet<>();
        for (MethodInfo mi : ti.methods.values()) {
            if (skipNames.contains(mi.name)) continue;
            Type[] params = Type.getArgumentTypes(mi.desc);
            if (!emitted.add(mi.name + "|" + Type.getMethodDescriptor(Type.VOID_TYPE, params))) continue;
            StringBuilder ps = new StringBuilder();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) ps.append(", ");
                ps.append(typeName(params[i])).append(" a").append(i);
            }
            Type ret = Type.getReturnType(mi.desc);
            sb.append(ind).append("public ").append(mi.isStatic ? "static " : "").append(typeName(ret))
              .append(" ").append(mi.name).append("(").append(ps).append(") { ");
            String dv = defaultValue(ret);
            if (dv != null) sb.append("return ").append(dv).append("; ");
            sb.append("}\n");
        }
        writeNested(sb, internal, all, indent);
        sb.append(indent).append("}\n");
    }

    static void writeNested(StringBuilder sb, String internal, List<String> all, String indent) {
        String prefix = internal + "$";
        for (String cand : all) {
            if (!cand.startsWith(prefix)) continue;
            if (cand.indexOf('$', prefix.length()) >= 0) continue; // deeper level handled recursively
            writeType(sb, cand, all, indent + "    ");
        }
    }
}
