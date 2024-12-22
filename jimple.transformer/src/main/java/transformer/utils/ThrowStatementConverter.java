package transformer.utils;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.common.stmt.JThrowStmt;

public class ThrowStatementConverter {

	static Logger logger = LoggerFactory.getLogger(ThrowStatementConverter.class);
    public void convertThrowStmt(JThrowStmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper) {
        // Get the Immediate (which represents the exception to be thrown)
        Immediate exception = stmt.getOp();

        // Convert the Immediate (which could be a local variable or a constant) to the appropriate ASM instruction
        int index = indexMapper.getLocalIndex(exception);

        // Load the exception onto the stack
        if(!exception.toString().contains("#")) {
        	 mv.visitVarInsn(Opcodes.ALOAD, index);
        }


        // Emit the bytecode instruction to throw the exception
        mv.visitInsn(Opcodes.ATHROW);
    }
}
