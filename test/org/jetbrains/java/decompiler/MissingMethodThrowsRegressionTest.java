package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MissingMethodThrowsRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testMissingCheckedThrowsAreInferredForCompilableOutput() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestMissingMethodThrows.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestMissingMethodThrows.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestMissingMethodThrows.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestMissingMethodThrows.java");
    }
    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
