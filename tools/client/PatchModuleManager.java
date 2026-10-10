import org.objectweb.asm.*;
import java.nio.file.*;

/**
 * Haengt Legendora direkt hinter KillAura in die Modulliste des ModuleManager.
 * Der Rest der Klasse bleibt Byte fuer Byte erhalten.
 */
public final class PatchModuleManager {
    static final String MM = "net/glowcube/client/core/ModuleManager";
    static final String KILL = "net/glowcube/client/module/combat/KillAura";
    static final String NEU = "net/glowcube/client/module/combat/Legendora";

    public static void main(String[] args) throws Exception {
        Path file = Paths.get(args[0]);
        ClassReader cr = new ClassReader(Files.readAllBytes(file));
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        boolean[] gepatcht = {false};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                if (!name.equals("<init>")) return mv;
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    boolean killAura;
                    @Override
                    public void visitTypeInsn(int op, String type) {
                        if (op == Opcodes.NEW && type.equals(KILL)) killAura = true;
                        super.visitTypeInsn(op, type);
                    }
                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        super.visitMethodInsn(op, owner, n, d, itf);
                        if (killAura && !gepatcht[0] && owner.equals(MM) && n.equals("add")) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitTypeInsn(Opcodes.NEW, NEU);
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESPECIAL, NEU, "<init>", "()V", false);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MM, "add", d, false);
                            gepatcht[0] = true;
                        }
                    }
                };
            }
        }, 0);
        if (!gepatcht[0]) throw new IllegalStateException("KillAura-Eintrag im ModuleManager nicht gefunden");
        Files.write(file, cw.toByteArray());
        System.out.println("ModuleManager gepatcht");
    }
}
