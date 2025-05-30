/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.sparql.engine.main ;

import java.util.Iterator;
import java.util.Set ;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.atlas.lib.SetUtils ;
import org.apache.jena.sparql.algebra.Op ;
import org.apache.jena.sparql.algebra.OpVisitor ;
import org.apache.jena.sparql.algebra.OpVisitorBase ;
import org.apache.jena.sparql.algebra.OpWalker ;
import org.apache.jena.sparql.algebra.op.* ;
import org.apache.jena.sparql.core.Var ;

public class JoinClassifier
{
    static /*final*/ public  boolean print = false ;

    static public boolean isLinear(OpJoin join) {
        return isLinear(join.getLeft(), join.getRight()) ;
    }

    static public boolean isLinear(Op _left, Op _right) {
        // Modifers that we can push substitution through whether left or right:
        //   OpDistinct, OpReduced, OpList, OpProject
        // Modifiers that we don't touch
        //   OpSlice, OpTopN, OpOrder (which gets lost - could remove it!)
        // (These could be first and top - i.e. in call once position, and be safe)

        Op left = effectiveOp(_left) ;
        Op right = effectiveOp(_right) ;

        if ( ! isSafeForLinear(left) || ! isSafeForLinear(right) )
            return false ;

        // Modifiers.
        if ( right instanceof OpExtend )    return false ;
        if ( right instanceof OpAssign )    return false ;
        if ( right instanceof OpGroup )     return false ;
//        if ( right instanceof OpSemiJoin )      return false ;
//        if ( right instanceof OpAntiJoin )      return false ;
//        if ( right instanceof OpMinus )     return false ;

        if ( right instanceof OpSlice )     return false ;
        if ( right instanceof OpTopN )      return false ;
        if ( right instanceof OpOrder )     return false ;

        // Lateral is different.
        if ( right instanceof OpLateral )   return false ;

        // Do not linearize when effectively joining tables - i.e. force hash joins between tables.
        Basis leftBasis = getBasis(left);
        Basis rightBasis = getBasis(right);
        if ( leftBasis == Basis.TABLE && rightBasis == Basis.TABLE )
            return false ;

        // Assume something will not commute these later on.
        return check(left, right) ;
    }

    // -- pre check for ops we can't handle in a linear fashion.
    // These are the negation patterns (minus and diff)
    // FILTER NOT EXISTS is safe - it's defined by iteration like the linear execution algorithm.
    private static class UnsafeLinearOpException extends RuntimeException {
        @Override public Throwable fillInStackTrace() { return this; }
    }
    private static OpVisitor checkForUnsafeVisitor = new OpVisitorBase() {
        @Override public void visit(OpMinus opMinus) { throw new UnsafeLinearOpException(); }
        @Override public void visit(OpSemiJoin opSemiJoin)   { throw new UnsafeLinearOpException(); }
        @Override public void visit(OpAntiJoin opAntiJoin)   { throw new UnsafeLinearOpException(); }
    };

    private static boolean isSafeForLinear(Op op) {
        try { OpWalker.walk(op, checkForUnsafeVisitor); return true; }
        catch (UnsafeLinearOpException e) { return false; }
    }
    // --

