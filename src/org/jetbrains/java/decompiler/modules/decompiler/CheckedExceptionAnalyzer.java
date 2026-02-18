// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.plugins.PluginContext;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructExceptionsAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.gen.CodeType;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CheckedExceptionAnalyzer {
  private static final ThreadLocal<CheckedExceptionAnalyzer> ACTIVE = new ThreadLocal<>();
  private static final String[] FALLBACK_CATCH_TYPES = {
    "java/lang/RuntimeException",
    "java/lang/Error",
    "java/lang/Exception",
    "java/lang/Throwable"
  };

  private final Map<String, List<String>> inferredCheckedExceptions = new HashMap<>();
  private final Map<String, ClassWrapper> detachedOwnClassWrappers = new HashMap<>();
  private final Set<String> inferenceStack = new HashSet<>();
  private final Set<String> throwabilityStack = new HashSet<>();

  private static final class MethodResolution {
    private final StructClass ownerClass;
    private final StructMethod method;

    private MethodResolution(StructClass ownerClass, StructMethod method) {
      this.ownerClass = ownerClass;
      this.method = method;
    }
  }

  public static @Nullable CheckedExceptionAnalyzer active() {
    return ACTIVE.get();
  }

  public static Scope activate(CheckedExceptionAnalyzer analyzer) {
    return new Scope(analyzer);
  }

  public CatchRewrite rewriteCatchTypes(Statement tryBody, List<String> exceptionTypes, List<String> previousCatchTypes) {
    List<String> renderedTypes = new ArrayList<>();
    List<String> removedCheckedTypes = new ArrayList<>();

    for (String exceptionType : exceptionTypes) {
      if (isCheckedExceptionType(exceptionType) && !canStatementThrow(tryBody, exceptionType)) {
        removedCheckedTypes.add(exceptionType);
      } else {
        renderedTypes.add(exceptionType);
      }
    }

    if (removedCheckedTypes.isEmpty()) {
      return new CatchRewrite(new ArrayList<>(exceptionTypes), Collections.emptyList(), false, false);
    }

    if (!renderedTypes.isEmpty()) {
      return new CatchRewrite(renderedTypes, removedCheckedTypes, true, false);
    }

    String fallback = selectFallbackCatchType(previousCatchTypes);
    if (fallback != null) {
      return new CatchRewrite(Collections.singletonList(fallback), removedCheckedTypes, true, true);
    }

    // No safe fallback left (all would be shadowed). Keep original to avoid introducing an unreachable duplicate catch.
    return new CatchRewrite(new ArrayList<>(exceptionTypes), removedCheckedTypes, false, false);
  }

  public List<String> inferMissingCheckedExceptions(StructClass ownerClass, ClassWrapper ownerWrapper, StructMethod method, MethodWrapper methodWrapper) {
    String methodKey = buildMethodKey(ownerClass, method);
    List<String> cached = inferredCheckedExceptions.get(methodKey);
    if (cached != null) {
      return cached;
    }
    if (!inferenceStack.add(methodKey)) {
      return Collections.emptyList();
    }

    try {
      if (methodWrapper == null || methodWrapper.root == null) {
        inferredCheckedExceptions.put(methodKey, Collections.emptyList());
        return Collections.emptyList();
      }

      LinkedHashSet<String> escaping = new LinkedHashSet<>();
      LinkedHashSet<String> callsiteCaughtTypes = method.hasModifier(CodeConstants.ACC_PRIVATE)
        ? collectSameClassCallsiteCaughtTypes(ownerClass, ownerWrapper, method)
        : new LinkedHashSet<>();
      collectEscapingCheckedExceptions(methodWrapper.root.getFirst(), ownerClass, ownerWrapper, Collections.emptyList(), escaping);
      LinkedHashSet<String> bodyInferred = new LinkedHashSet<>(escaping);
      augmentInferredExceptionsFromSameClassCallSites(method, methodWrapper, callsiteCaughtTypes, escaping);
      if (method.hasModifier(CodeConstants.ACC_PRIVATE)) {
        filterPrivateMethodInferredExceptionsByCallsiteCatches(escaping, bodyInferred, callsiteCaughtTypes);
      }
      List<String> inferred = new ArrayList<>(escaping);
      inferred = filterInferredExceptionsByOverrideCompatibility(ownerClass, method, inferred);
      inferredCheckedExceptions.put(methodKey, inferred);
      return inferred;
    }
    finally {
      inferenceStack.remove(methodKey);
    }
  }

  public boolean canStatementThrow(Statement statement, String exceptionType) {
    return canStatementThrow(statement, exceptionType, Collections.emptyList());
  }

  private boolean canStatementThrow(Statement statement, String exceptionType, List<String> activeCatchTypes) {
    if (statement == null) {
      return false;
    }

    if (statement instanceof CatchStatement catchStatement) {
      List<String> localCatchTypes = new ArrayList<>();
      for (List<String> catchTypes : catchStatement.getExctStrings()) {
        localCatchTypes.addAll(catchTypes);
      }
      List<String> tryCatchTypes = new ArrayList<>(activeCatchTypes);
      tryCatchTypes.addAll(localCatchTypes);

      if (canExprentsThrow(catchStatement.getResources(), exceptionType, tryCatchTypes)) {
        return true;
      }
      if (canStatementThrow(catchStatement.getFirst(), exceptionType, tryCatchTypes)) {
        return true;
      }
      for (int i = 1; i < catchStatement.getStats().size(); i++) {
        if (canStatementThrow(catchStatement.getStats().get(i), exceptionType, activeCatchTypes)) {
          return true;
        }
      }
      return false;
    }

    if (statement instanceof CatchAllStatement catchAllStatement) {
      List<String> tryCatchTypes = activeCatchTypes;
      if (!catchAllStatement.isFinally()) {
        tryCatchTypes = new ArrayList<>(activeCatchTypes);
        tryCatchTypes.add("java/lang/Throwable");
      }
      if (canStatementThrow(catchAllStatement.getFirst(), exceptionType, tryCatchTypes)) {
        return true;
      }
      return canStatementThrow(catchAllStatement.getHandler(), exceptionType, activeCatchTypes);
    }

    List<Exprent> exprents = statement.getExprents() != null ? statement.getExprents() : statement.getStatExprents();
    if (canExprentsThrow(exprents, exceptionType, activeCatchTypes)) {
      return true;
    }

    for (Statement child : statement.getStats()) {
      if (canStatementThrow(child, exceptionType, activeCatchTypes)) {
        return true;
      }
    }

    return false;
  }

  private boolean canExprentsThrow(List<Exprent> exprents, String exceptionType, List<String> activeCatchTypes) {
    for (Exprent exprent : exprents) {
      for (Exprent nested : exprent.getAllExprents(true, true)) {
        if (nested instanceof InvocationExprent invocation) {
          if (invocationThrows(invocation, exceptionType) && !isCaughtByActiveCatches(exceptionType, activeCatchTypes)) {
            return true;
          }
        }
        else if (nested instanceof ExitExprent exit && exit.getExitType() == ExitExprent.Type.THROW) {
          Exprent value = exit.getValue();
          if (value != null) {
            VarType thrownType = value.getExprType();
            if (thrownType.type == CodeType.OBJECT && thrownType.value != null && isSubtypeOf(thrownType.value, exceptionType)) {
              if (!isCaughtByActiveCatches(thrownType.value, activeCatchTypes)) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  private void collectEscapingCheckedExceptions(
    Statement statement,
    StructClass ownerClass,
    ClassWrapper ownerWrapper,
    List<String> activeCatchTypes,
    LinkedHashSet<String> escapingExceptions
  ) {
    if (statement == null) {
      return;
    }

    if (statement instanceof CatchStatement catchStatement) {
      List<String> localCatchTypes = new ArrayList<>();
      for (List<String> catchTypes : catchStatement.getExctStrings()) {
        localCatchTypes.addAll(catchTypes);
      }
      List<String> tryCatchTypes = new ArrayList<>(activeCatchTypes);
      tryCatchTypes.addAll(localCatchTypes);

      collectEscapingFromExprents(catchStatement.getResources(), ownerClass, ownerWrapper, tryCatchTypes, escapingExceptions);
      collectEscapingCheckedExceptions(catchStatement.getFirst(), ownerClass, ownerWrapper, tryCatchTypes, escapingExceptions);
      for (int i = 1; i < catchStatement.getStats().size(); i++) {
        collectEscapingCheckedExceptions(catchStatement.getStats().get(i), ownerClass, ownerWrapper, activeCatchTypes, escapingExceptions);
      }
      return;
    }

    if (statement instanceof CatchAllStatement catchAllStatement) {
      List<String> tryCatchTypes = activeCatchTypes;
      if (!catchAllStatement.isFinally()) {
        tryCatchTypes = new ArrayList<>(activeCatchTypes);
        tryCatchTypes.add("java/lang/Throwable");
      }
      collectEscapingCheckedExceptions(catchAllStatement.getFirst(), ownerClass, ownerWrapper, tryCatchTypes, escapingExceptions);
      collectEscapingCheckedExceptions(catchAllStatement.getHandler(), ownerClass, ownerWrapper, activeCatchTypes, escapingExceptions);
      return;
    }

    if (statement.getExprents() != null) {
      collectEscapingFromExprents(statement.getExprents(), ownerClass, ownerWrapper, activeCatchTypes, escapingExceptions);
    } else {
      collectEscapingFromExprents(statement.getStatExprents(), ownerClass, ownerWrapper, activeCatchTypes, escapingExceptions);
    }

    for (Statement child : statement.getStats()) {
      collectEscapingCheckedExceptions(child, ownerClass, ownerWrapper, activeCatchTypes, escapingExceptions);
    }
  }

  private void collectEscapingFromExprents(
    List<Exprent> exprents,
    StructClass ownerClass,
    ClassWrapper ownerWrapper,
    List<String> activeCatchTypes,
    LinkedHashSet<String> escapingExceptions
  ) {
    for (Exprent exprent : exprents) {
      List<Exprent> nestedExprents = exprent.getAllExprents(true, true);
      for (Exprent nested : nestedExprents) {
        if (nested instanceof InvocationExprent invocation) {
          for (String thrownException : getInvocationCheckedExceptions(invocation, ownerClass, ownerWrapper)) {
            if (!isCaughtByActiveCatches(thrownException, activeCatchTypes)) {
              escapingExceptions.add(thrownException);
            }
          }
        }
        else if (nested instanceof ExitExprent exitExprent && exitExprent.getExitType() == ExitExprent.Type.THROW) {
          Exprent throwValue = exitExprent.getValue();
          if (throwValue != null) {
            VarType thrownType = throwValue.getExprType();
            if (thrownType.type == CodeType.OBJECT && thrownType.value != null && isCheckedExceptionType(thrownType.value)) {
              if (!isCaughtByActiveCatches(thrownType.value, activeCatchTypes)) {
                escapingExceptions.add(thrownType.value);
              }
            }
          }
        }
      }
    }
  }

  private void augmentInferredExceptionsFromSameClassCallSites(
    StructMethod targetMethod,
    MethodWrapper targetWrapper,
    Set<String> callsiteCaughtTypes,
    LinkedHashSet<String> escapingExceptions
  ) {
    // Restrict callsite-based augmentation to private methods to avoid widening
    // externally visible or overriding signatures.
    if (!targetMethod.hasModifier(CodeConstants.ACC_PRIVATE)) {
      return;
    }

    if (targetWrapper.root == null || callsiteCaughtTypes.isEmpty()) {
      return;
    }

    for (String catchType : callsiteCaughtTypes) {
      if (!isCheckedExceptionType(catchType)) {
        continue;
      }
      // Broad catch-all types are too imprecise for signature synthesis and tend to
      // create cascading false positives on unresolved/library invocations.
      if (isBroadCheckedCatchType(catchType)) {
        continue;
      }
      if (canStatementThrow(targetWrapper.root.getFirst(), catchType)) {
        escapingExceptions.add(catchType);
      }
    }
  }

  private static boolean isBroadCheckedCatchType(String catchType) {
    return "java/lang/Exception".equals(catchType) || "java/lang/Throwable".equals(catchType);
  }

  private static void filterPrivateMethodInferredExceptionsByCallsiteCatches(
    LinkedHashSet<String> inferredExceptions,
    Set<String> bodyInferredExceptions,
    Set<String> callsiteCaughtTypes
  ) {
    if (bodyInferredExceptions.isEmpty() && callsiteCaughtTypes.isEmpty()) {
      inferredExceptions.clear();
      return;
    }

    if (callsiteCaughtTypes.isEmpty()) {
      inferredExceptions.removeIf(exceptionType -> !bodyInferredExceptions.contains(exceptionType));
      return;
    }

    inferredExceptions.removeIf(exceptionType ->
      !bodyInferredExceptions.contains(exceptionType)
        && !isCoveredByCallsiteCatch(exceptionType, callsiteCaughtTypes)
    );
  }

  private static boolean isCoveredByCallsiteCatch(String exceptionType, Set<String> callsiteCaughtTypes) {
    for (String catchType : callsiteCaughtTypes) {
      if (isSubtypeOf(exceptionType, catchType)) {
        return true;
      }
    }
    return false;
  }

  private LinkedHashSet<String> collectSameClassCallsiteCaughtTypes(
    StructClass ownerClass,
    ClassWrapper ownerWrapper,
    StructMethod targetMethod
  ) {
    LinkedHashSet<String> callsiteCaughtTypes = new LinkedHashSet<>();
    for (MethodWrapper callerWrapper : ownerWrapper.getMethods()) {
      if (callerWrapper.root == null) {
        continue;
      }
      collectCallsiteCaughtTypes(
        callerWrapper.root.getFirst(),
        ownerClass,
        targetMethod,
        Collections.emptyList(),
        callsiteCaughtTypes
      );
    }
    return callsiteCaughtTypes;
  }

  private void collectCallsiteCaughtTypes(
    Statement statement,
    StructClass ownerClass,
    StructMethod targetMethod,
    List<String> activeCatchTypes,
    LinkedHashSet<String> caughtTypes
  ) {
    if (statement == null) {
      return;
    }

    if (statement instanceof CatchStatement catchStatement) {
      List<String> localCatchTypes = new ArrayList<>();
      for (List<String> catchTypes : catchStatement.getExctStrings()) {
        localCatchTypes.addAll(catchTypes);
      }
      List<String> tryCatchTypes = new ArrayList<>(activeCatchTypes);
      tryCatchTypes.addAll(localCatchTypes);

      collectCallsiteCaughtTypesFromExprents(catchStatement.getResources(), ownerClass, targetMethod, tryCatchTypes, caughtTypes);
      collectCallsiteCaughtTypes(catchStatement.getFirst(), ownerClass, targetMethod, tryCatchTypes, caughtTypes);
      for (int i = 1; i < catchStatement.getStats().size(); i++) {
        collectCallsiteCaughtTypes(catchStatement.getStats().get(i), ownerClass, targetMethod, activeCatchTypes, caughtTypes);
      }
      return;
    }

    if (statement instanceof CatchAllStatement catchAllStatement) {
      // Catch-all handlers are too broad for checked-throws inference and can easily over-approximate.
      collectCallsiteCaughtTypes(catchAllStatement.getFirst(), ownerClass, targetMethod, activeCatchTypes, caughtTypes);
      collectCallsiteCaughtTypes(catchAllStatement.getHandler(), ownerClass, targetMethod, activeCatchTypes, caughtTypes);
      return;
    }

    if (statement.getExprents() != null) {
      collectCallsiteCaughtTypesFromExprents(statement.getExprents(), ownerClass, targetMethod, activeCatchTypes, caughtTypes);
    } else {
      collectCallsiteCaughtTypesFromExprents(statement.getStatExprents(), ownerClass, targetMethod, activeCatchTypes, caughtTypes);
    }

    for (Statement child : statement.getStats()) {
      collectCallsiteCaughtTypes(child, ownerClass, targetMethod, activeCatchTypes, caughtTypes);
    }
  }

  private static void collectCallsiteCaughtTypesFromExprents(
    List<Exprent> exprents,
    StructClass ownerClass,
    StructMethod targetMethod,
    List<String> activeCatchTypes,
    LinkedHashSet<String> caughtTypes
  ) {
    for (Exprent exprent : exprents) {
      for (Exprent nested : exprent.getAllExprents(true, true)) {
        if (nested instanceof InvocationExprent invocation && isSameClassInvocation(invocation, ownerClass, targetMethod)) {
          for (String catchType : activeCatchTypes) {
            caughtTypes.add(catchType);
          }
        }
      }
    }
  }

  private static boolean isSameClassInvocation(InvocationExprent invocation, StructClass ownerClass, StructMethod targetMethod) {
    if (!targetMethod.getName().equals(invocation.getName())
      || !targetMethod.getDescriptor().equals(invocation.getStringDescriptor())) {
      return false;
    }

    String invocationClass = invocation.getClassname();
    return invocationClass != null && invocationClass.equals(ownerClass.qualifiedName);
  }

  private List<String> getInvocationCheckedExceptions(InvocationExprent invocation, StructClass ownerClass, ClassWrapper ownerWrapper) {
    String className = invocation.getClassname();
    if (className == null) {
      return Collections.emptyList();
    }

    StructClass invokedClass = DecompilerContext.getStructContext().getClass(className);
    if (invokedClass != null) {
      MethodResolution resolution = findMethodResolution(invokedClass, invocation);
      if (resolution == null) {
        return Collections.emptyList();
      }
      StructClass methodOwner = resolution.ownerClass;
      StructMethod invokedMethod = resolution.method;

      List<String> declared = getDeclaredCheckedExceptions(methodOwner, invokedMethod);
      if (!declared.isEmpty()) {
        return declared;
      }

      if (methodOwner.isOwn() && invokedMethod.containsCode()) {
        // Cross-class inference for own classes is required when missing throws metadata
        // exists on callees outside the current class (common in obfuscated jars).
        ClassWrapper invokedClassWrapper = resolveClassWrapper(methodOwner, ownerClass, ownerWrapper);
        MethodWrapper invokedWrapper = invokedClassWrapper == null
          ? null
          : invokedClassWrapper.getMethodWrapper(invokedMethod.getName(), invokedMethod.getDescriptor());
        if (invokedWrapper != null) {
          return inferMissingCheckedExceptions(methodOwner, invokedClassWrapper, invokedMethod, invokedWrapper);
        }
      }

      if (className.startsWith("java/")) {
        return getReflectionCheckedExceptions(invocation);
      }

      return Collections.emptyList();
    }

    if (className.startsWith("java/")) {
      return getReflectionCheckedExceptions(invocation);
    }

    return Collections.emptyList();
  }

  private @Nullable ClassWrapper resolveClassWrapper(StructClass invokedClass, StructClass ownerClass, ClassWrapper ownerWrapper) {
    // Fast path for same-class calls: re-use wrapper already attached to current context.
    if (ownerClass != null
      && ownerWrapper != null
      && ownerClass.qualifiedName.equals(invokedClass.qualifiedName)) {
      return ownerWrapper;
    }

    // Cross-class path: resolve wrapper via root class map so we can recursively infer
    // checked throws for other own classes in the same decompilation session.
    ClassesProcessor processor = DecompilerContext.getClassProcessor();
    if (processor == null) {
      return null;
    }

    ClassesProcessor.ClassNode node = processor.getMapRootClasses().get(invokedClass.qualifiedName);
    if (node != null) {
      ClassWrapper wrapper = node.getWrapper();
      if (wrapper != null) {
        return wrapper;
      }
    }

    // If wrappers were already destroyed for that class, build a detached wrapper on demand.
    // This keeps inference behavior stable regardless of class write ordering.
    return getOrCreateDetachedClassWrapper(invokedClass);
  }

  private @Nullable ClassWrapper getOrCreateDetachedClassWrapper(StructClass invokedClass) {
    if (!invokedClass.isOwn()) {
      return null;
    }

    ClassWrapper cached = detachedOwnClassWrappers.get(invokedClass.qualifiedName);
    if (cached != null) {
      return cached;
    }

    ClassWrapper created = createDetachedClassWrapper(invokedClass);
    if (created != null) {
      detachedOwnClassWrappers.put(invokedClass.qualifiedName, created);
    }
    return created;
  }

  private static @Nullable ClassWrapper createDetachedClassWrapper(StructClass invokedClass) {
    StructClass previousClass = DecompilerContext.getContextProperty(DecompilerContext.CURRENT_CLASS);
    ClassWrapper previousWrapper = DecompilerContext.getContextProperty(DecompilerContext.CURRENT_CLASS_WRAPPER);
    try {
      ClassWrapper wrapper = new ClassWrapper(invokedClass);
      wrapper.init(PluginContext.getCurrentContext().getLanguageSpec(invokedClass));
      return wrapper;
    }
    catch (Throwable ignored) {
      return null;
    }
    finally {
      DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS, previousClass);
      DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_WRAPPER, previousWrapper);
    }
  }

  private boolean invocationThrows(InvocationExprent invocation, String exceptionType) {
    String className = invocation.getClassname();
    if (className == null) {
      return true;
    }

    StructClass cls = DecompilerContext.getStructContext().getClass(className);
    if (cls == null) {
      if (className.startsWith("java/")) {
        return reflectionInvocationThrows(invocation, exceptionType);
      }
      return true;
    }

    MethodResolution resolution = findMethodResolution(cls, invocation);
    if (resolution == null) {
      return true;
    }
    StructClass methodOwner = resolution.ownerClass;
    StructMethod method = resolution.method;

    StructExceptionsAttribute exceptions = method.getAttribute(StructGeneralAttribute.ATTRIBUTE_EXCEPTIONS);
    if (exceptions == null) {
      if (methodOwner.qualifiedName.startsWith("java/") || className.startsWith("java/")) {
        return reflectionInvocationThrows(invocation, exceptionType);
      }

      StructClass currentClass = DecompilerContext.getContextProperty(DecompilerContext.CURRENT_CLASS);
      ClassWrapper currentWrapper = DecompilerContext.getContextProperty(DecompilerContext.CURRENT_CLASS_WRAPPER);
      // Mirror getInvocationCheckedExceptions behavior here: if metadata is missing,
      // analyze own-class callees directly (including cross-class) instead of assuming
      // they may throw everything.
      ClassWrapper resolvedWrapper = resolveClassWrapper(methodOwner, currentClass, currentWrapper);
      if (resolvedWrapper != null && method.containsCode()) {
        MethodWrapper methodWrapper = resolvedWrapper.getMethodWrapper(method.getName(), method.getDescriptor());
        if (methodWrapper != null && methodWrapper.root != null) {
          String recursionKey = buildMethodKey(methodOwner, method) + " -> " + exceptionType;
          if (throwabilityStack.add(recursionKey)) {
            try {
              return canStatementThrow(methodWrapper.root.getFirst(), exceptionType);
            }
            finally {
              throwabilityStack.remove(recursionKey);
            }
          }
        }
      }

      return true;
    }

    boolean sawMalformedThrowsEntry = false;
    for (int i = 0; i < exceptions.getThrowsExceptions().size(); i++) {
      String thrownClass = exceptions.getExcClassname(i, methodOwner.getPool());
      if (thrownClass == null) {
        sawMalformedThrowsEntry = true;
        continue;
      }
      if (isSubtypeOf(thrownClass, exceptionType)) {
        return true;
      }
    }

    if (sawMalformedThrowsEntry) {
      // Malformed Exceptions attributes are common in obfuscated/J2ME inputs.
      // Keep catch-rewrite logic conservative when declared throws cannot be decoded.
      return true;
    }

    return false;
  }

  private static List<String> getDeclaredCheckedExceptions(StructClass ownerClass, StructMethod method) {
    StructExceptionsAttribute exceptionsAttribute = method.getAttribute(StructGeneralAttribute.ATTRIBUTE_EXCEPTIONS);
    if (exceptionsAttribute == null) {
      return Collections.emptyList();
    }

    List<String> checkedExceptions = new ArrayList<>();
    for (int i = 0; i < exceptionsAttribute.getThrowsExceptions().size(); i++) {
      String exceptionClass = exceptionsAttribute.getExcClassname(i, ownerClass.getPool());
      if (exceptionClass != null && isCheckedExceptionType(exceptionClass)) {
        checkedExceptions.add(exceptionClass);
      }
    }
    return checkedExceptions;
  }

  private static @Nullable MethodResolution findMethodResolution(StructClass cls, InvocationExprent invocation) {
    String invocationName = invocation.getName();
    String descriptor = invocation.getStringDescriptor();

    MethodResolution resolved = findMethodInHierarchy(cls, invocationName, descriptor, new HashSet<>());
    if (resolved != null) {
      return resolved;
    }

    PoolInterceptor interceptor = DecompilerContext.getPoolInterceptor();
    if (interceptor == null) {
      return null;
    }

    String mapped = interceptor.getName(cls.qualifiedName + " " + invocationName + " " + descriptor);
    if (mapped == null) {
      return null;
    }

    int firstSpace = mapped.indexOf(' ');
    int secondSpace = mapped.indexOf(' ', firstSpace + 1);
    if (firstSpace <= 0 || secondSpace <= firstSpace) {
      return null;
    }

    String renamedMethodName = mapped.substring(firstSpace + 1, secondSpace);
    return findMethodInHierarchy(cls, renamedMethodName, descriptor, new HashSet<>());
  }

  private static @Nullable MethodResolution findMethodInHierarchy(
    StructClass cls,
    String methodName,
    String descriptor,
    Set<String> visited
  ) {
    if (!visited.add(cls.qualifiedName)) {
      return null;
    }

    StructMethod method = cls.getMethod(methodName, descriptor);
    if (method != null) {
      return new MethodResolution(cls, method);
    }

    if (cls.superClass != null) {
      StructClass superClass = DecompilerContext.getStructContext().getClass(cls.superClass.getString());
      if (superClass != null) {
        MethodResolution fromSuper = findMethodInHierarchy(superClass, methodName, descriptor, visited);
        if (fromSuper != null) {
          return fromSuper;
        }
      }
    }

    for (String interfaceName : cls.getInterfaceNames()) {
      StructClass intf = DecompilerContext.getStructContext().getClass(interfaceName);
      if (intf != null) {
        MethodResolution fromInterface = findMethodInHierarchy(intf, methodName, descriptor, visited);
        if (fromInterface != null) {
          return fromInterface;
        }
      }
    }

    return null;
  }

  private static String selectFallbackCatchType(List<String> previousCatchTypes) {
    for (String candidate : FALLBACK_CATCH_TYPES) {
      if (!isShadowedByPreviousCatch(candidate, previousCatchTypes)) {
        return candidate;
      }
    }
    return null;
  }

  private static boolean isShadowedByPreviousCatch(String candidateType, List<String> previousCatchTypes) {
    for (String previousType : previousCatchTypes) {
      if (candidateType.equals(previousType) || isSubtypeOf(candidateType, previousType)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCaughtByActiveCatches(String thrownException, List<String> activeCatchTypes) {
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

  private static boolean isSubtypeOf(String subType, String superType) {
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

  private static boolean isCheckedExceptionType(String exceptionType) {
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

  private List<String> filterInferredExceptionsByOverrideCompatibility(StructClass ownerClass, StructMethod method, List<String> inferredExceptions) {
    // Never synthesize throws that would make an override signature illegal to compile.
    // If a super declaration has no checked throws, inferred checked throws are dropped.
    if (inferredExceptions.isEmpty()) {
      return inferredExceptions;
    }
    if (CodeConstants.INIT_NAME.equals(method.getName())
      || method.hasModifier(CodeConstants.ACC_PRIVATE)
      || method.hasModifier(CodeConstants.ACC_STATIC)) {
      return inferredExceptions;
    }

    List<List<String>> overriddenThrows = collectOverriddenMethodCheckedThrows(ownerClass, method);
    if (overriddenThrows.isEmpty()) {
      return inferredExceptions;
    }

    List<String> compatible = new ArrayList<>();
    for (String inferred : inferredExceptions) {
      boolean allowed = true;
      for (List<String> declared : overriddenThrows) {
        if (declared.isEmpty()) {
          allowed = false;
          break;
        }

        boolean coveredByDeclaration = false;
        for (String declaredType : declared) {
          if (isSubtypeOf(inferred, declaredType)) {
            coveredByDeclaration = true;
            break;
          }
        }
        if (!coveredByDeclaration) {
          allowed = false;
          break;
        }
      }

      if (allowed) {
        compatible.add(inferred);
      }
    }

    return compatible;
  }

  private List<List<String>> collectOverriddenMethodCheckedThrows(StructClass ownerClass, StructMethod method) {
    List<List<String>> declarations = new ArrayList<>();
    String originalName = getOriginalMethodName(ownerClass, method);
    String descriptor = method.getDescriptor();
    Set<String> visited = new HashSet<>();

    if (ownerClass.superClass != null) {
      collectOverriddenMethodCheckedThrows(ownerClass.superClass.getString(), originalName, descriptor, visited, declarations);
    }
    for (String ifaceName : ownerClass.getInterfaceNames()) {
      collectOverriddenMethodCheckedThrows(ifaceName, originalName, descriptor, visited, declarations);
    }

    return declarations;
  }

  private void collectOverriddenMethodCheckedThrows(
    String className,
    String originalMethodName,
    String descriptor,
    Set<String> visited,
    List<List<String>> declarations
  ) {
    if (className == null || !visited.add(className)) {
      return;
    }

    StructClass cls = DecompilerContext.getStructContext().getClass(className);
    if (cls == null) {
      if (className.startsWith("java/")) {
        List<String> reflected = reflectionMethodCheckedExceptions(className, originalMethodName, descriptor);
        if (reflected != null) {
          declarations.add(reflected);
        }
      }
      return;
    }

    StructMethod declaredMethod = findMethodByOriginalName(cls, originalMethodName, descriptor);
    if (declaredMethod != null) {
      declarations.add(getDeclaredCheckedExceptions(cls, declaredMethod));
    }

    if (cls.superClass != null) {
      collectOverriddenMethodCheckedThrows(cls.superClass.getString(), originalMethodName, descriptor, visited, declarations);
    }
    for (String ifaceName : cls.getInterfaceNames()) {
      collectOverriddenMethodCheckedThrows(ifaceName, originalMethodName, descriptor, visited, declarations);
    }
  }

  private static @Nullable StructMethod findMethodByOriginalName(StructClass ownerClass, String originalMethodName, String descriptor) {
    for (StructMethod method : ownerClass.getMethods()) {
      if (!descriptor.equals(method.getDescriptor())) {
        continue;
      }
      if (originalMethodName.equals(getOriginalMethodName(ownerClass, method))) {
        return method;
      }
    }
    return null;
  }

  private static String getOriginalMethodName(StructClass ownerClass, StructMethod method) {
    PoolInterceptor interceptor = DecompilerContext.getPoolInterceptor();
    if (interceptor == null) {
      return method.getName();
    }

    String oldName = interceptor.getOldName(ownerClass.qualifiedName + " " + method.getName() + " " + method.getDescriptor());
    if (oldName == null) {
      return method.getName();
    }

    int split = oldName.indexOf(' ');
    return split <= 0 ? oldName : oldName.substring(0, split);
  }

  private static @Nullable List<String> reflectionMethodCheckedExceptions(String className, String methodName, String descriptor) {
    try {
      Class<?> owner = Class.forName(toBinaryName(className), false, CheckedExceptionAnalyzer.class.getClassLoader());
      Class<?>[] params = toParameterClasses(MethodDescriptor.parseDescriptor(descriptor).params);

      Method method;
      try {
        method = owner.getDeclaredMethod(methodName, params);
      }
      catch (NoSuchMethodException ignored) {
        method = owner.getMethod(methodName, params);
      }

      List<String> checked = new ArrayList<>();
      for (Class<?> thrown : method.getExceptionTypes()) {
        String internal = thrown.getName().replace('.', '/');
        if (isCheckedExceptionType(internal)) {
          checked.add(internal);
        }
      }
      return checked;
    }
    catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private static boolean reflectionInvocationThrows(InvocationExprent invocation, String exceptionType) {
    try {
      Class<?> owner = Class.forName(toBinaryName(invocation.getClassname()), false, CheckedExceptionAnalyzer.class.getClassLoader());
      Class<?>[] params = toParameterClasses(MethodDescriptor.parseDescriptor(invocation.getStringDescriptor()).params);
      Class<?> catchType = Class.forName(toBinaryName(exceptionType), false, CheckedExceptionAnalyzer.class.getClassLoader());

      if (CodeConstants.INIT_NAME.equals(invocation.getName())) {
        Constructor<?> ctor = owner.getDeclaredConstructor(params);
        for (Class<?> thrown : ctor.getExceptionTypes()) {
          if (catchType.isAssignableFrom(thrown)) {
            return true;
          }
        }
        return false;
      }

      Method method;
      try {
        method = owner.getDeclaredMethod(invocation.getName(), params);
      }
      catch (NoSuchMethodException ignored) {
        method = owner.getMethod(invocation.getName(), params);
      }
      for (Class<?> thrown : method.getExceptionTypes()) {
        if (catchType.isAssignableFrom(thrown)) {
          return true;
        }
      }
    }
    catch (ReflectiveOperationException ignored) {
    }

    return false;
  }

  private static List<String> getReflectionCheckedExceptions(InvocationExprent invocation) {
    try {
      Class<?> owner = Class.forName(toBinaryName(invocation.getClassname()), false, CheckedExceptionAnalyzer.class.getClassLoader());
      Class<?>[] parameterTypes = toParameterClasses(MethodDescriptor.parseDescriptor(invocation.getStringDescriptor()).params);
      List<String> checkedExceptions = new ArrayList<>();

      if (CodeConstants.INIT_NAME.equals(invocation.getName())) {
        Constructor<?> constructor = owner.getDeclaredConstructor(parameterTypes);
        for (Class<?> exceptionType : constructor.getExceptionTypes()) {
          String internalName = exceptionType.getName().replace('.', '/');
          if (isCheckedExceptionType(internalName)) {
            checkedExceptions.add(internalName);
          }
        }
        return checkedExceptions;
      }

      Method method;
      try {
        method = owner.getDeclaredMethod(invocation.getName(), parameterTypes);
      }
      catch (NoSuchMethodException ignored) {
        method = owner.getMethod(invocation.getName(), parameterTypes);
      }

      for (Class<?> exceptionType : method.getExceptionTypes()) {
        String internalName = exceptionType.getName().replace('.', '/');
        if (isCheckedExceptionType(internalName)) {
          checkedExceptions.add(internalName);
        }
      }
      return checkedExceptions;
    }
    catch (ReflectiveOperationException ignored) {
      return Collections.emptyList();
    }
  }

  private static Class<?>[] toParameterClasses(VarType[] params) throws ClassNotFoundException {
    Class<?>[] result = new Class<?>[params.length];
    for (int i = 0; i < params.length; i++) {
      result[i] = toClass(params[i]);
    }
    return result;
  }

  private static Class<?> toClass(VarType type) throws ClassNotFoundException {
    if (type.arrayDim > 0) {
      return Class.forName(type.toString().replace('/', '.'), false, CheckedExceptionAnalyzer.class.getClassLoader());
    }

    return switch (type.type) {
      case BOOLEAN -> boolean.class;
      case BYTE -> byte.class;
      case CHAR -> char.class;
      case SHORT -> short.class;
      case INT -> int.class;
      case LONG -> long.class;
      case FLOAT -> float.class;
      case DOUBLE -> double.class;
      case OBJECT -> Class.forName(toBinaryName(type.value), false, CheckedExceptionAnalyzer.class.getClassLoader());
      default -> Class.forName("java.lang.Object", false, CheckedExceptionAnalyzer.class.getClassLoader());
    };
  }

  private static String toBinaryName(String internalName) {
    return internalName.replace('/', '.');
  }

  private static boolean isSubtypeByReflection(String subType, String superType) {
    try {
      Class<?> thrown = Class.forName(toBinaryName(subType), false, CheckedExceptionAnalyzer.class.getClassLoader());
      Class<?> caught = Class.forName(toBinaryName(superType), false, CheckedExceptionAnalyzer.class.getClassLoader());
      return caught.isAssignableFrom(thrown);
    }
    catch (ReflectiveOperationException ignored) {
      return false;
    }
  }

  private static String buildMethodKey(StructClass ownerClass, StructMethod method) {
    return ownerClass.qualifiedName + " " + InterpreterUtil.makeUniqueKey(method.getName(), method.getDescriptor());
  }

  public static final class CatchRewrite {
    private final List<String> renderedTypes;
    private final List<String> removedCheckedTypes;
    private final boolean rewritten;
    private final boolean fallbackUsed;

    private CatchRewrite(List<String> renderedTypes, List<String> removedCheckedTypes, boolean rewritten, boolean fallbackUsed) {
      this.renderedTypes = renderedTypes;
      this.removedCheckedTypes = removedCheckedTypes;
      this.rewritten = rewritten;
      this.fallbackUsed = fallbackUsed;
    }

    public List<String> getRenderedTypes() {
      return renderedTypes;
    }

    public List<String> getRemovedCheckedTypes() {
      return removedCheckedTypes;
    }

    public boolean isRewritten() {
      return rewritten;
    }

    public boolean isFallbackUsed() {
      return fallbackUsed;
    }
  }

  public static final class Scope implements AutoCloseable {
    private final @Nullable CheckedExceptionAnalyzer previous;
    private boolean closed;

    private Scope(CheckedExceptionAnalyzer analyzer) {
      this.previous = ACTIVE.get();
      ACTIVE.set(analyzer);
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      if (previous == null) {
        ACTIVE.remove();
      }
      else {
        ACTIVE.set(previous);
      }
    }
  }
}
