package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FinalFieldCtorGapRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testFinalFieldWithoutAssignmentInAllConstructorsCompiles() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestFinalFieldCtorGap.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestFinalFieldCtorGap.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestFinalFieldCtorGap.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestFinalFieldCtorGap.java");
    }

    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
