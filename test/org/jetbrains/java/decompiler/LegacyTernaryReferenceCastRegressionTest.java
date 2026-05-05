package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LegacyTernaryReferenceCastRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testSiblingReferenceTernaryCastsOneBranchForLegacySource() throws IOException {
    String content = compileDecompileAndRead("pkg/TestLegacyTernaryReferenceCast.java", """
      package pkg;

      public class TestLegacyTernaryReferenceCast {
        static abstract class Base {
        }

        static final class Left extends Base {
        }

        static final class Right extends Base {
        }

        public Base choose(boolean flag) {
          return flag ? new Left() : new Right();
        }
      }
      """);

    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);
    assertTrue(
      content.contains("? (TestLegacyTernaryReferenceCast.Base)(new TestLegacyTernaryReferenceCast.Left()) : new TestLegacyTernaryReferenceCast.Right()"),
      content
    );
  }
}
