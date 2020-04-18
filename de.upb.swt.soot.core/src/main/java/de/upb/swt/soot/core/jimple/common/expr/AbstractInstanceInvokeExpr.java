/* Soot - a J*va Optimization Framework
 * Copyright (C) 1999 Patrick Lam
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/*
 * Modified by the Sable Research Group and others 1997-1999.
 * See the 'credits' file distributed with Soot for the complete list of
 * contributors.  (Soot is distributed at http://www.sable.mcgill.ca/soot)
 */

package de.upb.swt.soot.core.jimple.common.expr;

import de.upb.swt.soot.core.jimple.basic.Immediate;
import de.upb.swt.soot.core.jimple.basic.Local;
import de.upb.swt.soot.core.jimple.basic.Value;
import de.upb.swt.soot.core.jimple.visitor.ExprVisitor;
import de.upb.swt.soot.core.jimple.visitor.Visitor;
import de.upb.swt.soot.core.signatures.MethodSignature;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public abstract class AbstractInstanceInvokeExpr extends AbstractInvokeExpr {

  private final Value base;

  AbstractInstanceInvokeExpr(@Nonnull Local base, MethodSignature methodSig, List<Immediate> args) {
    super(methodSig, args);
    this.base = base;
  }

  public Value getBase() {
    return base;
  }

  @Override
  public java.util.List<Value> getUses() {
    List<Value> list = new ArrayList<>();

    List<? extends Value> args = getArgs();
    if (args != null) {
      list.addAll(args);
      for (Value arg : args) {
        list.addAll(arg.getUses());
      }
    }
    list.addAll(base.getUses());
    list.add(base);
    return list;
  }

  @Override
  public void accept(Visitor sw) {
    ((ExprVisitor) sw).caseInstanceInvokeExpr(this);
  }

  /** Returns a hash code for this object, consistent with structural equality. */
  @Override
  public int equivHashCode() {
    return base.equivHashCode() * 101 + getMethodSignature().hashCode() * 17;
  }
}
