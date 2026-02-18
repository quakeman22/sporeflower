package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CheckedStatementWalkerConcurrentMutationTest {
  @Test
  public void walkToleratesConcurrentMutationOfSiblingList() {
    AtomicBoolean mutated = new AtomicBoolean(false);
    TestStatement root = new TestStatement(1, null);
    TestStatement secondChild = new TestStatement(3, null);
    TestStatement firstChild = new TestStatement(
      2,
      () -> {
        if (mutated.compareAndSet(false, true)) {
          root.addChild(new TestStatement(4, null));
        }
      }
    );

    root.addChild(firstChild);
    root.addChild(secondChild);

    assertDoesNotThrow(
      () -> CheckedStatementWalker.walk(
        root,
        Collections.emptyList(),
        CheckedStatementWalker.CatchAllPolicy.CATCH_ALL_IGNORED,
        (exprents, activeCatchTypes) -> false
      )
    );
    assertTrue(mutated.get(), "Test setup did not mutate root child list");
  }

  private static final class TestStatement extends Statement {
    private final Runnable onGetStatExprents;

    private TestStatement(int id, Runnable onGetStatExprents) {
      super(StatementType.GENERAL, id);
      this.onGetStatExprents = onGetStatExprents;
    }

    private void addChild(TestStatement child) {
      this.getStats().addWithKey(child, child.id);
      child.setParent(this);
      if (this.getFirst() == null) {
        this.setFirst(child);
      }
    }

    @Override
    public List<Exprent> getStatExprents() {
      if (onGetStatExprents != null) {
        onGetStatExprents.run();
      }
      return Collections.emptyList();
    }
  }
}
