package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReferenceNarrowingFlowRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testInlineCastAssignmentQualifierProbeCompiles() throws IOException {
    assertProbeCompiles("TestInlineCastAssignmentQualifierProbe");
  }

  @Test
  public void testInlineTypeRefinementCastProbeCompiles() throws IOException {
    assertProbeCompiles("TestInlineTypeRefinementCastProbe");
  }

  @Test
  public void testSlotReuseNarrowingCastProbeCompiles() throws IOException {
    assertProbeCompiles("TestSlotReuseNarrowingCastProbe");
  }

  @Test
  public void testSlotReuseNarrowingCastLoopProbeCompiles() throws IOException {
    assertProbeCompiles("TestSlotReuseNarrowingCastLoopProbe");
  }

  private void assertProbeCompiles(String className) throws IOException {
    Path supportSource = fixture.getTempDir().resolve("compile-src/pkg/SupportTypes.java");
    Files.createDirectories(supportSource.getParent());
    Files.writeString(
      supportSource,
      """
        package pkg;

        class SupportBase {
          int value;

          static SupportBase produce() {
            return null;
          }
        }

        class SupportDerived extends SupportBase {
          int special;

          void ping() {
          }
        }
        """
    );

    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/" + className + ".class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    String content = decompileClassFile(classFile, "pkg/" + className + ".java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/" + className + ".java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve(className + ".java");
    }

    compileJava8(List.of(decompiledFile, supportSource), fixture.getTempDir().resolve("compile-out-" + className));
  }
}
