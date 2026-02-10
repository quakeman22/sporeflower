package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassForNameLiteralRemapTest {
  private DecompilerTestFixture fixture;

  @BeforeEach
  public void setUp() {
    fixture = new DecompilerTestFixture();
  }

  @AfterEach
  public void tearDown() {
    if (fixture != null) {
      fixture.tearDown();
      fixture = null;
    }
  }

  @Test
  public void testClassForNameStringLiteralUsesMappedBinaryName() throws IOException {
    Path mapping = Files.createTempFile("vf-tiny2-", ".tiny");
    try {
      Files.writeString(mapping, """
tiny\t2\t0\tofficial\tnamed
c\tLoader\tLoaderReadable
\tm\t()Ljava/lang/Class;\tload\tloadAdapterClass
c\ta\tAdapter
""", StandardCharsets.UTF_8);

      fixture.setUp(
        IFernflowerPreferences.MAPPINGS_PATH, mapping.toString(),
        IFernflowerPreferences.MAPPINGS_SOURCE_NAMESPACE, "official",
        IFernflowerPreferences.MAPPINGS_TARGET_NAMESPACE, "named"
      );

      Path srcRoot = fixture.getTempDir().resolve("compile-src");
      Path outRoot = fixture.getTempDir().resolve("compile-out");
      Path source = srcRoot.resolve("Loader.java");
      Files.createDirectories(source.getParent());

      Files.writeString(source, """
public class Loader {
  public static Class load() throws Exception {
    return Class.forName("a");
  }
}

class a {
}
""", StandardCharsets.UTF_8);

      compileJava8NoDebug(source, outRoot);

      ConsoleDecompiler decompiler = fixture.getDecompiler();
      decompiler.addSource(outRoot.toFile());
      decompiler.decompileContext();

      assertClassForNameLiteralRemapped(fixture.getTargetDir(), "Adapter");
    } finally {
      Files.deleteIfExists(mapping);
    }
  }

  private static void assertClassForNameLiteralRemapped(Path targetDir, String expectedBinaryName) throws IOException {
    List<Path> javaFiles;
    try (Stream<Path> stream = Files.walk(targetDir)) {
      javaFiles = stream
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(".java"))
        .toList();
    }

    assertTrue(!javaFiles.isEmpty(), "No decompiled .java files found in " + targetDir);

    String needle = "Class.forName(\"" + expectedBinaryName + "\")";
    for (Path javaFile : javaFiles) {
      String content = DecompilerTestFixture.getContent(javaFile);
      if (content.contains(needle)) {
        return;
      }
    }

    StringBuilder dump = new StringBuilder();
    for (Path javaFile : javaFiles) {
      dump.append(javaFile).append('\n')
        .append(DecompilerTestFixture.getContent(javaFile))
        .append("\n---\n");
    }
    throw new IOException("Expected literal not found: " + needle + "\n" + dump);
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
