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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ReceiverSlotOverwriteRegressionTest {
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
  public void testReceiverSlotOverwriteDoesNotDecompileToThisAssignment() throws IOException {
    Path srcRoot = fixture.getTempDir().resolve("compile-src");
    Path outRoot = fixture.getTempDir().resolve("compile-out");
    Path source = srcRoot.resolve("pkg/TestReceiverSlotOverwrite.java");
    Files.createDirectories(source.getParent());

    Files.writeString(source, """
package pkg;

public class TestReceiverSlotOverwrite {
  public TestReceiverSlotOverwrite rewrite() {
    TestReceiverSlotOverwrite tmp = this;
    tmp = new TestReceiverSlotOverwrite();
    return tmp;
  }
}
""", StandardCharsets.UTF_8);

    compileJava8NoDebug(List.of(source), outRoot);

    Path classFile = outRoot.resolve("pkg/TestReceiverSlotOverwrite.class");
    patchMethodLocal1ToLocal0(classFile, "rewrite", "()Lpkg/TestReceiverSlotOverwrite;");

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSource(outRoot.toFile());
    decompiler.decompileContext();

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestReceiverSlotOverwrite.java");
    String content = DecompilerTestFixture.getContent(decompiledFile);

    assertFalse(content.contains("this ="), content);

    compileJava8NoDebug(List.of(decompiledFile), fixture.getTempDir().resolve("recompiled-out"));
  }

  @Test
  public void testNoOpReceiverStoreIsRemovedInsteadOfPrintedAsThisAssignment() throws IOException {
    Path srcRoot = fixture.getTempDir().resolve("compile-src-noop");
    Path outRoot = fixture.getTempDir().resolve("compile-out-noop");
    Path source = srcRoot.resolve("pkg/TestReceiverNoopStore.java");
    Files.createDirectories(source.getParent());

    Files.writeString(source, """
package pkg;

public class TestReceiverNoopStore {
  public TestReceiverNoopStore identity() {
    TestReceiverNoopStore tmp = this;
    return tmp;
  }
}
""", StandardCharsets.UTF_8);

    compileJava8NoDebug(List.of(source), outRoot);

    Path classFile = outRoot.resolve("pkg/TestReceiverNoopStore.class");
    patchMethodLocal1ToLocal0(classFile, "identity", "()Lpkg/TestReceiverNoopStore;");

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSource(outRoot.toFile());
    decompiler.decompileContext();

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestReceiverNoopStore.java");
    String content = DecompilerTestFixture.getContent(decompiledFile);

    assertFalse(content.contains("this ="), content);
    assertTrue(content.contains("return this;"), content);

    compileJava8NoDebug(List.of(decompiledFile), fixture.getTempDir().resolve("recompiled-out-noop"));
  }

  @Test
  public void testReceiverStoreWithCompanionStackValueDoesNotRenderAsThisAssignment() throws IOException {
    Path srcRoot = fixture.getTempDir().resolve("compile-src-stack");
    Path outRoot = fixture.getTempDir().resolve("compile-out-stack");
    Path source = srcRoot.resolve("pkg/TestReceiverStackStore.java");
    Files.createDirectories(source.getParent());

    Files.writeString(source, """
package pkg;

public class TestReceiverStackStore {
  private int n = 3;

  public TestReceiverStackStore probe() {
    TestReceiverStackStore[] arr = new TestReceiverStackStore[]{new TestReceiverStackStore()};
    TestReceiverStackStore self = this;
    int count = 0;

    for (int i = 0; i < self.n; i++) {
      count++;
    }

    if (count == 0) {
      return self;
    }

    return arr[0];
  }
}
""", StandardCharsets.UTF_8);

    compileJava8NoDebug(List.of(source), outRoot);

    Path classFile = outRoot.resolve("pkg/TestReceiverStackStore.class");
    patchMethodLocalToLocal0(classFile, "probe", "()Lpkg/TestReceiverStackStore;", 2);

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSource(outRoot.toFile());
    decompiler.decompileContext();

    Path decompiledFile = fixture.getTargetDir().resolve("pkg/TestReceiverStackStore.java");
    String content = DecompilerTestFixture.getContent(decompiledFile);

    assertFalse(content.contains("this ="), content);

    compileJava8NoDebug(List.of(decompiledFile), fixture.getTempDir().resolve("recompiled-out-stack"));
  }

  private static void patchMethodLocal1ToLocal0(Path classFile, String targetMethodName, String targetDescriptor) throws IOException {
    patchMethodLocalToLocal0(classFile, targetMethodName, targetDescriptor, 1);
  }

  private static void patchMethodLocalToLocal0(Path classFile, String targetMethodName, String targetDescriptor, int localIndex) throws IOException {
    patchMethodCode(classFile, targetMethodName, targetDescriptor, (classBytes, codeStart, codeLength) -> patchCodeAttributeLocalToLocal0(classBytes, codeStart, codeLength, localIndex));
  }

  private static void patchMethodCode(
    Path classFile,
    String targetMethodName,
    String targetDescriptor,
    CodeAttributePatcher patcher
  ) throws IOException {
    byte[] bytes = Files.readAllBytes(classFile);
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

    if (buffer.getInt() != 0xCAFEBABE) {
      fail("Not a class file: " + classFile);
    }

    buffer.getShort(); // minor
    buffer.getShort(); // major

    int cpCount = Short.toUnsignedInt(buffer.getShort());
    String[] utf8 = new String[cpCount];

    for (int i = 1; i < cpCount; i++) {
      int tag = Byte.toUnsignedInt(buffer.get());
      switch (tag) {
        case 1 -> {
          int len = Short.toUnsignedInt(buffer.getShort());
          byte[] data = new byte[len];
          buffer.get(data);
          utf8[i] = new String(data, StandardCharsets.UTF_8);
        }
        case 3, 4, 9, 10, 11, 12, 17, 18 -> buffer.position(buffer.position() + 4);
        case 5, 6 -> {
          buffer.position(buffer.position() + 8);
          i++;
        }
        case 7, 8, 16, 19, 20 -> buffer.position(buffer.position() + 2);
        case 15 -> buffer.position(buffer.position() + 3);
        default -> fail("Unsupported constant pool tag " + tag);
      }
    }

    buffer.position(buffer.position() + 6); // access, this, super

    int interfacesCount = Short.toUnsignedInt(buffer.getShort());
    buffer.position(buffer.position() + interfacesCount * 2);

    int fieldsCount = Short.toUnsignedInt(buffer.getShort());
    for (int i = 0; i < fieldsCount; i++) {
      skipMember(buffer);
    }

    int methodsCount = Short.toUnsignedInt(buffer.getShort());
    boolean patchedMethod = false;

    for (int i = 0; i < methodsCount; i++) {
      buffer.getShort(); // access
      int nameIndex = Short.toUnsignedInt(buffer.getShort());
      int descriptorIndex = Short.toUnsignedInt(buffer.getShort());
      String methodName = utf8[nameIndex];
      String descriptor = utf8[descriptorIndex];

      int attributesCount = Short.toUnsignedInt(buffer.getShort());
      for (int j = 0; j < attributesCount; j++) {
        int attributeNameIndex = Short.toUnsignedInt(buffer.getShort());
        String attributeName = utf8[attributeNameIndex];
        int attributeLength = buffer.getInt();
        int attributeStart = buffer.position();

        if ("Code".equals(attributeName)
          && targetMethodName.equals(methodName)
          && targetDescriptor.equals(descriptor)) {
          int codeLengthPos = attributeStart + 4;
          int codeLength = ByteBuffer.wrap(bytes, codeLengthPos, 4).order(ByteOrder.BIG_ENDIAN).getInt();
          int codeStart = codeLengthPos + 4;
          patchedMethod = patcher.patch(bytes, codeStart, codeLength);
        }

        buffer.position(attributeStart + attributeLength);
      }
    }

    assertTrue(patchedMethod, "Failed to patch " + targetMethodName + targetDescriptor + " in " + classFile);
    Files.write(classFile, bytes);
  }

  private static boolean patchCodeAttributeLocalToLocal0(byte[] classBytes, int codeStart, int codeLength, int localIndex) {
    byte aload = switch (localIndex) {
      case 0 -> 0x2A;
      case 1 -> 0x2B;
      case 2 -> 0x2C;
      case 3 -> 0x2D;
      default -> 0;
    };
    byte astore = switch (localIndex) {
      case 0 -> 0x4B;
      case 1 -> 0x4C;
      case 2 -> 0x4D;
      case 3 -> 0x4E;
      default -> 0;
    };

    int replacements = 0;
    for (int i = 0; i < codeLength; i++) {
      int pos = codeStart + i;
      int opcode = Byte.toUnsignedInt(classBytes[pos]);

      if (aload != 0 && opcode == Byte.toUnsignedInt(aload)) {
        classBytes[pos] = 0x2A;
        replacements++;
      } else if (astore != 0 && opcode == Byte.toUnsignedInt(astore)) {
        classBytes[pos] = 0x4B;
        replacements++;
      } else if ((opcode == 0x19 || opcode == 0x3A) && i + 1 < codeLength) {
        int indexPos = pos + 1;
        if (Byte.toUnsignedInt(classBytes[indexPos]) == localIndex) {
          classBytes[indexPos] = 0;
          replacements++;
          i++;
        }
      }
    }

    return replacements > 0;
  }

  @FunctionalInterface
  private interface CodeAttributePatcher {
    boolean patch(byte[] classBytes, int codeStart, int codeLength);
  }

  private static void skipMember(ByteBuffer buffer) {
    buffer.position(buffer.position() + 6); // access, name, descriptor
    int attributesCount = Short.toUnsignedInt(buffer.getShort());
    for (int i = 0; i < attributesCount; i++) {
      buffer.getShort(); // name
      int length = buffer.getInt();
      buffer.position(buffer.position() + length);
    }
  }

  private static void compileJava8NoDebug(List<Path> sourceFiles, Path outputDir) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "JDK compiler is required to run this test");
    Files.createDirectories(outputDir);

    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
      List<String> options = List.of(
        "-g:none",
        "-source", "8",
        "-target", "8",
        "-d", outputDir.toString()
      );
      Boolean success = compiler.getTask(null, fileManager, null, options, null, sources).call();
      assertTrue(Boolean.TRUE.equals(success), "javac failed for " + sourceFiles);
    }
  }
}
