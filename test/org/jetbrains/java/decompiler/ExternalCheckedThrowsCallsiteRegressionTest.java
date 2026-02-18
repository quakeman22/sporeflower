package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExternalCheckedThrowsCallsiteRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testCheckedThrowsInferredForSameClassMethodInvokingExternalApi() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestExternalCheckedThrowsCallsite.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestExternalCheckedThrowsCallsite.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestExternalCheckedThrowsCallsite.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestExternalCheckedThrowsCallsite.java");
    }

    Path externalApiSource = writeSource("pkg/ExternalApi.java", """
package pkg;

import java.io.IOException;

public class ExternalApi {
  public static Object create(String value) throws IOException {
    return value;
  }
}
""");

    compileJava8(List.of(decompiledFile, externalApiSource), fixture.getTempDir().resolve("compile-out"));
  }
}
