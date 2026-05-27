package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SyntheticAccessorPreservationRegressionTest extends DecompileRegressionTestBase {
  private void copyJasmClass(String simpleName, Path inputRoot) throws IOException {
    Path jasmClasses = fixture.getTestDataDir().resolve("classes/jasm/pkg");
    Path input = inputRoot.resolve("pkg");
    Files.createDirectories(input);
    Path classFile = input.resolve(simpleName + ".class");
    Files.copy(jasmClasses.resolve(simpleName + ".class"), classFile);
  }

  private void copyClassFamily(String simpleName, Path sourcePackage, Path inputRoot) throws IOException {
    Path input = inputRoot.resolve("pkg");
    Files.createDirectories(input);
    try (var stream = Files.list(sourcePackage)) {
      for (Path classFile : stream
        .filter(path -> {
          String fileName = path.getFileName().toString();
          return fileName.equals(simpleName + ".class") || (fileName.startsWith(simpleName + "$") && fileName.endsWith(".class"));
        })
        .toList()) {
        Files.copy(classFile, input.resolve(classFile.getFileName()));
      }
    }
  }

  @Test
  public void testSyntheticAccessorReferencedBySeparateClassStillCompiles() throws IOException {
    Path jasmClasses = fixture.getTestDataDir().resolve("classes/jasm/pkg");
    Path input = fixture.getTempDir().resolve("synthetic-accessor-input/pkg");
    Files.createDirectories(input);
    Files.copy(jasmClasses.resolve("TestSyntheticAccessorOwner.class"), input.resolve("TestSyntheticAccessorOwner.class"));
    Files.copy(jasmClasses.resolve("TestSyntheticAccessorOwner$1.class"), input.resolve("TestSyntheticAccessorOwner$1.class"));

    String owner = decompileDirectory(input.getParent(), "pkg/TestSyntheticAccessorOwner.java");
    assertFalse(owner.contains("$VF: Couldn't be decompiled"), owner);
    assertTrue(owner.contains("access$000(TestSyntheticAccessorOwner"), owner);

    String nested = Files.readString(fixture.getTargetDir().resolve("pkg/TestSyntheticAccessorOwner$1.java"));
    assertTrue(nested.contains("TestSyntheticAccessorOwner.access$000"), nested);

    recompile();
  }

  @Test
  public void testSameClassSyntheticMethodReferencedByVisibleCodeStillCompiles() throws IOException {
    Path inputRoot = fixture.getTempDir().resolve("synthetic-same-class-input");
    copyJasmClass("TestSyntheticSameClassHiddenMethod", inputRoot);

    String content = decompileDirectory(inputRoot, "pkg/TestSyntheticSameClassHiddenMethod.java");
    assertTrue(content.contains("static String hidden()"), content);

    recompile();
  }

  @Test
  public void testTransitiveSyntheticMethodDependencyStillCompiles() throws IOException {
    Path inputRoot = fixture.getTempDir().resolve("synthetic-transitive-input");
    copyJasmClass("TestSyntheticTransitiveHiddenMethod", inputRoot);

    String content = decompileDirectory(inputRoot, "pkg/TestSyntheticTransitiveHiddenMethod.java");
    assertTrue(content.contains("static String first()"), content);
    assertTrue(content.contains("static String second()"), content);

    recompile();
  }

  @Test
  public void testSyntheticConstructorMarkerArgumentIsElidedInsteadOfPreserved() throws IOException {
    Path inputRoot = fixture.getTempDir().resolve("synthetic-constructor-marker-input");
    copyClassFamily("TestInner2", fixture.getTestDataDir().resolve("classes/java8/pkg"), inputRoot);

    String content = decompileDirectory(inputRoot, "pkg/TestInner2.java");
    assertTrue(content.contains("super();"), content);
    assertTrue(content.contains("super(2);"), content);
    assertFalse(content.contains("TestInner2$1"), content);
    assertFalse(content.contains("super(null)"), content);

    recompile();
  }

  @Test
  public void testSyntheticAccessorOwnedByNonStaticInnerClassStillCompiles() throws IOException {
    Path inputRoot = fixture.getTempDir().resolve("non-static-inner-synthetic-accessor-input");
    copyJasmClass("TestNonStaticInnerSyntheticAccessor", inputRoot);
    copyJasmClass("TestNonStaticInnerSyntheticAccessor$Inner", inputRoot);
    copyJasmClass("TestNonStaticInnerSyntheticAccessor$Inner$1", inputRoot);

    String content = decompileDirectory(inputRoot, "pkg/TestNonStaticInnerSyntheticAccessor.java");
    assertTrue(content.contains("access$000"), content);

    recompile();
  }
}
