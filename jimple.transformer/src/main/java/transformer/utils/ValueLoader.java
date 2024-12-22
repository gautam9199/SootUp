package transformer.utils;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.FloatConstant;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.constant.StringConstant;

public class ValueLoader {
	static Logger logger = LoggerFactory.getLogger(ValueLoader.class);

    public static void loadValue(MethodVisitor mv, Value value) {
        if (value instanceof Local) {
            mv.visitVarInsn(Opcodes.ILOAD, getLocalIndex((Local) value));
        } else if (value instanceof IntConstant) {
            mv.visitIntInsn(Opcodes.BIPUSH, ((IntConstant) value).getValue());
        } else if (value instanceof FloatConstant) {
            mv.visitLdcInsn(((FloatConstant) value).getValue());
        } else if (value instanceof StringConstant) {
            mv.visitLdcInsn(((StringConstant) value).getValue());
        } else {
            throw new UnsupportedOperationException("Unsupported value type: " + value.getClass().getName());
        }
    }

    private static int getLocalIndex(Local local) {
        // Implement logic to determine local variable index
        return 0;
    }
}
