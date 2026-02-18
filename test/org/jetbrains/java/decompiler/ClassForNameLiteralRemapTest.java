package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassForNameLiteralRemapTest extends DecompileRegressionTestBase {
  private Path mapping;

  @Override
  @BeforeEach
  public void setUp() throws IOException {
    mapping = Files.createTempFile("vf-tiny2-", ".tiny");
    Files.writeString(mapping, """
tiny\t2\t0\tofficial\tnamed
c\tLoader\tLoaderReadable
\tm\t()Ljava/lang/Class;\tload\tloadAdapterClass
c\ta\tAdapter
""", StandardCharsets.UTF_8);

    fixture = new DecompilerTestFixture();
    fixture.setUp(
      IFernflowerPreferences.MAPPINGS_PATH, mapping.toString(),
      IFernflowerPreferences.MAPPINGS_SOURCE_NAMESPACE, "official",
      IFernflowerPreferences.MAPPINGS_TARGET_NAMESPACE, "named"
    );
  }

  @Override
  public void tearDown() {
    super.tearDown();
    try {
      if (mapping != null) Files.deleteIfExists(mapping);
    } catch (IOException ignored) {
    }
  }

  @Test
  public void testClassForNameStringLiteralUsesMappedBinaryName() throws IOException {
    Path source = writeSource("Loader.java", """
public class Loader {
  public static Class load() throws Exception {
    return Class.forName("a");
  }
}

class a {
}
""");

    compileJava8NoDebug(source, outRoot());

    fixture.getDecompiler().addSource(outRoot().toFile());
    fixture.getDecompiler().decompileContext();

    List<Path> javaFiles = listJavaSources(fixture.getTargetDir());
    assertTrue(!javaFiles.isEmpty(), "No decompiled .java files found in " + fixture.getTargetDir());

    String needle = "Class.forName(\"Adapter\")";
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
}
