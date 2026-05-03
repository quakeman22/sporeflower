// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.main.DecompilerContext;

import java.util.List;

final class CheckedExceptionSupport {
  private CheckedExceptionSupport() {
  }

  static boolean isCaughtByActiveCatches(String thrownException, List<String> activeCatchTypes) {
    for (String catchType : activeCatchTypes) {
      if ("java/lang/Throwable".equals(catchType)) {
        return true;
      }
      if (thrownException.equals(catchType) || isSubtypeOf(thrownException, catchType)) {
        return true;
      }
    }
    return false;
  }

  static boolean isSubtypeOf(String subType, String superType) {
    if (subType == null || superType == null) {
      return false;
    }
    if (subType.equals(superType)) {
      return true;
    }
    if (DecompilerContext.getStructContext().instanceOf(subType, superType)) {
      return true;
    }
    return isSubtypeByReflection(subType, superType);
  }

  static boolean isCheckedExceptionType(String exceptionType) {
    if (exceptionType == null) {
      return false;
    }

    if (isSubtypeOf(exceptionType, "java/lang/RuntimeException") || isSubtypeOf(exceptionType, "java/lang/Error")) {
      return false;
    }
    if (isSubtypeOf(exceptionType, "java/lang/Throwable")) {
      return true;
    }

    String simpleName = exceptionType.substring(exceptionType.lastIndexOf('/') + 1);
    if (simpleName.endsWith("RuntimeException") || simpleName.endsWith("Error")) {
      return false;
    }
    return simpleName.endsWith("Exception");
  }

  static boolean needsDeclaredCheckedThrowForCatchReachability(String catchType) {
    if (!isCheckedExceptionType(catchType)) {
      return false;
    }

    // javac accepts catch (Exception) and catch (Throwable) even when the try
    // body has no declared checked throws: unchecked RuntimeException/Error
    // flows are always possible and are assignment-compatible with those types.
    return !isUncheckedThrowableAssignableTo(catchType);
  }

  private static boolean isUncheckedThrowableAssignableTo(String catchType) {
    return isSubtypeOf("java/lang/RuntimeException", catchType) || isSubtypeOf("java/lang/Error", catchType);
  }

  static String toBinaryName(String internalName) {
    return internalName.replace('/', '.');
  }

  private static boolean isSubtypeByReflection(String subType, String superType) {
    try {
      Class<?> thrown = Class.forName(toBinaryName(subType), false, CheckedExceptionSupport.class.getClassLoader());
      Class<?> caught = Class.forName(toBinaryName(superType), false, CheckedExceptionSupport.class.getClassLoader());
      return caught.isAssignableFrom(thrown);
    }
    catch (ReflectiveOperationException ignored) {
      return false;
    }
  }
}
