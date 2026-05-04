package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaticInitializerOrderRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void preservesStaticAssignmentAfterConstructorSideEffect() throws IOException {
    String content = compileDecompileAndRead("pkg/TestStaticInitializerOrder.java", """
      package pkg;

      import java.util.Vector;

      public final class TestStaticInitializerOrder {
        private static Object cache;
        private static int master;
        private static int effective;
        private static boolean wav;
        private static int current;
        private static int loop;
        private static byte[] preload;
        private static String[] supported;
        private static boolean enabled;

        private TestStaticInitializerOrder() {
          supported = new String[]{"audio/x-wav"};
          wav = true;
        }

        public static String[] supported() {
          return supported;
        }

        static {
          cache = new Object();
          new Vector();
          master = effective = 100;
          wav = false;
          current = -1;
          loop = 1;
          preload = null;
          new TestStaticInitializerOrder();
          supported = null;
          enabled = true;
        }
      }
      """);

    assertFalse(content.contains("private static String[] supported = null;"), content);

    int constructorCall = content.indexOf("new TestStaticInitializerOrder();");
    int clearAfterProbe = content.indexOf("supported = null;");
    assertTrue(constructorCall >= 0, content);
    assertTrue(clearAfterProbe > constructorCall, content);
  }
}
