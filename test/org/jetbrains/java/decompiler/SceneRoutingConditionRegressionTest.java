package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SceneRoutingConditionRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testMixedSceneComparisonsDropRedundantTerms() throws IOException {
    Path srcFile = writeSource("pkg/TestSceneRouting.java", """
package pkg;

public class TestSceneRouting {
  void naval() {}
  void world() {}
  void duel() {}

  void render(int scene) {
    if (scene == 4 || scene == 5) {
      this.naval();
    } else if (scene == 6 || scene == 7 || scene != 10) {
      this.world();
    } else {
      this.duel();
    }
  }
}
""");

    compileJava8(srcFile, outRoot());

    String content = decompileDirectory(outRoot(), "pkg/TestSceneRouting.java");

    assertTrue(Pattern.compile("else if \\(var\\d+\\s*==\\s*10\\)").matcher(content).find(), content);
    assertFalse(Pattern.compile("var\\d+\\s*!=\\s*6\\s*&&\\s*var\\d+\\s*!=\\s*7\\s*&&\\s*var\\d+\\s*==\\s*10").matcher(content).find(), content);
  }
}
