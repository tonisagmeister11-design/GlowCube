import org.objectweb.asm.*;
import java.nio.file.*;

/**
 * Freecam legt eine RemotePlayer-Kamera an, ohne ihr eine Entity-ID zu geben. Mit Iris (oder
 * allem, was getId() beim Rendern abfragt) stuerzt das Spiel dann ab: "Tried to access entity ID
 * before ID assignment". Hier bekommt die Kamera direkt nach dem Anlegen eine feste ID.
 */
public final class PatchFreecam {
    static final String KLASSE = "net/glowcube/client/module/render/Freecam";
    static final String SPIELER = "net/minecraft/client/player/RemotePlayer";
    static final String ENTITY = "net/minecraft/world/entity/Entity";

    public static void main(String[] args) throws Exception {
        Path file = Paths.get(args[0]);
        ClassReader cr = new ClassReader(Files.readAllBytes(file));
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        boolean[] ok = {false};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                if (!name.equals("onEnable")) return mv;
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitFieldInsn(int op, String owner, String n, String d) {
                        super.visitFieldInsn(op, owner, n, d);
                        if (!ok[0] && op == Opcodes.PUTFIELD && owner.equals(KLASSE) && n.equals("kamera")) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, KLASSE, "kamera", "L" + SPIELER + ";");
                            super.visitLdcInsn(Integer.MAX_VALUE - 16);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ENTITY, "setId", "(I)V", false);
                            ok[0] = true;
                        }
                    }
                };
            }
        }, 0);
        if (!ok[0]) throw new IllegalStateException("Freecam.onEnable: kamera-Zuweisung nicht gefunden");
        Files.write(file, cw.toByteArray());
        System.out.println("Freecam gepatcht");
    }
}
