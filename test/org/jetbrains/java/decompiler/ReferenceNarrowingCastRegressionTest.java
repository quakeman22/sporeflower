package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.collectors.ImportCollector;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.CodeType;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReferenceNarrowingCastRegressionTest {
  @Test
  public void testReferenceNarrowingCastIsRenderedForNonObjectSupertype() throws IOException {
    MinimalFernflowerEnvironment.setup();
    installImportCollector();

    VarType leftType = new VarType(CodeType.OBJECT, 0, "pkg/Derived");
    VarType rightType = new VarType(CodeType.OBJECT, 0, "pkg/Base");
    Exprent exprent = new DummyExprent(rightType, "value");

    TextBuffer buffer = new TextBuffer();
    boolean casted = ExprProcessor.getCastedExprent(exprent, leftType, buffer, 0, false);
    String rendered = buffer.convertToStringAndAllowDataDiscard();

    assertTrue(casted, "Expected narrowing reference cast to be rendered");
    assertTrue(rendered.contains("Derived"), rendered);
  }

  private static void installImportCollector() throws IOException {
    Path fixtureClass = new DecompilerTestFixture().getTestDataDir().resolve("classes/jasm/pkg/TestLegacyStackMapSlotProbe.class");
    StructClass structClass;
    try (DataInputFullStream in = new DataInputFullStream(Files.readAllBytes(fixtureClass))) {
      structClass = StructClass.create(in, true);
    }
    DecompilerContext.setImportCollector(new ImportCollector(new ClassNode(ClassNode.Type.ROOT, structClass)));
  }

  private static final class DummyExprent extends Exprent {
    private final VarType type;
    private final String text;

    private DummyExprent(VarType type, String text) {
      super(Type.OTHER);
      this.type = type;
      this.text = text;
    }

    @Override
    protected List<Exprent> getAllExprents(List<Exprent> list) {
      return list;
    }

    @Override
    public Exprent copy() {
      return new DummyExprent(type, text);
    }

    @Override
    public VarType getExprType() {
      return type;
    }

    @Override
    public VarType getInferredExprType(VarType upperBound) {
      return type;
    }

    @Override
    public TextBuffer toJava(int indent) {
      return new TextBuffer(text);
    }

    @Override
    public void getBytecodeRange(BitSet values) {
    }
  }
}
