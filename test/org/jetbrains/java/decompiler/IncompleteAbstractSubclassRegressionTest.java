package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IncompleteAbstractSubclassRegressionTest extends DecompileRegressionTestBase {
  @Override
  protected Object[] fixtureOptions() {
    return new Object[] {
      IFernflowerPreferences.RENAME_ENTITIES, "1",
      IFernflowerPreferences.LEGACY_SOURCE_COMPATIBILITY, "1"
    };
  }

  @Test
  public void testFinalSubclassMissingAbstractSuperclassMethodsCompiles() throws IOException {
    Path baseClass = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestIncompleteAbstractBase.class");
    Path childClass = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestIncompleteAbstractChild.class");
    assertTrue(Files.isRegularFile(baseClass), "Missing test class: " + baseClass);
    assertTrue(Files.isRegularFile(childClass), "Missing test class: " + childClass);

    fixture.getDecompiler().addSource(baseClass.toFile());
    fixture.getDecompiler().addSource(childClass.toFile());
    fixture.getDecompiler().decompileContext();

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestIncompleteAbstractChild.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestIncompleteAbstractChild.java");
    }
    String content = DecompilerTestFixture.getContent(decompiledFile);
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    recompile();
  }
}
