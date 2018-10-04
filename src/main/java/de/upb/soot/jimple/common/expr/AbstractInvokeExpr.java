/* Soot - a J*va Optimization Framework
 * Copyright (C) 1999 Patrick Lam
 * Copyright (C) 2004 Ondrej Lhotak
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

package de.upb.soot.jimple.common.expr;

import de.upb.soot.core.AbstractViewResident;
import de.upb.soot.core.SootMethod;
import de.upb.soot.jimple.basic.Value;
import de.upb.soot.jimple.basic.ValueBox;
import de.upb.soot.jimple.common.type.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("serial")
public abstract class AbstractInvokeExpr extends AbstractViewResident implements Expr {
  protected SootMethod method;
  protected final ValueBox[] argBoxes;

  protected AbstractInvokeExpr(SootMethod method, ValueBox[] argBoxes) {
    this.method = method;
    this.argBoxes = argBoxes.length == 0 ? null : argBoxes;
  }

  public void setMethodRef(SootMethod method) {
    this.method = method;
  }

  public SootMethod getMethod() {
    return method.resolve();
  }

  @Override
  public abstract Object clone();

  public Value getArg(int index) {
    return argBoxes[index].getValue();
  }

  /**
   * Returns a list of arguments, consisting of values contained in the box.
   */
  public List<Value> getArgs() {
    List<Value> l = new ArrayList<>();
    if (argBoxes != null) {
      for (ValueBox element : argBoxes) {
        l.add(element.getValue());
      }
    }
    return l;
  }

  public int getArgCount() {
    return argBoxes == null ? 0 : argBoxes.length;
  }

  public void setArg(int index, Value arg) {
    argBoxes[index].setValue(arg);
  }

  public ValueBox getArgBox(int index) {
    return argBoxes[index];
  }

  @Override
  public Type getType() {
    return method.returnType();
  }

  @Override
  public List<ValueBox> getUseBoxes() {
    if (argBoxes == null) {
      return Collections.emptyList();
    }

    List<ValueBox> list = new ArrayList<ValueBox>();
    Collections.addAll(list, argBoxes);

    for (ValueBox element : argBoxes) {
      list.addAll(element.getValue().getUseBoxes());
    }

    return list;
  }

}
