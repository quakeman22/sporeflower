package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NullOverloadAmbiguityRegressionTest extends DecompileRegressionTestBase {
  @Override
  protected Object[] fixtureOptions() {
    return new Object[]{IFernflowerPreferences.RENAME_ENTITIES, "1"};
  }

  @Test
  public void testNullLiteralCallKeepsExplicitCastForOverloadSelection() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestNullOverloadAmbiguity.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestNullOverloadAmbiguity.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
    assertTrue(Pattern.compile("\\(.*\\)null").matcher(content).find(), content);

    List<Path> decompiledSources = listJavaSources(fixture.getTargetDir());
    assertFalse(decompiledSources.isEmpty(), "No decompiled sources found");
    compileJava8(decompiledSources, fixture.getTempDir().resolve("compile-out"));
  }
}
