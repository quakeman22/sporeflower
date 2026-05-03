package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BroadCatchWithoutDeclaredThrowsRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testBroadExceptionAndThrowableCatchesAreNotNarrowedToRuntimeException() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestBroadCatchWithoutDeclaredThrows.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestBroadCatchWithoutDeclaredThrows.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    assertTrue(content.contains("catch (Exception"), content);
    assertTrue(content.contains("catch (Throwable"), content);
    assertFalse(content.contains("catch (RuntimeException"), content);
    assertFalse(content.contains("substituted checked catch types"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestBroadCatchWithoutDeclaredThrows.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestBroadCatchWithoutDeclaredThrows.java");
    }
    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
