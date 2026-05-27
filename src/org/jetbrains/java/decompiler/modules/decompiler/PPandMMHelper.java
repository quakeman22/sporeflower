// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.flow.DirectEdge;
import org.jetbrains.java.decompiler.modules.decompiler.flow.DirectEdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.flow.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.flow.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.flow.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.Pair;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class PPandMMHelper {

  private boolean exprentReplaced;
  private VarProcessor varProc;
  private DirectGraph dgraph;

  public PPandMMHelper(VarProcessor varProc) {
    this.varProc = varProc;
  }

  public boolean findPPandMM(RootStatement root) {

    FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
    this.dgraph = flatthelper.buildDirectGraph(root);

    LinkedList<DirectNode> stack = new LinkedList<>();
    stack.add(this.dgraph.first);

    HashSet<DirectNode> setVisited = new HashSet<>();

    boolean res = false;

    while (!stack.isEmpty()) {

      DirectNode node = stack.removeFirst();

      if (setVisited.contains(node)) {
        continue;
      }
      setVisited.add(node);

      res |= processExprentList(node.exprents);

      for (DirectEdge suc : node.getSuccessors(DirectEdgeType.REGULAR)) {
        stack.add(suc.getDestination());
      }
    }

    return res;
  }

  private boolean processExprentList(List<Exprent> lst) {

    boolean result = false;

    for (int i = 0; i < lst.size(); i++) {
      Exprent exprent = lst.get(i);
      exprentReplaced = false;

      Exprent retexpr = processExprentRecursive(exprent);
      if (retexpr != null) {
        lst.set(i, retexpr);

        result = true;
        i--; // process the same exprent again
      }

      result |= exprentReplaced;
    }

    return result;
  }

  private Exprent processExprentRecursive(Exprent exprent) {

    boolean replaced = true;
    while (replaced) {
      replaced = false;

      for (Exprent expr : exprent.getAllExprents()) {
        Exprent retexpr = processExprentRecursive(expr);
        if (retexpr != null) {
          exprent.replaceExprent(expr, retexpr);
          retexpr.addBytecodeOffsets(expr.bytecode);
          replaced = true;
          exprentReplaced = true;
          break;
        }
      }
    }

    if (exprent instanceof AssignmentExprent) {
      AssignmentExprent as = (AssignmentExprent)exprent;

      if (as.getRight() instanceof FunctionExprent) {
        FunctionExprent func = (FunctionExprent)as.getRight();

        VarType midlayer = func.getFuncType().castType;
        if (midlayer != null) {
          if (func.getLstOperands().get(0) instanceof FunctionExprent) {
            func = (FunctionExprent)func.getLstOperands().get(0);
          }
          else {
            return null;
          }
        }

        if (func.getFuncType() == FunctionExprent.FunctionType.ADD ||
            func.getFuncType() == FunctionExprent.FunctionType.SUB) {
          Exprent econd = func.getLstOperands().get(0);
          Exprent econst = func.getLstOperands().get(1);

          if (!(econst instanceof ConstExprent) && econd instanceof ConstExprent &&
              func.getFuncType() == FunctionExprent.FunctionType.ADD) {
            econd = econst;
            econst = func.getLstOperands().get(0);
          }

          if (econst instanceof ConstExprent && ((ConstExprent)econst).hasValueOne()) {
            Exprent left = as.getLeft();

            VarType condtype = left.getExprType();
            if (exprsEqual(left, econd) && (midlayer == null || midlayer.equals(condtype))) {
              FunctionExprent ret = new FunctionExprent(
                func.getFuncType() == FunctionExprent.FunctionType.ADD ? FunctionExprent.FunctionType.PPI : FunctionExprent.FunctionType.MMI,
                econd, func.bytecode);
              ret.setImplicitType(condtype);

              exprentReplaced = true;

              if (!left.equals(econd)) {
                updateVersions(this.dgraph, new VarVersionPair((VarExprent)left), new VarVersionPair((VarExprent)econd));
              }

              return ret;
            }
          }
        }
      }
    }

    return null;
  }

  private boolean exprsEqual(Exprent e1, Exprent e2) {
    if (e1 == e2) return true;
    if (e1 == null || e2 == null) return false;
    if (e1 instanceof VarExprent) {
      return varsEqual(e1, e2);
    }
    return e1.equals(e2);
  }

  private boolean varsEqual(Exprent e1, Exprent e2) {
    if (!(e1 instanceof VarExprent)) return false;
    if (!(e2 instanceof VarExprent)) return false;

    VarExprent v1 = (VarExprent)e1;
    VarExprent v2 = (VarExprent)e2;
    return varProc.getVarOriginalIndex(v1.getIndex()) == varProc.getVarOriginalIndex(v2.getIndex());
    // TODO: Verify the types are in the same 'family' {byte->short->int}
    //        && InterpreterUtil.equalObjects(v1.getVarType(), v2.getVarType());
  }


  private void updateVersions(DirectGraph graph, final VarVersionPair oldVVP, final VarVersionPair newVVP) {
    graph.iterateExprents(new DirectGraph.ExprentIterator() {
      @Override
      public int processExprent(Exprent exprent) {
        List<Exprent> lst = exprent.getAllExprents(true);
        lst.add(exprent);

        for (Exprent expr : lst) {
          if (expr instanceof VarExprent) {
            VarExprent var = (VarExprent)expr;
            if (var.getIndex() == oldVVP.var && var.getVersion() == oldVVP.version) {
              var.setIndex(newVVP.var);
              var.setVersion(newVVP.version);
            }
          }
        }

        return 0;
      }
    });
  }

  //
  // ++a
  // (a > 0) {
  //   ...
  // }
  //
  // becomes
  //
  // if (++a > 0) {
  //   ...
  // }
  //
  // Semantically the same, but cleaner and allows for loop inlining. Keep this
  // limited to loop-header guards; folding ordinary body ifs tends to move
  // source-like increments into conditions for no structural benefit.
  public static boolean inlinePPIandMMIIfForLoopHeaders(RootStatement stat) {
    boolean res = inlinePPIandMMIIfForLoopHeadersRec(stat);

    if (res) {
      SequenceHelper.condenseSequences(stat);
    }

    return res;
  }

  private static boolean inlinePPIandMMIIfForLoopHeadersRec(Statement stat) {
    boolean res = false;
    for (Statement st : stat.getStats()) {
      res |= inlinePPIandMMIIfForLoopHeadersRec(st);
    }

    if (stat.getExprents() != null && !stat.getExprents().isEmpty()) {
      IfStatement destination = findIfSuccessor(stat);

      if (destination != null &&
          isLoopHeaderCandidate(destination) &&
          stat.getExprents().get(stat.getExprents().size() - 1) instanceof FunctionExprent func &&
          (func.getFuncType() == FunctionExprent.FunctionType.PPI || func.getFuncType() == FunctionExprent.FunctionType.MMI) &&
          func.getLstOperands().get(0) instanceof VarExprent inner) {
        Exprent ifExpr = destination.getHeadexprent().getCondition();

        while (ifExpr instanceof FunctionExprent ifFunc && ifFunc.getFuncType() == FunctionExprent.FunctionType.BOOL_NOT) {
          ifExpr = ifFunc.getLstOperands().get(0);
        }

        Pair<Exprent, VarExprent> usage = SimplifyExprentsHelper.findFirstSafeUsage(inner, ifExpr, func);
        if (usage != null) {
          usage.a.replaceExprent(usage.b, func);
          func.addBytecodeOffsets(usage.b.bytecode);
          stat.getExprents().remove(stat.getExprents().size() - 1);
          res = true;

          destination.setHasPPMM(true);
        }
      }
    }

    return res;
  }

  private static boolean isLoopHeaderCandidate(IfStatement destination) {
    Statement child = destination;
    Statement parent = child.getParent();

    while (parent != null) {
      if (parent instanceof DoStatement loop) {
        return loop.getLooptype() == DoStatement.Type.INFINITE && loop.getFirst() == child;
      }

      if (parent.getFirst() != child) {
        return false;
      }

      child = parent;
      parent = child.getParent();
    }

    return false;
  }

  private static IfStatement findIfSuccessor(Statement stat) {
    if (stat.getParent() instanceof IfStatement) {
      if (stat.getParent().getFirst() == stat) {
        return (IfStatement) stat.getParent();
      }
    }

    return null;
  }
}
