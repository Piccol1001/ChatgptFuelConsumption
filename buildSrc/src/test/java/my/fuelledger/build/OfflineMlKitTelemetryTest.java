package my.fuelledger.build;

import static org.junit.Assert.*;
import org.junit.Test;
import org.objectweb.asm.*;

public class OfflineMlKitTelemetryTest {
    @Test public void transportStartupAndSendAreRemovedForAllPinnedSinks() {
        verify("com.google.android.gms.internal.mlkit_common.zzsp", "com/google/android/gms/internal/mlkit_common/zzsb", "com/google/android/gms/internal/mlkit_common/zzry");
        verify("com.google.android.gms.internal.mlkit_vision_common.zzmp", "com/google/android/gms/internal/mlkit_vision_common/zzme", "com/google/android/gms/internal/mlkit_vision_common/zzmb");
        verify("com.google.android.gms.internal.mlkit_vision_text_common.zzuk", "com/google/android/gms/internal/mlkit_vision_text_common/zztu", "com/google/android/gms/internal/mlkit_vision_text_common/zztr");
    }

    private void verify(String target, String configuration, String event) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor policy = OfflineMlKitTelemetry.visitor(target, writer);
        policy.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, target.replace('.', '/'), null, "java/lang/Object", null);
        MethodVisitor constructor = policy.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Landroid/content/Context;L" + configuration + ";)V", null, null);
        constructor.visitCode();
        constructor.visitMethodInsn(Opcodes.INVOKESTATIC, "forbidden/TransportRuntime", "initialize", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(0, 3); constructor.visitEnd();
        MethodVisitor send = policy.visitMethod(Opcodes.ACC_PUBLIC, "zza", "(L" + event + ";)V", null, null);
        send.visitCode();
        send.visitMethodInsn(Opcodes.INVOKESTATIC, "forbidden/Transport", "send", "()V", false);
        send.visitInsn(Opcodes.RETURN); send.visitMaxs(0, 2); send.visitEnd();
        policy.visitEnd();
        final int[] methods = {0};
        new ClassReader(writer.toByteArray()).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        assertEquals("Only the Object constructor may remain", "java/lang/Object", owner);
                        assertEquals("<init>", name);
                    }
                };
            }
        }, 0);
        assertEquals(2, methods[0]);
    }

    @Test public void changedSdkSignatureFailsBuild() {
        ClassVisitor policy = OfflineMlKitTelemetry.visitor("com.google.android.gms.internal.mlkit_common.zzsp", new ClassWriter(0));
        assertThrows(IllegalStateException.class, () -> policy.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null));
    }

    @Test public void playServicesTelemetryCannotInitializeOrLog() {
        String name = "com.google.android.gms.internal.mlkit_vision_text_common.zzue";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor policy = OfflineMlKitTelemetry.visitor(name, writer);
        policy.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
        String[][] methods = {{"<init>", "(Landroid/content/Context;Ljava/lang/String;)V"},
            {"zzb", "(JLjava/lang/Exception;)V"}, {"zzc", "(IIJJ)V"}};
        for (String[] method : methods) {
            MethodVisitor mv = policy.visitMethod(Opcodes.ACC_PUBLIC, method[0], method[1], null, null);
            mv.visitCode();
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "forbidden/TelemetryLogging", "getClient", "()V", false);
            mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(0, 7); mv.visitEnd();
        }
        policy.visitEnd();
        new ClassReader(writer.toByteArray()).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String owner, String method, String desc, boolean itf) {
                        assertEquals("java/lang/Object", owner);
                        assertEquals("<init>", method);
                    }
                };
            }
        }, 0);
    }

    @Test public void missingDispatchFailsBuild() {
        ClassVisitor policy = OfflineMlKitTelemetry.visitor("com.google.android.gms.internal.mlkit_common.zzsp", new ClassWriter(0));
        assertThrows(IllegalStateException.class, policy::visitEnd);
    }
}
