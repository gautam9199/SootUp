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

package de.upb.soot.jimple.common.stmt;

import de.upb.soot.jimple.Jimple;
import de.upb.soot.jimple.basic.JimpleComparator;
import de.upb.soot.jimple.basic.PositionInfo;
import de.upb.soot.jimple.basic.Value;
import de.upb.soot.jimple.basic.ValueBox;
import de.upb.soot.jimple.visitor.IStmtVisitor;
import de.upb.soot.jimple.visitor.IVisitor;
import de.upb.soot.util.printer.IStmtPrinter;

public class JReturnStmt extends AbstractOpStmt {
  /** */
  private static final long serialVersionUID = 4601025616184085996L;

  public JReturnStmt(Value returnValue, PositionInfo positionInfo) {
    this(Jimple.newImmediateBox(returnValue), positionInfo);
  }

  protected JReturnStmt(ValueBox returnValueBox, PositionInfo positionInfo) {
    super(returnValueBox, positionInfo);
  }

  @Override
  public JReturnStmt clone() {
    return new JReturnStmt(Jimple.cloneIfNecessary(getOp()), getPositionInfo().clone());
  }

  @Override
  public String toString() {
    return Jimple.RETURN + " " + opBox.getValue().toString();
  }

  @Override
  public void toString(IStmtPrinter up) {
    up.literal(Jimple.RETURN);
    up.literal(" ");
    opBox.toString(up);
  }

  @Override
  public void accept(IVisitor sw) {
    ((IStmtVisitor) sw).caseReturnStmt(this);
  }

  @Override
  public boolean fallsThrough() {
    return false;
  }

  @Override
  public boolean branches() {
    return false;
  }

  @Override
  public boolean equivTo(Object o, JimpleComparator comparator) {
    return comparator.caseReturnStmt(this, o);
  }

  @Override
  public int equivHashCode() {
    return super.equivHashCode();
  }
}