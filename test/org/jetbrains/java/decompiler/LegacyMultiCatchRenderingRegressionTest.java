package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LegacyMultiCatchRenderingRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testPreJava7SharedHandlerDoesNotRenderJava7MultiCatch() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestLegacyMultiCatchRendering.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestLegacyMultiCatchRendering.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    assertTrue(content.contains("catch (IOException"), content);
    assertTrue(content.contains("catch (ClassNotFoundException"), content);
    assertFalse(content.contains(" | "), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestLegacyMultiCatchRendering.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestLegacyMultiCatchRendering.java");
    }
    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
