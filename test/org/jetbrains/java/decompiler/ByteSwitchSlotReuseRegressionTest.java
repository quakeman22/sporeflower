package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ByteSwitchSlotReuseRegressionTest extends DecompileRegressionTestBase {
  @Test
  public void testSwitchCaseSlotReuseDoesNotNarrowIntAssignmentToByte() throws IOException {
    Path source = writeSource("Repro.java", """
import java.util.Vector;

public class Repro {
  static byte[] field_234 = new byte[32];

  public static int test(Vector a, int var1) {
    int var7 = var1;
    Object[] var3 = (Object[])a.elementAt(var7);
    int[] var4;
    int var10000;
    if ((var4 = (int[])var3[0])[2] + 1 < field_234.length) {
      byte var17 = field_234[++var4[2]];
      var3[0] = var4;
      a.setElementAt(var3, var7);
      var10000 = var17;
    } else {
      var10000 = 15;
    }

    byte var8 = (byte)var10000;
    byte var18 = var8;
    int var9 = var1;
    switch (var18) {
      case 2:
        int var30 = var9;
        int[] var60 = (int[])((Object[])a.elementAt(var30))[0];
        Object var48 = null;
        var10000 = var60[2];
        int var31 = var10000;
        short var38 = 7;
        return var38 + var31 - 1;
      default:
        return 0;
    }
  }
}
""");

    compileJava8NoDebug(source, outRoot());

    String content = decompileDirectory(outRoot(), "Repro.java");
    assertFalse(content.contains("$VF: Couldn't be decompiled"), content);

    Path decompiledFile = fixture.getTargetDir().resolve("Repro.java");
    compileJava8(decompiledFile, fixture.getTempDir().resolve("compile-out"));
  }
}
