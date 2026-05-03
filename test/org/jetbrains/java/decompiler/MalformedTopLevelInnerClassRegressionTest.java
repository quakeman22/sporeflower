package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MalformedTopLevelInnerClassRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testMalformedAnonymousInnerAttributeDoesNotChangeTopLevelClassShape() throws IOException {
    Path classDir = fixture.getTestDataDir().resolve("classes/jasm");
    Path formatClass = classDir.resolve("TestMalformedTopLevelInnerFormat.class");
    Path ownerClass = classDir.resolve("TestMalformedTopLevelInnerOwner.class");

    assertTrue(Files.isRegularFile(formatClass), "Missing test class: " + formatClass);
    assertTrue(Files.isRegularFile(ownerClass), "Missing test class: " + ownerClass);

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSource(formatClass.toFile());
    decompiler.addSource(ownerClass.toFile());
    decompiler.decompileContext();

    Path formatSource = fixture.getTargetDir().resolve("TestMalformedTopLevelInnerFormat.java");
    Path ownerSource = fixture.getTargetDir().resolve("TestMalformedTopLevelInnerOwner.java");

    assertTrue(Files.isRegularFile(formatSource), "Expected malformed class to stay top-level");
    assertTrue(Files.isRegularFile(ownerSource), "Expected owner class source");

    String formatContent = DecompilerTestFixture.getContent(formatSource);
    String ownerContent = DecompilerTestFixture.getContent(ownerSource);

    assertTrue(formatContent.contains("public final class TestMalformedTopLevelInnerFormat"), formatContent);
    assertTrue(ownerContent.contains("TestMalformedTopLevelInnerFormat"), ownerContent);
    assertFalse(ownerContent.contains("class TestMalformedTopLevelInnerFormat {"), ownerContent);

    compileJava8NoDebug(List.of(formatSource, ownerSource), fixture.getTempDir().resolve("compile-out"));
  }
}
