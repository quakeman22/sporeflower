package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompoundIntMultiplierNoByteNarrowRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testCompoundMultiplierKeepsOriginalIntStoreSemantics() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestCompoundIntMultiplierNoByteNarrow.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestCompoundIntMultiplierNoByteNarrow.java");

    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
    assertFalse(content.contains("byte var3"), content);
    assertFalse(content.contains("(byte)var3"), content);
    assertTrue(content.contains("int var3 = 1;"), content);
    assertTrue(content.contains("var3 *= 10"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestCompoundIntMultiplierNoByteNarrow.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestCompoundIntMultiplierNoByteNarrow.java");
    }
    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
