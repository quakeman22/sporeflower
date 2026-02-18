package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrossClassMissingThrowsPropagationRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testConstructorInfersThrowsFromOtherClassWithInferredThrows() throws IOException {
    Path jasmClasses = fixture.getTestDataDir().resolve("classes/jasm/pkg");
    Path producer = jasmClasses.resolve("TestCrossClassMissingThrowsProducer.class");
    Path caller = jasmClasses.resolve("TestCrossClassMissingThrowsCaller.class");
    assertTrue(Files.isRegularFile(producer), "Missing test class: " + producer);
    assertTrue(Files.isRegularFile(caller), "Missing test class: " + caller);

    Path sourceRoot = fixture.getTempDir().resolve("cross-class-throws-input/pkg");
    Files.createDirectories(sourceRoot);
    Files.copy(producer, sourceRoot.resolve(producer.getFileName()));
    Files.copy(caller, sourceRoot.resolve(caller.getFileName()));

    String content = decompileDirectory(sourceRoot.getParent(), "pkg/TestCrossClassMissingThrowsCaller.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    recompile();
  }
}
