import org.objectweb.asm.*;
import java.nio.file.*;

/** PvpFreigabe.erlaubt() liefert immer true - keine Server-Bestaetigung mehr noetig. */
public final class PatchPvpFreigabe {
    public static void main(String[] args) throws Exception {
        Path file = Paths.get(args[0]);
        ClassReader cr = new ClassReader(Files.readAllBytes(file));
        ClassWriter cw = new ClassWriter(0);
        boolean[] ok = {false};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                if (name.equals("erlaubt") && desc.equals("()Z")) {
                    MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.ICONST_1);
                    mv.visitInsn(Opcodes.IRETURN);
                    mv.visitMaxs(1, 0);
                    mv.visitEnd();
                    ok[0] = true;
                    return null;
                }
                return super.visitMethod(acc, name, desc, sig, ex);
            }
        }, 0);
        if (!ok[0]) throw new IllegalStateException("erlaubt() nicht gefunden");
        Files.write(file, cw.toByteArray());
        System.out.println("PvpFreigabe gepatcht");
    }
}
