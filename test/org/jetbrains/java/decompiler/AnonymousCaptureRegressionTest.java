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

public class AnonymousCaptureRegressionTest {
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
  public void testAnonymousCaptureUsedOnlyInInitDoesNotLeakCtorArguments() throws IOException {
    Path srcRoot = fixture.getTempDir().resolve("compile-src");
    Path outRoot = fixture.getTempDir().resolve("compile-out");
    Path source = srcRoot.resolve("anon/TestAnonymousCapture.java");
    Files.createDirectories(source.getParent());

    Files.writeString(source, """
package anon;

public class TestAnonymousCapture {
  private final int seed = 7;

  public Runnable build(final String levelId) {
    final int local = this.seed;
    return new Runnable() {
      private int total;

      {
        this.total = local;
        java.io.InputStream in = this.getClass().getResourceAsStream("/lvl/" + levelId + ".bin");
        if (in == null) {
          this.total++;
        }
      }

      @Override
      public void run() {
        this.total += local;
      }
    };
  }
}
""", StandardCharsets.UTF_8);

    compileJava8NoDebug(source, outRoot);

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSource(outRoot.toFile());
    decompiler.decompileContext();

    Path decompiledFile = fixture.getTargetDir().resolve("anon/TestAnonymousCapture.java");
    String content = DecompilerTestFixture.getContent(decompiledFile);

    assertTrue(content.contains("new Runnable() {"), content);
    assertFalse(content.contains("new Runnable(this"), content);
    assertFalse(Pattern.compile("new\\s+Runnable\\s*\\(\\s*[^)]").matcher(content).find(), content);
  }

  private static void compileJava8NoDebug(Path sourceFile, Path outputDir) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "JDK compiler is required to run this test");
    Files.createDirectories(outputDir);

    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjects(sourceFile.toFile());
      List<String> options = List.of(
        "-g:none",
        "-source", "8",
        "-target", "8",
        "-d", outputDir.toString()
      );
      Boolean success = compiler.getTask(null, fileManager, null, options, null, sources).call();
      assertTrue(Boolean.TRUE.equals(success), "javac failed for " + sourceFile);
    }
  }
}
