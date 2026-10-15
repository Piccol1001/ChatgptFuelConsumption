package my.fuelledger.build;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import java.util.Map;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Removes ML Kit's Firelog and Play Services telemetry sinks in both APK variants.
 * OCR/model code is untouched. No reflection, runtime SDK patch, or network fallback.
 * Targets are reviewed against the pinned SDKs; dependency changes require a new audit.
 */
public abstract class OfflineMlKitTelemetry implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    public static final Map<String, String> TARGETS = Map.of(
        "com.google.android.gms.internal.mlkit_common.zzsp", "zzsb;Lcom/google/android/gms/internal/mlkit_common/zzry;",
        "com.google.android.gms.internal.mlkit_vision_common.zzmp", "zzme;Lcom/google/android/gms/internal/mlkit_vision_common/zzmb;",
        "com.google.android.gms.internal.mlkit_vision_text_common.zzuk", "zztu;Lcom/google/android/gms/internal/mlkit_vision_text_common/zztr;",
        "com.google.android.gms.internal.mlkit_vision_text_common.zzue", "GMS_TELEMETRY"
    );

    @Override public boolean isInstrumentable(ClassData data) { return TARGETS.containsKey(data.getClassName()); }

    @Override public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
        return visitor(context.getCurrentClassData().getClassName(), next);
    }

    /** Fail the build when pinned SDKs stop supplying any reviewed telemetry sink. */
    public static void verifyAars(Collection<File> archives) throws IOException {
        Set<String> found = new HashSet<>();
        for (File archive : archives) {
            if (!archive.getName().endsWith(".aar")) continue;
            try (ZipFile zip = new ZipFile(archive)) {
                var entry = zip.getEntry("classes.jar");
                if (entry == null) continue;
                try (JarInputStream jar = new JarInputStream(zip.getInputStream(entry))) {
                    java.util.jar.JarEntry cls;
                    while ((cls = jar.getNextJarEntry()) != null) {
                        String name = cls.getName().replace('/', '.').replaceFirst("\\.class$", "");
                        if (!TARGETS.containsKey(name)) continue;
                        if (!found.add(name)) throw new IllegalStateException("Duplicate reviewed ML Kit sink: " + name);
                        new ClassReader(jar).accept(visitor(name, new ClassWriter(0)), 0);
                    }
                }
            }
        }
        if (!found.equals(TARGETS.keySet()))
            throw new IllegalStateException("ML Kit dependencies changed; review offline telemetry policy: " + found);
    }

    public static ClassVisitor visitor(String name, ClassVisitor next) {
        if (!TARGETS.containsKey(name)) throw new IllegalArgumentException("Unreviewed ML Kit telemetry class");
        String prefix = name.substring(0, name.lastIndexOf('.') + 1).replace('.', '/');
        boolean gms = TARGETS.get(name).equals("GMS_TELEMETRY");
        String[] types = TARGETS.get(name).split(";L");
        String constructor = gms ? "(Landroid/content/Context;Ljava/lang/String;)V"
            : "(Landroid/content/Context;L" + prefix + types[0] + ";)V";
        String dispatch = gms ? "(IIJJ)V" : "(L" + types[1] + ")V";
        return new ClassVisitor(Opcodes.ASM9, next) {
            private int constructors;
            private int sends;
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor output = super.visitMethod(access, method, descriptor, signature, exceptions);
                boolean init = method.equals("<init>");
                boolean send = (gms ? method.equals("zzb") || method.equals("zzc") : method.equals("zza"))
                    && (access & Opcodes.ACC_STATIC) == 0;
                if (!init && !send) return output;
                String expectedDispatch = gms && method.equals("zzb") ? "(JLjava/lang/Exception;)V" : dispatch;
                if ((init && !descriptor.equals(constructor)) || (send && !descriptor.equals(expectedDispatch)))
                    throw new IllegalStateException("ML Kit telemetry signature changed; review offline build policy: " + name);
                if (init) constructors++; else sends++;
                // Drop the original implementation completely, including all transport startup.
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitEnd() {
                        output.visitCode();
                        if (init) {
                            output.visitVarInsn(Opcodes.ALOAD, 0);
                            output.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                        }
                        output.visitInsn(Opcodes.RETURN);
                        output.visitMaxs(init ? 1 : 0, Type.getArgumentsAndReturnSizes(descriptor) >> 2);
                        output.visitEnd();
                    }
                };
            }
            @Override public void visitEnd() {
                if (constructors != 1 || sends != (gms ? 2 : 1))
                    throw new IllegalStateException("ML Kit telemetry shape changed; review offline build policy: " + name);
                super.visitEnd();
            }
        };
    }
}
