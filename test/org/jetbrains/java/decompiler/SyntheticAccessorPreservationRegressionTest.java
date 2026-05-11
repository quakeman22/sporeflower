package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SyntheticAccessorPreservationRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testSyntheticAccessorReferencedBySeparateClassStillCompiles() throws IOException {
    Path jasmClasses = fixture.getTestDataDir().resolve("classes/jasm/pkg");
    Path input = fixture.getTempDir().resolve("synthetic-accessor-input/pkg");
    Files.createDirectories(input);
    Files.copy(jasmClasses.resolve("TestSyntheticAccessorOwner.class"), input.resolve("TestSyntheticAccessorOwner.class"));
    Files.copy(jasmClasses.resolve("TestSyntheticAccessorOwner$1.class"), input.resolve("TestSyntheticAccessorOwner$1.class"));

    String owner = decompileDirectory(input.getParent(), "pkg/TestSyntheticAccessorOwner.java");
    assertFalse(owner.contains("$VF: Couldn't be decompiled"), owner);

    recompile();
  }
}
