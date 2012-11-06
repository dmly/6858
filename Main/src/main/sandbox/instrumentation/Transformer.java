package sandbox.instrumentation;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class which maintains static state (e.g. storing the javaagent's
 * sandbox.instrumentation object and transformMe map) and provides instances
 * which can be used by the sandbox.instrumentation to transform loaded classes
 */
public class Transformer implements ClassFileTransformer {
    public static Set<Class<?>> transformMe = Collections.newSetFromMap(new ConcurrentHashMap<Class<?>,Boolean>());

    static {
        agent.JavaAgent.instrumentation.addTransformer(new Transformer(), agent.JavaAgent.instrumentation.isRetransformClassesSupported());
    }

    private Transformer() {}

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> cls,
                            ProtectionDomain protectionDomain,
                            byte[] origBytes) {
        /**
         * Skip instrumenting classes which cause problems.
         *
         * Not sure why all these guys need to be skipped, but they cause
         * red words to appear if they're not. Ideally we would figure out why.
         */
        if (className.startsWith("java/lang/ThreadLocal")) {
            return null;
        } else if (className.startsWith("java/lang/Class")) {
            return null;
        } else if (className.startsWith("java/lang/reflect/Array")) {
            return null;
        }else if (className.startsWith("sandbox/")){
            return null;
        } else  if (!transformMe.contains(cls)) {
            System.out.println("Not Transforming!");
            return null;
        } else {
            return instrument(origBytes, loader);
        }
    }

    /**
     * Given the bytes representing a class, go through all the bytecode in it and
     * instrument any occurences of new/newarray/anewarray/multianewarray with
     * pre- and post-allocation hooks.  Even more fun, intercept calls to the
     * reflection API's Array.newInstance() and instrument those too.
     *
     * @param originalBytes the original <code>byte[]</code> code.
     * @return the instrumented <code>byte[]</code> code.
     */
    private static byte[] instrument(byte[] originalBytes, ClassLoader loader) {
        try {

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

            ClassAdapter adapter = new ClassAdapter(cw);

            ClassReader cr = new ClassReader(originalBytes);

            cr.accept(adapter, ClassReader.SKIP_FRAMES);
            return cw.toByteArray();
        } catch (Exception e) {
            System.out.println("Failed to instrument class: " + e);
            e.printStackTrace();
            throw e;
        } catch (Error e) {
            System.out.println("Failed to instrument class: " + e);
            e.printStackTrace();
            throw e;
        }
    }
}