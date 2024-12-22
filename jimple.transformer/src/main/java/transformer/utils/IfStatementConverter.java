package transformer.utils;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.constant.NullConstant;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.expr.AbstractConditionExpr;
import sootup.core.jimple.common.expr.JEqExpr;
import sootup.core.jimple.common.expr.JGeExpr;
import sootup.core.jimple.common.expr.JGtExpr;
import sootup.core.jimple.common.expr.JLeExpr;
import sootup.core.jimple.common.expr.JLtExpr;
import sootup.core.jimple.common.expr.JNeExpr;
import sootup.core.jimple.common.stmt.JIfStmt;

public class IfStatementConverter extends ConversionUtil {

    static Logger logger = LoggerFactory.getLogger(IfStatementConverter.class);

    public void convertIfStmt(JIfStmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper, Label trueLabel, Label falseLabel) {
        AbstractConditionExpr condition = stmt.getCondition();

        generateConditionBytecode(condition, mv, indexMapper, trueLabel);


    }

    private void generateConditionBytecode(AbstractConditionExpr condition, MethodVisitor mv, LocalIndexMapper indexMapper, Label trueLabel) {
        Value op1 = condition.getOp1();
        Value op2 = condition.getOp2();

        // Load operands onto the stack
        loadOperand(op1, mv, indexMapper);
        loadOperand(op2, mv, indexMapper);

        // Generate appropriate bytecode for the condition type
        if (condition instanceof JEqExpr) {
            mv.visitJumpInsn(Opcodes.IFEQ, trueLabel);
        } else if (condition instanceof JNeExpr) {
            mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
        } else if (condition instanceof JLtExpr) {
            if(condition.toString().contains("=")){
                mv.visitJumpInsn(Opcodes.IF_ICMPLT,trueLabel);
            }else{
                mv.visitJumpInsn(Opcodes.IFLT, trueLabel);
            }

        } else if (condition instanceof JLeExpr) {
            if(condition.toString().contains("=")){
                mv.visitJumpInsn(Opcodes.IF_ICMPLE,trueLabel);
            }else{
                mv.visitJumpInsn(Opcodes.IFLE, trueLabel);
            }

        } else if (condition instanceof JGtExpr) {
            if(condition.toString().contains("=")){
                mv.visitJumpInsn(Opcodes.IF_ICMPGT,trueLabel);
            }else{
                mv.visitJumpInsn(Opcodes.IFGT, trueLabel);
            }

        } else if (condition instanceof JGeExpr) {
            if(condition.toString().contains("=")){
                mv.visitJumpInsn(Opcodes.IF_ICMPGE, trueLabel);
            }else{
                mv.visitJumpInsn(Opcodes.IFGE, trueLabel);
            }

        }
        else {
                throw new UnsupportedOperationException("Unsupported condition type: " + condition.getClass().getName());
            }

    }

    private void loadOperand(Value operand, MethodVisitor mv, LocalIndexMapper indexMapper) {
        // Handle loading immediate constants or locals onto the stack
        if (operand instanceof Local) {
            int index = indexMapper.getLocalIndex((Immediate) operand);
            mv.visitVarInsn(getLoadOpcode(operand.getType()), index);
        } else if (operand instanceof IntConstant) {
            int value = ((IntConstant) operand).getValue();
            if (value >= -1 && value <= 5) {
                mv.visitInsn(Opcodes.ICONST_0 + value);  // Load small constants
            } else {
                mv.visitLdcInsn(value);  // Load larger constants with LDC
            }
        }else if (operand instanceof StringConstant) {
            // Load a string constant onto the stack (e.g., "HelloWorld")
            mv.visitLdcInsn(((StringConstant) operand).getValue());
        } else if (operand instanceof NullConstant) {
        	mv.visitInsn(Opcodes.ACONST_NULL);
        }
        else {
            throw new UnsupportedOperationException("Unsupported operand type: " + operand.getClass().getName());
        }
    }

    private boolean isCheckForEmpty(AbstractConditionExpr condition, MethodVisitor mv, LocalIndexMapper indexMapper, Label trueLabel) {
        if (condition instanceof JEqExpr && condition.getOp1().toString().endsWith("length")) {
            mv.visitJumpInsn(Opcodes.IFEQ, trueLabel);  // Assuming check for "is empty"
            return true;
        }
        return false;
    }
}
