package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
    Path jasmClasses = fixture.getTestDataDir().resolve("classes/jasm/pkg");
    Path input = fixture.getTempDir().resolve("legacy-class-literal-input/pkg");
    Files.createDirectories(input);
    Files.copy(jasmClasses.resolve("x.class"), input.resolve("x.class"));
    Files.copy(jasmClasses.resolve("y.class"), input.resolve("y.class"));

    fixture.getDecompiler().addSource(input.getParent().toFile());
    fixture.getDecompiler().decompileContext();

    for (Path source : listJavaSources(fixture.getTargetDir())) {
      String content = DecompilerTestFixture.getContent(source);
      assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
      assertFalse(content.contains("Class<"), content);
      assertFalse(content.contains("x.class"), content);
    }

    recompile();
  }
}
