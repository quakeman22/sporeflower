package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BroadCatchUnknownInvocationNoBroadThrowsRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testPrivateHelperDoesNotInferBroadExceptionFromCatchAllCallsite() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestBroadCatchUnknownInvocation.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestBroadCatchUnknownInvocation.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
    assertFalse(content.contains("helper() throws Exception"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestBroadCatchUnknownInvocation.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestBroadCatchUnknownInvocation.java");
    }

    Path missingApiSource = writeSource("pkg/MissingApi.java", """
package pkg;

public class MissingApi {
  public static void ping() {
  }
}
""");

    compileJava8(List.of(decompiledFile, missingApiSource), fixture.getTempDir().resolve("compile-out"));
  }
}
