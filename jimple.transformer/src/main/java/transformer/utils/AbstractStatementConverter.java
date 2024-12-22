package transformer.utils;


import org.objectweb.asm.MethodVisitor;

import sootup.core.jimple.common.stmt.JThrowStmt;
import sootup.core.jimple.common.stmt.Stmt;

public abstract class AbstractStatementConverter {
    protected final MethodVisitor mv;

    public AbstractStatementConverter(MethodVisitor mv) {
        this.mv = mv;
    }

    public abstract void convert(Stmt stmt);

	public void convert(JThrowStmt stmt) {
		// TODO Auto-generated method stub

	}
}
