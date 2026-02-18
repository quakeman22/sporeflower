package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrossClassMissingThrowsCatchRewriteOrderRegressionTest extends DecompileRegressionTestBase {
  @Override
  protected Object[] fixtureOptions() {
    return new Object[] {
      IFernflowerPreferences.THREADS, "1",
    };
  }

  @Test
  public void testCrossClassCatchRewriteRemainsStableWhenCalleeWrapperIsUnavailable() throws IOException {
    Path jasmClasses = fixture.getTestDataDir().resolve("classes/jasm/pkg");
    Path producer = jasmClasses.resolve("TestCrossClassMissingThrowsCatchRewriteOrder.class");
    Path caller = jasmClasses.resolve("TestCrossClassMissingThrowsCatchRewriteOrderCaller.class");
    assertTrue(Files.isRegularFile(producer), "Missing test class: " + producer);
    assertTrue(Files.isRegularFile(caller), "Missing test class: " + caller);

    Path sourceRoot = fixture.getTempDir().resolve("ordered-input/pkg");
    Files.createDirectories(sourceRoot);
    Files.copy(producer, sourceRoot.resolve(producer.getFileName()));
    Files.copy(caller, sourceRoot.resolve(caller.getFileName()));

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSource(sourceRoot.getParent().toFile());
    decompiler.decompileContext();

    assertTrue(!listJavaSources(fixture.getTargetDir()).isEmpty(), "No decompiled Java files were produced");

    recompile();
  }
}
