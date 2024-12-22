package transformer.utils;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sootup.core.jimple.common.stmt.JReturnVoidStmt;

public class ReturnVoidStatementConverter {
	static Logger logger = LoggerFactory.getLogger(ReturnStatementConverter.class);
    private final MethodVisitor mv;

    public ReturnVoidStatementConverter(MethodVisitor mv) {
        this.mv = mv;
    }

    public void convert(JReturnVoidStmt stmt) {
        mv.visitInsn(Opcodes.RETURN);
    }
}
