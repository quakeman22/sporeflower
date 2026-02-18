package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NarrowingAssignCastRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testNarrowingReferenceAssignmentsKeepRequiredCast() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestNarrowingAssignCast.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/TestNarrowingAssignCast.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestNarrowingAssignCast.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestNarrowingAssignCast.java");
    }

    Path supportSource = fixture.getTempDir().resolve("compile-src/pkg/SupportTypes.java");
    Files.createDirectories(supportSource.getParent());
    Files.writeString(
      supportSource,
      """
        package pkg;

        class TestNarrowingBase {
          static TestNarrowingBase produce() {
            return null;
          }
        }

        class TestNarrowingDerived extends TestNarrowingBase {
          void ping() {
          }
        }
        """
    );

    compileJava8(List.of(decompiledFile, supportSource), fixture.getTempDir().resolve("compile-out"));
  }
}
