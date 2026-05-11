package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class MissingSubclassConstructorRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testSubclassWithoutConstructorAndNoDefaultSuperConstructorCompiles() throws IOException {
    Path jasmClasses = fixture.getTestDataDir().resolve("classes/jasm/pkg");
    Path input = fixture.getTempDir().resolve("missing-subclass-constructor-input/pkg");
    Files.createDirectories(input);
    Files.copy(jasmClasses.resolve("TestMissingSubclassConstructorBase.class"), input.resolve("TestMissingSubclassConstructorBase.class"));
    Files.copy(jasmClasses.resolve("TestMissingSubclassConstructorChild.class"), input.resolve("TestMissingSubclassConstructorChild.class"));

    String content = decompileDirectory(input.getParent(), "pkg/TestMissingSubclassConstructorChild.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    recompile();
  }
}
