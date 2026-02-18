// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.code.BytecodeVersion;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructExceptionsAttribute extends StructGeneralAttribute {

  private List<Integer> throwsExceptions;

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool, BytecodeVersion version) throws IOException {
    int len = data.readUnsignedShort();
    if (len > 0) {
      throwsExceptions = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        throwsExceptions.add(data.readUnsignedShort());
      }
    }
    else {
      throwsExceptions = Collections.emptyList();
    }
  }

  public @Nullable String getExcClassname(int index, ConstantPool pool) {
    int cpIndex = throwsExceptions.get(index);
    if (cpIndex <= 0 || cpIndex >= pool.getPool().size()) {
      return null;
    }

    PooledConstant constant = pool.getConstant(cpIndex);
    if (!(constant instanceof PrimitiveConstant primitive)) {
      return null;
    }

    String className = switch (primitive.type) {
      case CodeConstants.CONSTANT_Class -> resolveClassNameFromClassConstant(primitive, pool);
      case CodeConstants.CONSTANT_Utf8 -> primitive.value instanceof String ? (String)primitive.value : null;
      default -> null;
    };

    if (className == null) {
      return null;
    }

    String mapped = pool.buildNewClassname(className);
    return mapped != null ? mapped : className;
  }

  private static @Nullable String resolveClassNameFromClassConstant(PrimitiveConstant primitive, ConstantPool pool) {
    if (primitive.value instanceof String) {
      return (String)primitive.value;
    }
    if (!(primitive.value instanceof Integer)) {
      return null;
    }

    int nameIndex = (Integer)primitive.value;
    if (nameIndex <= 0 || nameIndex >= pool.getPool().size()) {
      return null;
    }

    PooledConstant nameConstant = pool.getConstant(nameIndex);
    if (!(nameConstant instanceof PrimitiveConstant namePrimitive)) {
      return null;
    }
    if (namePrimitive.type != CodeConstants.CONSTANT_Utf8 || !(namePrimitive.value instanceof String)) {
      return null;
    }
    return (String)namePrimitive.value;
  }

  public List<Integer> getThrowsExceptions() {
    return throwsExceptions;
  }

  public void setThrowsExceptions(List<Integer> throwsExceptions) {
    this.throwsExceptions = throwsExceptions;
  }
}
