package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteArrayMergeRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testByteArrayVariableKeepsArrayTypeAfterNullInitialization() throws IOException {
    String content = compileDecompileAndRead("pkg/TestByteArrayMerge.java", """
package pkg;

public class TestByteArrayMerge {
  private static byte[] decode(int size, int step) {
    byte[] pixels = null;
    if (size > 0) {
      pixels = new byte[size];
    } else {
      pixels = new byte[1];
    }

    int value = step & 255;
    for (int i = 0; i < pixels.length; i++) {
      pixels[i] = (byte)value;
    }

    return pixels;
  }
}
""");

    assertTrue(content.contains("private static byte[] decode"), content);
    assertTrue(Pattern.compile("\\bbyte\\[]\\s+var\\d+\\s*=\\s*null;").matcher(content).find(), content);
    assertFalse(Pattern.compile("\\bB\\s+var\\d+\\s*=\\s*null;").matcher(content).find(), content);
    assertFalse(content.contains("(Object[])"), content);
  }

  @Test
  public void testLegacyStackMapObjectFrameKeepsPrimitiveArrayMergeAtObject() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestPrimitiveArrayObjectMergeStackMap.class");
    String content = assertTimeout(Duration.ofSeconds(10), () ->
      decompileClassFile(classFile, "pkg/TestPrimitiveArrayObjectMergeStackMap.java"));

    assertTrue(content.contains("public static Object decode"), content);
    assertFalse(Pattern.compile("\\bObject\\[]\\s+var\\d+;").matcher(content).find(), content);

    assertTimeout(Duration.ofSeconds(10), () -> {
      recompile();
    });
  }
}
