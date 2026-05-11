package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReservedSyntheticFieldNameRegressionTest extends DecompileRegressionTestBase {
  @Override
  protected Object[] fixtureOptions() {
    return new Object[] {IFernflowerPreferences.LEGACY_SOURCE_COMPATIBILITY, "1"};
  }

  @Test
  public void testLegacySourceDoesNotRenderSyntheticThisFieldName() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestSyntheticReferencedField.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestSyntheticReferencedField.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
    assertFalse(content.contains("this$0"), content);
  }

  @Test
  public void testNonLegacySourceKeepsSyntheticThisFieldName() throws IOException {
    DecompilerTestFixture nonLegacyFixture = new DecompilerTestFixture();
    nonLegacyFixture.setUp(IFernflowerPreferences.LEGACY_SOURCE_COMPATIBILITY, "0");
    try {
      Path classFile = nonLegacyFixture.getTestDataDir().resolve("classes/jasm/pkg/TestSyntheticReferencedField.class");
      assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

      nonLegacyFixture.getDecompiler().addSource(classFile.toFile());
      nonLegacyFixture.getDecompiler().decompileContext();
      Path decompiledFile = nonLegacyFixture.getTargetDir().resolve("pkg/TestSyntheticReferencedField.java");
      if (!Files.isRegularFile(decompiledFile)) {
        decompiledFile = nonLegacyFixture.getTargetDir().resolve("TestSyntheticReferencedField.java");
      }

      assertTrue(Files.isRegularFile(decompiledFile), "Decompiled file not found: " + decompiledFile);
      String content = DecompilerTestFixture.getContent(decompiledFile);
      assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
      assertTrue(content.contains("this$0"), content);
    } finally {
      nonLegacyFixture.tearDown();
    }
  }
}
