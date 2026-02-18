package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SceneRoutingConditionRegressionTest {
  private DecompilerTestFixture fixture;

  @BeforeEach
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    fixture.setUp();
  }

  @AfterEach
  public void tearDown() {
    fixture.tearDown();
    fixture = null;
  }

  @Test
  public void testMixedSceneComparisonsDropRedundantTerms() throws IOException {
    Path srcRoot = fixture.getTempDir().resolve("compile-src");
    Path outRoot = fixture.getTempDir().resolve("compile-out");
    Path source = srcRoot.resolve("pkg/TestSceneRouting.java");
    Files.createDirectories(source.getParent());

    Files.writeString(source, """
package pkg;

public class TestSceneRouting {
  void naval() {}
  void world() {}
  void duel() {}

  void render(int scene) {
    if (scene == 4 || scene == 5) {
      this.naval();
    } else if (scene == 6 || scene == 7 || scene != 10) {
      this.world();
    } else {
      this.duel();
    }
  }
}
""", StandardCharsets.UTF_8);

    compileJava8(source, outRoot);

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSource(outRoot.toFile());
    decompiler.decompileContext();

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestSceneRouting.java");
    String content = DecompilerTestFixture.getContent(decompiledFile);

    assertTrue(Pattern.compile("else if \\(var\\d+\\s*==\\s*10\\)").matcher(content).find(), content);
    assertFalse(Pattern.compile("var\\d+\\s*!=\\s*6\\s*&&\\s*var\\d+\\s*!=\\s*7\\s*&&\\s*var\\d+\\s*==\\s*10").matcher(content).find(), content);
  }

  private static void compileJava8(Path sourceFile, Path outputDir) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "JDK compiler is required to run this test");
    Files.createDirectories(outputDir);

    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjects(sourceFile.toFile());
      List<String> options = List.of(
        "-source", "8",
        "-target", "8",
        "-d", outputDir.toString()
      );
      Boolean success = compiler.getTask(null, fileManager, null, options, null, sources).call();
      assertTrue(Boolean.TRUE.equals(success), "javac failed for " + sourceFile);
    }
  }
}