    // Check left can stream into right
    static private boolean check(Op leftOp, Op rightOp) {
        if ( print ) {
            System.err.println("== JoinClassifier");
            System.err.println("Left::");
            System.err.println(leftOp) ;
            System.err.println("Right::");
            System.err.println(rightOp) ;
        }

        // Need only check left/right.
        VarFinder vfLeft = VarFinder.process(leftOp) ;
        Set<Var> vLeftFixed = vfLeft.getFixed() ;
        Set<Var> vLeftOpt = vfLeft.getOpt() ;
        // Set<Var> vLeftFilter = vfLeft.getFilter() ;
        if ( print ) {
            System.err.println("Left") ;
            vfLeft.print(System.err) ;
        }
        VarFinder vfRight           = VarFinder.process(rightOp) ;
        if ( print ) {
            System.err.println("Right") ;
            vfRight.print(System.err) ;
        }

        Set<Var> vRightFixed        = vfRight.getFixed() ;
        Set<Var> vRightOpt          = vfRight.getOpt() ;
        Set<Var> vRightFilter       = vfRight.getFilter() ;
        Set<Var> vRightFilterOnly   = vfRight.getFilterOnly() ;
        Set<Var> vRightAssign       = vfRight.getAssign() ;

        // Step 1 : If there are any variables in the LHS that are filter-only or filter-before define,
        // we can't do anything.
        if ( ! vRightFilterOnly.isEmpty() ) {
            if ( SetUtils.intersectionP(vLeftFixed, vRightFilterOnly) ||
                 SetUtils.intersectionP(vLeftOpt, vRightFilterOnly) ) {
                if ( print )
                    System.err.println("vRightFilterOnly has variables used in the left");
                return false;
            }
            // JENA-1534 fixes this.
//            // The above is the tigher condition to see of any of the getFilterOnly are
//            // possible from the left. If not, then we can still use a sequence.
//            // An outer sequence should not push arbitrary variables here but ... play
//            // safe on the argument this is a relative uncommon case.
//            if ( print )
//                System.err.println("vRightFilterOnly.not isEmpty");
//            return false;
        }

        // Step 2 : remove any variable definitely fixed from the floating sets
        // because the nature of the "join" will deal with that.
        vLeftOpt = SetUtils.difference(vLeftOpt, vLeftFixed) ;
        vRightOpt = SetUtils.difference(vRightOpt, vRightFixed) ;

        // And also assign/filter variables in the RHS which are always defined
        // in the RHS.
        // Leaves any potentially free variables in RHS filter.
        vRightFilter = SetUtils.difference(vRightFilter, vRightFixed) ;
        vRightAssign = SetUtils.difference(vRightAssign, vRightFixed) ;

        if ( print )
            System.err.println() ;
        if ( print )
            System.err.println("Left/opt:      " + vLeftOpt) ;
        if ( print )
            System.err.println("Right/opt:     " + vRightOpt) ;
        if ( print )
            System.err.println("Right/filter:  " + vRightFilter) ;
        if ( print )
            System.err.println("Right/assign:  " + vRightAssign) ;

        // Step 2 : check whether any variables in the right are optional or
        // filter vars which are also optional in the left side.

        // Two cases to consider::
        // Case 1 : a variable in the RHS is optional
        // (this is a join we are classifying).
        // Check no variables are optional on right if bound on the left (fixed
        // or optional)
        // Check no variables are optional on the left side, and optional on the
        // right.
        boolean r11 = SetUtils.intersectionP(vRightOpt, vLeftFixed) ;

        boolean r12 = SetUtils.intersectionP(vRightOpt, vLeftOpt) ;

        // What about rightfixed, left opt?

        boolean bad1 = r11 || r12 ;

        if ( print )
            System.err.println("J: Case 1 (false=ok) = " + bad1) ;
        if ( bad1 )
            return false;

        // Case 2 : a filter in the RHS is uses a variable from the LHS (whether
        // fixed or optional)
        // Scoping means we must hide the LHS value form the RHS
        // Could mask (??). For now, we stop linearization of this join.
        // (we removed fixed variables of the right side, so right/filter is
        // unfixed vars)

        boolean bad2 = SetUtils.intersectionP(vRightFilter, vLeftFixed) ;
        if ( print )
            System.err.println("J: Case 2 (false=ok) = " + bad2) ;
        if ( bad2 )
            return false;

        // Case 3 : an assign in the RHS uses a variable not introduced
        // Scoping means we must hide the LHS value from the RHS

        // Think this may be slightly relaxed, using variables in an
        // assign on the RHS is in principal fine if they're also available on
        // the RHS
        // vRightAssign.removeAll(vRightFixed);
        // boolean bad3 = vRightAssign.size() > 0;

        boolean bad3 = SetUtils.intersectionP(vRightAssign, vLeftFixed) ;
        if ( print )
            System.err.println("J: Case 3 (false=ok) = " + bad3) ;
        if ( bad3 )
            return false;
        if ( print )
            System.err.println("J: Result: OK");
        return true;
    }

