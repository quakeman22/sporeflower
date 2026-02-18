package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InheritedInterfaceThrowsCatchRewriteRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testCatchExceptionIsKeptWhenInvocationThrowsViaSuperInterface() throws IOException {
    String content = compileDecompileAndRead("pkg/TestInheritedInterfaceThrowsCatch.java", """
      package pkg;

      import java.io.IOException;

      interface A {
        void close() throws IOException;
      }

      interface B extends A {
      }

      public class TestInheritedInterfaceThrowsCatch {
        B value;

        public void probe() {
          try {
            value.close();
          } catch (Exception e) {
          }
        }
      }
      """);

    assertTrue(content.contains("catch (Exception"), content);
    assertFalse(content.contains("substituted checked catch types: Exception"), content);
    recompile();
  }
}
