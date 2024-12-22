package transformer.utils;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.stmt.JReturnStmt;
import sootup.core.jimple.common.stmt.JReturnVoidStmt;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;

public class ReturnStatementConverter extends ConversionUtil {

	static Logger logger = LoggerFactory.getLogger(ReturnStatementConverter.class);

    public void convertReturnStmt(JReturnStmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper) {
        Immediate returnValue = stmt.getOp();
        Type returnType = returnValue.getType();

        // Load the return value onto the stack

        if(returnValue instanceof StringConstant){
            mv.visitLdcInsn(((StringConstant) returnValue).getValue());
            mv.visitInsn(Opcodes.ARETURN);
        }
        else{
            mv.visitVarInsn(getLoadOpcode(returnType), indexMapper.getLocalIndex(returnValue));

            // Return the value
            mv.visitInsn(getReturnOpcode(returnType));
        }

    }

    public void convertReturnVoidStmt(JReturnVoidStmt stmt, MethodVisitor mv) {
        // Return void
    	//mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
        mv.visitInsn(Opcodes.RETURN);
    }

    private int getReturnOpcode(Type returnType) {
        if (Type.isIntLikeType(returnType)) {
            return Opcodes.IRETURN;
        } else if (returnType == PrimitiveType.FloatType.getInstance()) {
            return Opcodes.FRETURN;
        } else if (returnType == PrimitiveType.LongType.getInstance()) {
            return Opcodes.LRETURN;
        } else if (returnType == PrimitiveType.DoubleType.getInstance()) {
            return Opcodes.DRETURN;
        } else if (Type.isObject(returnType) || returnType instanceof sootup.core.types.ClassType || returnType instanceof sootup.core.types.ArrayType) {
            return Opcodes.ARETURN;
        } else {
        	System.out.println("Unsupported type for return: " + returnType);
        	return Opcodes.ARETURN;

        }
    }
}