    /** Find the "effective op" - i.e. the one that may be sensitive to linearization */
    private static Op effectiveOp(Op op) {
        for (;;) {
            if ( op instanceof OpExt )
                op = ((OpExt)op).effectiveOp() ;
            else if (safeModifier(op))
                op = ((OpModifier)op).getSubOp() ;
            // JENA-1813
            else if (op instanceof OpGraph )
                op = ((OpGraph)op).getSubOp() ;
            // JENA-2332
            else if (op instanceof OpService )
                op = ((OpService)op).getSubOp() ;
            else
                return op;
        }
    }

    /** Helper - test for "safe" modifiers */
    private static boolean safeModifier(Op op) {
        if ( !(op instanceof OpModifier) )
            return false ;
        return op instanceof OpDistinct || op instanceof OpReduced || op instanceof OpProject || op instanceof OpList ;
    }

    /** Enumeration of the primitive op types in the SPARQL algebra. */
    private enum Basis {
        PATTERN,
        TABLE,
        PFUNCTION
    }

    /**
     * This method checks whether the given op is based on {@link OpTable} or {@link OpPropFunc}.
     * If neither is the case then the result is {@link Basis#PATTERN}.
     * <p>
     * This method is called for each side of a join from {@link #isLinear(Op, Op)}.
     * If that side of the join is a property function then {@link Basis#PFUNCTION} is returned.
     *
     * <p>
     * The special handling of property functions is due to that
     * OpJoin(TABLE, PFUNCTION) needs to be linearized to OpSequence(TABLE, PFUNCTION).
     *
     * <p>
     * This method resolves OpExt to its effective op.
     */
    private static Basis getBasis(Op op) {
        Basis result;
        if (op instanceof OpTable) {
            result = Basis.TABLE;
        } else if (op instanceof OpPropFunc) {
            result = Basis.PFUNCTION;
        } else if (op instanceof OpExt) {
            Op effectiveOp = ((OpExt)op).effectiveOp();
            result = effectiveOp == null
                    ? Basis.PATTERN // Assume pattern
                    : getBasis(effectiveOp);
        } else if (op instanceof Op1) {
            result = getBasis(((Op1)op).getSubOp());
        } else {
            Iterator<Op> it = getSubOps(op);
            if (!it.hasNext()) { // Op0 that is not a table (e.g. OpPath, OpQuad, OpTriple, ...)
                result = Basis.PATTERN;
            } else { // Op2, OpN
                result = Basis.TABLE; // Start with table; if any argument evaluates to pattern then change to pattern.
                while (it.hasNext()) {
                    Op subOp = it.next();
                    Basis contrib = getBasis(subOp);

                    // Treat property functions in sub operations with more than one argument as tables
                    if (contrib == Basis.PFUNCTION) {
                        contrib = Basis.TABLE;
                    }

                    if (contrib != Basis.TABLE) {
                        result = Basis.PATTERN;
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Return an iterator over the given op's immediate sub ops.
     * An empty iterator is returned for Op0 and OpExt.
     */
    private static Iterator<Op> getSubOps(Op op) {
        if (op instanceof Op1)
            return Iter.singletonIterator(((Op1)op).getSubOp());

        if (op instanceof Op2) {
            Op2 x = (Op2)op;
            return Iter.of(x.getLeft(), x.getRight());
        }

        if (op instanceof OpN) {
            return ((OpN)op).iterator();
        }

        // Op0 and OpExt are treated as having no sub ops
        return Iter.empty();
    }
}
