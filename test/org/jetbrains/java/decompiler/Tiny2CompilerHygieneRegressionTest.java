package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Tiny2CompilerHygieneRegressionTest extends DecompileRegressionTestBase {
  private Path mapping;

  @Override
  @BeforeEach
  public void setUp() throws IOException {
    mapping = Files.createTempFile("vf-tiny2-compiler-hygiene-", ".tiny");
    Files.writeString(mapping, """
tiny\t2\t0\tofficial\tnamed
c\tC\tGameEngine
""", StandardCharsets.UTF_8);

    fixture = new DecompilerTestFixture();
    fixture.setUp(
      IFernflowerPreferences.MAPPINGS_PATH, mapping.toString(),
      IFernflowerPreferences.MAPPINGS_SOURCE_NAMESPACE, "official",
      IFernflowerPreferences.MAPPINGS_TARGET_NAMESPACE, "named"
    );
  }

  @Override
  @AfterEach
  public void tearDown() {
    super.tearDown();
    try {
      if (mapping != null) {
        Files.deleteIfExists(mapping);
      }
    }
    catch (IOException ignored) {
    }
  }

  @Test
  public void testTinyModeKeepsLegalOriginalNamesButRenamesJavaKeywords() throws IOException {
    Path source = writeSource("C.java", """
public class C {
  static int b;
  static int xx;

  static int a() {
    return b + xx;
  }
}
""");

    compileJava8NoDebug(source, outRoot());
    renameUtf8Constant(outRoot().resolve("C.class"), "xx", "do");

    String content = decompileDirectory(outRoot(), "GameEngine.java");
    assertTrue(Pattern.compile("static\\s+int\\s+b\\s*;").matcher(content).find(), content);
    assertTrue(Pattern.compile("static\\s+int\\s+a\\s*\\(").matcher(content).find(), content);
    assertTrue(content.contains("field_0"), content);
    assertFalse(Pattern.compile("\\bdo\\b").matcher(stripComments(content)).find(), content);

    recompile();
  }

  private static String stripComments(String content) {
    return content.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//.*", "");
  }
}
