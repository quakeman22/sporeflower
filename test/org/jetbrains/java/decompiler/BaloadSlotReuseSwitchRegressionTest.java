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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BaloadSlotReuseSwitchRegressionTest {
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
  public void testBaloadSwitchSelectorFromReusedSlotDecompilesToValidJava() throws IOException {
    Path classFile = fixture.getTestDataDir().resolve("classes/jasm/pkg/TestBaloadSlotReuseSwitch.class");
    assertTrue(Files.isRegularFile(classFile), "Missing test class: " + classFile);

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSource(classFile.toFile());
    decompiler.decompileContext();

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestBaloadSlotReuseSwitch.java");
    if (!Files.isRegularFile(decompiledFile)) {
      decompiledFile = fixture.getTargetDir().resolve("TestBaloadSlotReuseSwitch.java");
    }
    assertTrue(Files.isRegularFile(decompiledFile), "Decompiled file not found: " + decompiledFile);

    String content = DecompilerTestFixture.getContent(decompiledFile);
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
    assertFalse(content.contains("switch (((Object[])"), content);
    assertTrue(content.contains("case 119:"), content);
    assertTrue(content.contains("case 120:"), content);

    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }

  private static void compileJava8(Path sourceFile, Path outputDir) throws IOException {
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
