package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThisSelfAssignmentRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testNoOpReceiverAndParameterSelfAssignmentsAreNotRendered() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestThisSelfAssignment.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestThisSelfAssignment.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
    assertFalse(content.contains("this = this;"), content);
    assertFalse(content.contains("var1 = var1;"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestThisSelfAssignment.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestThisSelfAssignment.java");
    }
    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
