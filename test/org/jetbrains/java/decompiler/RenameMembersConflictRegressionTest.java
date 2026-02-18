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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RenameMembersConflictRegressionTest {
  private DecompilerTestFixture fixture;

  @BeforeEach
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    fixture.setUp(IFernflowerPreferences.RENAME_ENTITIES, "1");
  }

  @AfterEach
  public void tearDown() {
    fixture.tearDown();
    fixture = null;
  }

  @Test
  public void testRenameMembersConflictResolutionKeepsRunAndValidConstructorSyntax() throws IOException {
    Path srcRoot = fixture.getTempDir().resolve("compile-src");
    Path outRoot = fixture.getTempDir().resolve("compile-out");
    Files.createDirectories(srcRoot.resolve("ren"));

    Path hasCoords = srcRoot.resolve("ren/HasCoords.java");
    Path baseUnit = srcRoot.resolve("ren/BaseUnit.java");
    Path unitRunner = srcRoot.resolve("ren/UnitRunner.java");
    Path factory = srcRoot.resolve("ren/Factory.java");

    Files.writeString(hasCoords, """
package ren;

public interface HasCoords {
  int n = 1;
  int o = 2;
}
""", StandardCharsets.UTF_8);

    Files.writeString(baseUnit, """
package ren;

public abstract class BaseUnit {
  protected int n;
  protected int o;
  protected int p;
}
""", StandardCharsets.UTF_8);

    Files.writeString(unitRunner, """
package ren;

public class UnitRunner extends BaseUnit implements HasCoords, Runnable {
  public UnitRunner(int x, int y) {
    super.n = x;
    super.o = y;
    this.p = x + y;
  }

  @Override
  public void run() {
    this.p++;
  }

  public int a() {
    return super.n + super.o;
  }

  public int a(int delta) {
    return this.p + delta;
  }
}
""", StandardCharsets.UTF_8);

    Files.writeString(factory, """
package ren;

public final class Factory {
  private Factory() {
  }

  public static UnitRunner create() {
    return new UnitRunner(1, 2);
  }
}
""", StandardCharsets.UTF_8);

    compileJava8NoDebug(List.of(hasCoords, baseUnit, unitRunner, factory), outRoot);

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSource(outRoot.toFile());
    decompiler.decompileContext();

    String ifaceContent = DecompilerTestFixture.getContent(fixture.getTargetDir().resolve("ren/HasCoords.java"));
    String unitContent = DecompilerTestFixture.getContent(fixture.getTargetDir().resolve("ren/UnitRunner.java"));
    String factoryContent = DecompilerTestFixture.getContent(fixture.getTargetDir().resolve("ren/Factory.java"));

    assertTrue(unitContent.contains("public void run()"), unitContent);
    assertFalse(unitContent.contains("renamed from: run () void"), unitContent);
    assertTrue(unitContent.contains("public int a()"), unitContent);
    assertTrue(Pattern.compile("public int method_\\d+\\(int").matcher(unitContent).find(), unitContent);
    assertFalse(Pattern.compile("public int a\\(int").matcher(unitContent).find(), unitContent);

    assertTrue(ifaceContent.contains("int field_0 = 1;"), ifaceContent);
    assertTrue(ifaceContent.contains("int field_1 = 2;"), ifaceContent);
    assertFalse(ifaceContent.contains("int n = 1;"), ifaceContent);
    assertFalse(ifaceContent.contains("int o = 2;"), ifaceContent);

    assertTrue(factoryContent.contains("new UnitRunner(1, 2)"), factoryContent);
    assertFalse(Pattern.compile("new\\s+UnitRunner\\s*;").matcher(factoryContent).find(), factoryContent);

    compileJava8NoDebug(listJavaSources(fixture.getTargetDir()), fixture.getTempDir().resolve("recompiled-out"));
  }

  private static List<Path> listJavaSources(Path root) throws IOException {
    List<Path> sources = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root)) {
      stream.filter(path -> path.getFileName().toString().endsWith(".java")).forEach(sources::add);
    }
    return sources;
  }

  private static void compileJava8NoDebug(List<Path> sourceFiles, Path outputDir) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "JDK compiler is required to run this test");
    Files.createDirectories(outputDir);

    List<File> sourceFileList = sourceFiles.stream().map(Path::toFile).collect(Collectors.toList());
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(sourceFileList);
      List<String> options = List.of(
        "-g:none",
        "-source", "8",
        "-target", "8",
        "-d", outputDir.toString()
      );
      Boolean success = compiler.getTask(null, fileManager, null, options, null, sources).call();
      assertTrue(Boolean.TRUE.equals(success), "javac failed for " + sourceFileList);
    }
  }
}
