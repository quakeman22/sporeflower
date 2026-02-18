package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class BytecharCastRenderingRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testBytecharPlaceholderIsNotEmittedInJavaOutput() throws IOException {
    String content = compileDecompileAndRead("pkg/TestBytecharCastRender.java", """
      package pkg;

      public class TestBytecharCastRender {
        public static void mark(Object grid, int x, int y) {
          if (((byte[]) ((Object[]) grid)[x])[y] > 0) {
            ((byte[]) ((Object[]) grid)[x])[y] = (byte) (((byte[]) ((Object[]) grid)[x])[y] | 4);
          }
        }
      }
      """);

    assertFalse(content.contains("<bytechar>"), content);
    assertFalse(content.contains("<shortchar>"), content);
    recompile();
  }
}
