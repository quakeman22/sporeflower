package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class IncompleteInterfaceImplementationRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testFinalClassMissingInheritedInterfaceMethodStillCompiles() throws IOException {
    Path jasmClasses = fixture.getTestDataDir().resolve("classes/jasm/pkg");
    Path input = fixture.getTempDir().resolve("incomplete-interface-input/pkg");
    Files.createDirectories(input);
    Files.copy(jasmClasses.resolve("TestIncompleteInterfaceParent.class"), input.resolve("TestIncompleteInterfaceParent.class"));
    Files.copy(jasmClasses.resolve("TestIncompleteInterfaceChild.class"), input.resolve("TestIncompleteInterfaceChild.class"));
    Files.copy(jasmClasses.resolve("TestIncompleteInterfaceImpl.class"), input.resolve("TestIncompleteInterfaceImpl.class"));

    String content = decompileDirectory(input.getParent(), "pkg/TestIncompleteInterfaceImpl.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    recompile();
  }
}
