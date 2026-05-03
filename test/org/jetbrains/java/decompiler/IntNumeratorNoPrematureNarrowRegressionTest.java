package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntNumeratorNoPrematureNarrowRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testIntegerNumeratorsStayWideUntilExplicitArrayStoreNarrowing() throws IOException {
    String content = compileDecompileAndRead(
      "pkg/TestIntNumeratorNoPrematureNarrow.java",
      """
        package pkg;

        public class TestIntNumeratorNoPrematureNarrow {
          static byte mission;

          public static void shortScale(short[] out, int distance, int h) {
            int numerator = 8000;
            if (distance > 0) {
              numerator = mission == -5 ? 40000 : 20000;
            }
            out[7] = (short)(numerator / ((distance << 1) + Math.min(h, 208) + 10));
          }

          public static void byteScale(byte[] out, int distance) {
            int numerator = 12;
            if (distance > 0) {
              numerator = mission == -5 ? 1000 : 200;
            }
            out[1] = (byte)(numerator / (distance + 3));
          }

          public static void charScale(char[] out, int distance) {
            int numerator = 65;
            if (distance > 0) {
              numerator = mission == -5 ? 70000 : 40000;
            }
            out[2] = (char)(numerator / (distance + 3));
          }
        }
        """
    );

    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
    assertFalse(content.contains("short var3 = 8000"), content);
    assertFalse(content.contains("byte var3 = 12"), content);
    assertFalse(content.contains("char var3 = 65"), content);
    assertFalse(content.contains("= (short)(mission == -5 ? 40000 : 20000)"), content);
    assertFalse(content.contains("= (byte)(mission == -5 ? 1000 : 200)"), content);
    assertFalse(content.contains("= (char)(mission == -5 ? 70000 : 40000)"), content);

    assertTrue(content.contains("int var3 = 8000;"), content);
    assertTrue(content.contains("var3 = mission == -5 ? 40000 : 20000;"), content);
    assertTrue(content.contains("int var2 = 12;"), content);
    assertTrue(content.contains("var2 = mission == -5 ? 1000 : 200;"), content);
    assertTrue(content.contains("int var2 = 65;"), content);
    assertTrue(content.contains("var2 = mission == -5 ? 70000 : 40000;"), content);

    recompile();
  }
}
