package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;
import org.jetbrains.java.decompiler.modules.renamer.Tiny2IdentifierRenamer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Tiny2IdentifierRenamerTest {
  @Test
  public void testTiny2MappingRenamesClassFieldAndMethod() throws IOException {
    Path mapping = Files.createTempFile("vf-tiny2-", ".tiny");
    try {
      Files.writeString(mapping, """
tiny\t2\t0\tofficial\tnamed
c\taf\tdefpackage/GameEngine
\tf\tI\ta\tcounter
\tm\t(IJ)I\tc\ttick
\t\tp\t1\tp0\tframe
""", StandardCharsets.UTF_8);

      Tiny2IdentifierRenamer renamer = Tiny2IdentifierRenamer.fromFile(mapping, "official", "named");

      assertTrue(renamer.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_CLASS, "af", null, null));
      assertEquals("defpackage/GameEngine", renamer.getNextClassName("af", "af"));

      assertTrue(renamer.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_FIELD, "af", "a", "I"));
      assertEquals("counter", renamer.getNextFieldName("af", "a", "I"));

      assertTrue(renamer.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_METHOD, "af", "c", "(IJ)I"));
      assertEquals("tick", renamer.getNextMethodName("af", "c", "(IJ)I"));
      assertFalse(renamer.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_METHOD, "af", "<init>", "()V"));
      assertEquals(1, renamer.parameterRenameCount());
      assertEquals("frame", renamer.getParameterRename("af", "c", "(IJ)I", 1));
      assertEquals("frame", renamer.getParameterRename("defpackage/GameEngine", "tick", "(IJ)I", 1));
    } finally {
      Files.deleteIfExists(mapping);
    }
  }

  @Test
  public void testTiny2MappingResolvesConfiguredNamespaces() throws IOException {
    Path mapping = Files.createTempFile("vf-tiny2-", ".tiny");
    try {
      Files.writeString(mapping, """
tiny\t2\t0\tofficial\tintermediary\tnamed
c\taf\tafInter\tdefpackage/GameEngine
\tm\t(I)V\ta\taInter\tsetValue
""", StandardCharsets.UTF_8);

      Tiny2IdentifierRenamer renamer = Tiny2IdentifierRenamer.fromFile(mapping, null, "named");

      assertTrue(renamer.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_CLASS, "af", null, null));
      assertEquals("defpackage/GameEngine", renamer.getNextClassName("af", "af"));
      assertTrue(renamer.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_METHOD, "af", "a", "(I)V"));
      assertEquals("setValue", renamer.getNextMethodName("af", "a", "(I)V"));
    } finally {
      Files.deleteIfExists(mapping);
    }
  }

  @Test
  public void testTiny2MappingRejectsConflictingDuplicates() throws IOException {
    Path mapping = Files.createTempFile("vf-tiny2-", ".tiny");
    try {
      Files.writeString(mapping, """
tiny\t2\t0\tofficial\tnamed
c\taf\tdefpackage/GameEngine
c\taf\tdefpackage/OtherName
""", StandardCharsets.UTF_8);

      assertThrows(IOException.class, () -> Tiny2IdentifierRenamer.fromFile(mapping, "official", "named"));
    } finally {
      Files.deleteIfExists(mapping);
    }
  }

  @Test
  public void testRenamerAddsStableSuffixOnRetryCalls() throws IOException {
    Path mapping = Files.createTempFile("vf-tiny2-", ".tiny");
    try {
      Files.writeString(mapping, """
tiny\t2\t0\tofficial\tnamed
c\taf\tdefpackage/GameEngine
\tf\tI\ta\tcounter
\tm\t(I)V\tb\ttick
""", StandardCharsets.UTF_8);

      Tiny2IdentifierRenamer renamer = Tiny2IdentifierRenamer.fromFile(mapping, "official", "named");

      assertEquals("defpackage/GameEngine", renamer.getNextClassName("af", "af"));
      assertEquals("defpackage/GameEngine_1", renamer.getNextClassName("af", "af"));

      assertEquals("counter", renamer.getNextFieldName("af", "a", "I"));
      assertEquals("counter_1", renamer.getNextFieldName("af", "a", "I"));

      assertEquals("tick", renamer.getNextMethodName("af", "b", "(I)V"));
      assertEquals("tick_1", renamer.getNextMethodName("af", "b", "(I)V"));
    } finally {
      Files.deleteIfExists(mapping);
    }
  }

  @Test
  public void testTiny2ParameterLookupSupportsMappedMethodDescriptor() throws IOException {
    Path mapping = Files.createTempFile("vf-tiny2-", ".tiny");
    try {
      Files.writeString(mapping, """
tiny\t2\t0\tofficial\tnamed
c\taf\tpkg/Entity
\tm\t(Laf;I)Laf;\ta\tcreate
\t\tp\t1\tp0\tentityType
""", StandardCharsets.UTF_8);

      Tiny2IdentifierRenamer renamer = Tiny2IdentifierRenamer.fromFile(mapping, "official", "named");

      assertEquals("entityType", renamer.getParameterRename("af", "a", "(Laf;I)Laf;", 1));
      assertEquals("entityType", renamer.getParameterRename("pkg/Entity", "create", "(Lpkg/Entity;I)Lpkg/Entity;", 1));
    } finally {
      Files.deleteIfExists(mapping);
    }
  }

  @Test
  public void testTiny2ParameterLookupHandlesForwardClassRenameInDescriptor() throws IOException {
    Path mapping = Files.createTempFile("vf-tiny2-", ".tiny");
    try {
      Files.writeString(mapping, """
tiny\t2\t0\tofficial\tnamed
c\ta\tpkg/A
\tm\t(Lb;)V\ta\tuse
\t\tp\t1\tp0\tvalue
c\tb\tpkg/B
""", StandardCharsets.UTF_8);

      Tiny2IdentifierRenamer renamer = Tiny2IdentifierRenamer.fromFile(mapping, "official", "named");

      assertEquals("value", renamer.getParameterRename("a", "a", "(Lb;)V", 1));
      assertEquals("value", renamer.getParameterRename("pkg/A", "use", "(Lpkg/B;)V", 1));
    } finally {
      Files.deleteIfExists(mapping);
    }
  }
}
