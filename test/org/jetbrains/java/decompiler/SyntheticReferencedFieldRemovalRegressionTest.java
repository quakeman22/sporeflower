package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SyntheticReferencedFieldRemovalRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testSyntheticReferencedFieldsAreNotRemovedFromRootClass() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestSyntheticReferencedField.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestSyntheticReferencedField.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestSyntheticReferencedField.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestSyntheticReferencedField.java");
    }
    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
