package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnonymousCaptureRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testAnonymousCaptureUsedOnlyInInitDoesNotLeakCtorArguments() throws IOException {
    String content = compileDecompileAndRead("anon/TestAnonymousCapture.java", """
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
""");

    assertTrue(content.contains("new Runnable() {"), content);
    assertFalse(content.contains("new Runnable(this"), content);
    assertFalse(Pattern.compile("new\\s+Runnable\\s*\\(\\s*[^)]").matcher(content).find(), content);
  }
}
