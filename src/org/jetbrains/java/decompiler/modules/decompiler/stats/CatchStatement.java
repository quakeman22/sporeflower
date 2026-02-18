// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.DecHelper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.ValidationHelper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructExceptionsAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.gen.CodeType;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CatchStatement extends Statement {
  private static final ThreadLocal<Set<String>> RECURSION_GUARD = ThreadLocal.withInitial(java.util.HashSet::new);

  private final List<List<String>> exctstrings = new ArrayList<>();
  private final List<VarExprent> vars = new ArrayList<>();
  private final List<Exprent> resources = new ArrayList<>();

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  protected CatchStatement() {
    super(StatementType.TRY_CATCH);
  }

  protected CatchStatement(Statement head, Statement next, Set<Statement> setHandlers) {
    this();

    first = head;
    stats.addWithKey(first, first.id);

    for (StatEdge edge : head.getSuccessorEdges(StatEdge.TYPE_EXCEPTION)) {
      Statement stat = edge.getDestination();

      if (setHandlers.contains(stat)) {
        stats.addWithKey(stat, stat.id);
        exctstrings.add(new ArrayList<>(edge.getExceptions()));
        
        vars.add(new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                                new VarType(CodeType.OBJECT, 0, edge.getExceptions().get(0)),
                                // FIXME: for now simply the first type. Should get the first common superclass when possible.
                                DecompilerContext.getVarProcessor()));
      }
    }

    if (next != null) {
      post = next;
    }
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public static Statement isHead(Statement head) {
    if (head.getLastBasicType() != LastBasicType.GENERAL) {
      return null;
    }

    Set<Statement> setHandlers = DecHelper.getUniquePredExceptions(head);
    if (!setHandlers.isEmpty()) {
      int hnextcount = 0; // either no statements with connection to next, or more than 1

      Statement next = null;
      List<StatEdge> lstHeadSuccs = head.getSuccessorEdges(STATEDGE_DIRECT_ALL);
      if (!lstHeadSuccs.isEmpty() && lstHeadSuccs.get(0).getType() == StatEdge.TYPE_REGULAR) {
        next = lstHeadSuccs.get(0).getDestination();
        hnextcount = 2;
      }

      for (StatEdge edge : head.getSuccessorEdges(StatEdge.TYPE_EXCEPTION)) {
        Statement stat = edge.getDestination();

        boolean handlerok = true;

        if (edge.getExceptions() != null && setHandlers.contains(stat)) {
          if (stat.getLastBasicType() != LastBasicType.GENERAL) {
            handlerok = false;
          } else {
            List<StatEdge> lstStatSuccs = stat.getSuccessorEdges(STATEDGE_DIRECT_ALL);
            if (!lstStatSuccs.isEmpty() && lstStatSuccs.get(0).getType() == StatEdge.TYPE_REGULAR) {

              Statement statn = lstStatSuccs.get(0).getDestination();

              if (next == null) {
                next = statn;
              } else if (next != statn) {
                handlerok = false;
              }

              if (handlerok) {
                hnextcount++;
              }
            }
          }
        } else {
          handlerok = false;
        }

        if (!handlerok) {
          setHandlers.remove(stat);
        }
      }

      if (hnextcount != 1 && !setHandlers.isEmpty()) {
        List<Statement> lst = new ArrayList<>();
        lst.add(head);
        lst.addAll(setHandlers);

        for (Statement st : lst) {
          if (st.isMonitorEnter()) {
            return null;
          }
        }

        if (DecHelper.invalidHeadMerge(head)) {
          return null;
        }

        if (DecHelper.checkStatementExceptions(lst)) {
          return new CatchStatement(head, next, setHandlers);
        }
      }
    }
    return null;
  }

  @Override
  public TextBuffer toJava(int indent) {
    TextBuffer buf = new TextBuffer();
    List<String> renderedCatchTypesSoFar = new ArrayList<>();

    buf.append(ExprProcessor.listToJava(varDefinitions, indent));

    if (isLabeled()) {
      buf.appendIndent(indent).append("label").append(this.id).append(":").appendLineSeparator();
    }

    if (resources.isEmpty()) {
      buf.appendIndent(indent).append("try {").appendLineSeparator();
    }
    else {
      buf.appendIndent(indent).append("try (");

      if (resources.size() > 1) {
        buf.appendLineSeparator();
        buf.append(ExprProcessor.listToJava(resources, indent + 1));
        buf.appendIndent(indent);
      }
      else {
        buf.append(resources.get(0).toJava(indent + 1));
      }
      buf.append(") {").appendLineSeparator();
    }

    buf.append(ExprProcessor.jmpWrapper(first, indent + 1, true));
    buf.appendIndent(indent).append("}");

    for (int i = 1; i < stats.size(); i++) {
      Statement stat = stats.get(i);
      // map first instruction storing the exception to the catch statement
      BasicBlock block = stat.getBasichead().getBlock();
      if (!block.getSeq().isEmpty() && block.getInstruction(0).opcode == CodeConstants.opc_astore) {
        Integer offset = block.getOldOffset(0);
        if (offset > -1) buf.addBytecodeMapping(offset);
      }

      buf.append(" catch (");

      List<String> exception_types = exctstrings.get(i - 1);
      List<String> renderedTypes = getRenderedCatchTypes(first, exception_types, renderedCatchTypesSoFar);
      for (int exc_index = 0; exc_index < renderedTypes.size(); ++exc_index) {
        String name = ExprProcessor.getCastTypeName(new VarType(CodeType.OBJECT, 0, renderedTypes.get(exc_index)));
        if (renderedTypes.size() > 1 && exc_index > 0) { // multi-catch, Java 7 style
          buf.append(" | ");
        }
        buf.append(name);
      }
      renderedCatchTypesSoFar.addAll(renderedTypes);

      buf.append(" ");

      VarExprent var = vars.get(i - 1);

      validateType(exception_types, var.getVarType());

      // Temporarily set variable as not a definition, since we just wrote the type above
      try (var v = var.new DefinitionLocker()) {
        buf.append(var.toJava(indent));
      }

      buf.append(") {").appendLineSeparator();
      buf.append(ExprProcessor.jmpWrapper(stat, indent + 1, false)).appendIndent(indent)
        .append("}");
    }
    buf.appendLineSeparator();

    return buf;
  }

  private void validateType(List<String> exTypes, VarType exVarType) {
    // TODO: join together all types, then check if exVarType instanceof that
    // Not correct!!
    if (ValidationHelper.VALIDATE) {
//      VarType type = new VarType(CodeType.OBJECT, 0, exTypes.get(exTypes.size() - 1));
//      ValidationHelper.validateTrue(type.higherEqualInLatticeThan(exVarType), "Invalid exception type " + exVarType + " " + type);
    }
  }

  private static List<String> getRenderedCatchTypes(Statement tryBody, List<String> exceptionTypes, List<String> previousCatchTypes) {
    for (String exceptionType : exceptionTypes) {
      if (isCheckedException(exceptionType) && !canTryBodyThrow(tryBody, exceptionType)) {
        return Collections.singletonList(selectFallbackCatchType(previousCatchTypes));
      }
    }
    return exceptionTypes;
  }

  private static String selectFallbackCatchType(List<String> previousCatchTypes) {
    String[] candidates = {"java/lang/RuntimeException", "java/lang/Error", "java/lang/Exception", "java/lang/Throwable"};
    for (String candidate : candidates) {
      if (!isShadowedByPreviousCatch(candidate, previousCatchTypes)) {
        return candidate;
      }
    }
    return "java/lang/Throwable";
  }

  private static boolean isShadowedByPreviousCatch(String candidateType, List<String> previousCatchTypes) {
    for (String previousType : previousCatchTypes) {
      if (candidateType.equals(previousType) || DecompilerContext.getStructContext().instanceOf(candidateType, previousType)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCheckedException(String exceptionType) {
    if (exceptionType == null) {
      return false;
    }

    if ("java/lang/RuntimeException".equals(exceptionType) || "java/lang/Error".equals(exceptionType)) {
      return false;
    }

    if (DecompilerContext.getStructContext().instanceOf(exceptionType, "java/lang/Throwable")) {
      return !DecompilerContext.getStructContext().instanceOf(exceptionType, "java/lang/RuntimeException") &&
             !DecompilerContext.getStructContext().instanceOf(exceptionType, "java/lang/Error");
    }

    // Fallback for partial classpaths (e.g., tests without Java runtime loaded):
    // treat named Exception/Throwable types as checked unless they are known unchecked roots.
    return exceptionType.endsWith("Exception") || exceptionType.endsWith("Throwable");
  }

  private static boolean canTryBodyThrow(Statement tryBody, String exceptionType) {
    List<Exprent> exprents = new ArrayList<>();
    collectExprents(tryBody, exprents);

    for (Exprent exprent : exprents) {
      for (Exprent nested : exprent.getAllExprents(true, true)) {
        if (nested instanceof InvocationExprent invocation && invocationThrows(invocation, exceptionType)) {
          return true;
        }

        if (nested instanceof ExitExprent exit && exit.getExitType() == ExitExprent.Type.THROW) {
          Exprent value = exit.getValue();
          if (value != null) {
            VarType thrownType = value.getExprType();
            if (thrownType.type == CodeType.OBJECT && thrownType.value != null &&
                DecompilerContext.getStructContext().instanceOf(thrownType.value, exceptionType)) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private static void collectExprents(Statement stat, List<Exprent> out) {
    if (stat.getExprents() != null) {
      out.addAll(stat.getExprents());
      return;
    }

    out.addAll(stat.getStatExprents());
    for (Statement child : stat.getStats()) {
      collectExprents(child, out);
    }
  }

  private static boolean invocationThrows(InvocationExprent invocation, String exceptionType) {
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

    StructMethod method = cls.getMethod(invocation.getName(), invocation.getStringDescriptor());
    if (method == null) {
      method = cls.getMethodRecursive(invocation.getName(), invocation.getStringDescriptor());
    }
    if (method == null) {
      return true;
    }

    StructExceptionsAttribute exceptions = method.getAttribute(StructGeneralAttribute.ATTRIBUTE_EXCEPTIONS);
    if (exceptions == null) {
      StructClass currentClass = DecompilerContext.getContextProperty(DecompilerContext.CURRENT_CLASS);
      ClassWrapper currentWrapper = DecompilerContext.getContextProperty(DecompilerContext.CURRENT_CLASS_WRAPPER);
      if (currentClass != null && currentWrapper != null && currentClass.qualifiedName.equals(cls.qualifiedName) && method.containsCode()) {
        MethodWrapper methodWrapper = currentWrapper.getMethodWrapper(method.getName(), method.getDescriptor());
        if (methodWrapper != null && methodWrapper.root != null) {
          String recursionKey = currentClass.qualifiedName + " " + InterpreterUtil.makeUniqueKey(method.getName(), method.getDescriptor()) + " -> " + exceptionType;
          Set<String> stack = RECURSION_GUARD.get();
          if (stack.add(recursionKey)) {
            try {
              return canTryBodyThrow(methodWrapper.root.getFirst(), exceptionType);
            }
            finally {
              stack.remove(recursionKey);
              if (stack.isEmpty()) {
                RECURSION_GUARD.remove();
              }
            }
          }
        }
      }
      return true;
    }

    for (int i = 0; i < exceptions.getThrowsExceptions().size(); i++) {
      String thrownClass = exceptions.getExcClassname(i, cls.getPool());
      if (DecompilerContext.getStructContext().instanceOf(thrownClass, exceptionType)) {
        return true;
      }
    }

    return false;
  }

  private static boolean reflectionInvocationThrows(InvocationExprent invocation, String exceptionType) {
    try {
      Class<?> owner = Class.forName(toBinaryName(invocation.getClassname()));
      Class<?>[] params = toParameterClasses(MethodDescriptor.parseDescriptor(invocation.getStringDescriptor()).params);
      Class<?> catchType = Class.forName(toBinaryName(exceptionType));

      if ("<init>".equals(invocation.getName())) {
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
      } catch (NoSuchMethodException ignored) {
        method = owner.getMethod(invocation.getName(), params);
      }
      for (Class<?> thrown : method.getExceptionTypes()) {
        if (catchType.isAssignableFrom(thrown)) {
          return true;
        }
      }
    } catch (ReflectiveOperationException ignored) {
    }

    return false;
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
      return Class.forName(type.toString().replace('/', '.'));
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
      case OBJECT -> Class.forName(toBinaryName(type.value));
      default -> Class.forName("java.lang.Object");
    };
  }

  private static String toBinaryName(String internalName) {
    return internalName.replace('/', '.');
  }

  @Override
  public List<Exprent> getStatExprents() {
    List<Exprent> lst = new ArrayList<>(resources);
    lst.addAll(vars);
    return lst;
  }

  @Override
  public Statement getSimpleCopy() {
    CatchStatement cs = new CatchStatement();

    for (List<String> exc : this.exctstrings) {
      cs.exctstrings.add(new ArrayList<>(exc));
      cs.vars.add(new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                                 new VarType(CodeType.OBJECT, 0, exc.get(0)),
                                 DecompilerContext.getVarProcessor()));
    }

    return cs;
  }

  public void getOffset(BitSet values) {
    super.getOffset(values);

    for (Exprent exp : this.getResources()) {
      exp.getBytecodeRange(values);
    }
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public List<List<String>> getExctStrings() {
    return exctstrings;
  }

  public List<VarExprent> getVars() {
    return vars;
  }

  public List<Exprent> getResources() {
    return resources;
  }

  @Override
  public List<VarExprent> getImplicitlyDefinedVars() {
    List<VarExprent> vars = new ArrayList<>(getVars());

    // resource vars must also be included
    for (Exprent exp : getResources()) {
      if (exp instanceof AssignmentExprent assignment) {
        vars.add((VarExprent) assignment.getLeft());
      }
    }

    return vars;
  }
}
