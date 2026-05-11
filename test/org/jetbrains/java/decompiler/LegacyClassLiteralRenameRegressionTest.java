package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LegacyClassLiteralRenameRegressionTest extends DecompileRegressionTestBase {
  @Override
  protected Object[] fixtureOptions() {
    return new Object[] {
      IFernflowerPreferences.RENAME_ENTITIES, "1",
      IFernflowerPreferences.LEGACY_SOURCE_COMPATIBILITY, "1"
    };
  }

  @Test
  public void testLegacyClassLiteralUsesRenamedClassWithoutGenerics() throws IOException {
    decompileFixtureInput(fixture);

    for (Path source : listJavaSources(fixture.getTargetDir())) {
      String content = DecompilerTestFixture.getContent(source);
      assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
      assertFalse(content.contains("Class<"), content);
      assertFalse(content.contains("x.class"), content);
    }

    recompile();
  }

  @Test
  public void testModernClassLiteralUsesRenamedClassWithGenerics() throws IOException {
    DecompilerTestFixture modernFixture = new DecompilerTestFixture();
    modernFixture.setUp(
      IFernflowerPreferences.RENAME_ENTITIES, "1",
      IFernflowerPreferences.LEGACY_SOURCE_COMPATIBILITY, "0");
    try {
      decompileFixtureInput(modernFixture);

      boolean sawGenericClassLiteral = false;
      for (Path source : listJavaSources(modernFixture.getTargetDir())) {
        String content = DecompilerTestFixture.getContent(source);
        assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
        assertFalse(content.contains("Class<x>"), content);
        assertFalse(content.contains("x.class"), content);
        sawGenericClassLiteral |= content.contains("Class<");
      }
      assertTrue(sawGenericClassLiteral, "Modern output should keep generic Class<T> class-literal types");

      compileJava8NoDebug(listJavaSources(modernFixture.getTargetDir()), modernFixture.getTempDir().resolve("recompiled-out"));
    } finally {
      modernFixture.tearDown();
    }
  }

  private static void decompileFixtureInput(DecompilerTestFixture fixture) throws IOException {
    Path jasmClasses = fixture.getTestDataDir().resolve("classes/jasm/pkg");
    Path input = fixture.getTempDir().resolve("legacy-class-literal-input/pkg");
    Files.createDirectories(input);
    Files.copy(jasmClasses.resolve("x.class"), input.resolve("x.class"));
    Files.copy(jasmClasses.resolve("y.class"), input.resolve("y.class"));

    fixture.getDecompiler().addSource(input.getParent().toFile());
    fixture.getDecompiler().decompileContext();
  }
}
