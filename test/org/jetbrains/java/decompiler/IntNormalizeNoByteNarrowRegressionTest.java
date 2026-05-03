package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntNormalizeNoByteNarrowRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testDupStoredIntCompareDoesNotInventByteNarrowing() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestIntNormalizeNoByteNarrow.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestIntNormalizeNoByteNarrow.java");

    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
    assertFalse(content.contains("byte "), content);
    assertFalse(content.contains("(byte)"), content);
    assertTrue(content.contains("int var1;"), content);
    assertTrue(content.contains("int var2 = (var1 ="), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestIntNormalizeNoByteNarrow.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestIntNormalizeNoByteNarrow.java");
    }
    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
