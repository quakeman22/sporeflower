package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SparseProtectedRangeFinallyRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testSparseProtectedLoopFinallyReturnRangeDecompiles() throws IOException {
    String content = decompileFixture();
    assertMethodDecompiled(content, "DataInputStream\\s+findStream\\s*\\(");
    compileDecompiledFixture();
  }

  @Test
  public void testSparseProtectedSplitLoopFinallyRangeDecompiles() throws IOException {
    String content = decompileFixture();
    assertMethodDecompiled(content, "int\\s+findSlot\\s*\\(");
    compileDecompiledFixture();
  }

  private String decompileFixture() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestSparseProtectedRangeFinally.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);
    return decompileClassFile(classFile, "pkg/TestSparseProtectedRangeFinally.java");
  }

  private void assertMethodDecompiled(String content, String methodPattern) {
    assertFalse(
      Pattern.compile(methodPattern + "[\\s\\S]*?\\{\\s*// \\$VF: Couldn't be decompiled").matcher(content).find(),
      content
    );
    assertFalse(content.contains("Statement cannot be decomposed although reducible"), content);
  }

  private void compileDecompiledFixture() throws IOException {
    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestSparseProtectedRangeFinally.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestSparseProtectedRangeFinally.java");
    }
    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
