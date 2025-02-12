package transformer.utils;

import static org.objectweb.asm.Opcodes.ALOAD;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.ClassConstant;
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

  public void convertIfStmt(
          JIfStmt stmt,
          MethodVisitor mv,
          LocalIndexMapper indexMapper,
          Label trueLabel,
          Label falseLabel) {
    AbstractConditionExpr condition = stmt.getCondition();
    generateConditionBytecode(condition, mv, indexMapper, trueLabel);
  }

  private void generateConditionBytecode(
          AbstractConditionExpr condition,
          MethodVisitor mv,
          LocalIndexMapper indexMapper,
          Label trueLabel) {
    Value op1 = condition.getOp1();
    Value op2 = condition.getOp2();

    // Load operands onto the stack
    loadOperand(op1, mv, indexMapper);
    loadOperand(op2, mv, indexMapper);

    // Determine the appropriate opcode for the condition
    if (condition instanceof JEqExpr) {
      if (isReferenceComparison(op1, op2)) {

          mv.visitJumpInsn(Opcodes.IF_ACMPEQ, trueLabel); // Reference equality


      } else {
        if(op2.equals(NullConstant.getInstance())){
          mv.visitJumpInsn(Opcodes.IF_ACMPEQ,trueLabel);
        }else{
          mv.visitJumpInsn(Opcodes.IF_ICMPEQ, trueLabel); // Integer equality
        }

      }
    } else if (condition instanceof JNeExpr) {
      if (isReferenceComparison(op1, op2)) {
        mv.visitJumpInsn(Opcodes.IF_ACMPNE, trueLabel); // Reference inequality
      } else {
        mv.visitJumpInsn(Opcodes.IF_ICMPNE, trueLabel); // Integer inequality
      }
    } else if (condition instanceof JLtExpr) {
      mv.visitJumpInsn(Opcodes.IF_ICMPLT, trueLabel); // Less than
    } else if (condition instanceof JLeExpr) {
      mv.visitJumpInsn(Opcodes.IF_ICMPLE, trueLabel); // Less than or equal
    } else if (condition instanceof JGtExpr) {
      mv.visitJumpInsn(Opcodes.IF_ICMPGT, trueLabel); // Greater than
    } else if (condition instanceof JGeExpr) {
      mv.visitJumpInsn(Opcodes.IF_ICMPGE, trueLabel); // Greater than or equal
    } else {
      throw new UnsupportedOperationException(
              "Unsupported condition type: " + condition.getClass().getName());
    }
  }

  private void loadOperand(Value operand, MethodVisitor mv, LocalIndexMapper indexMapper) {
    if (operand instanceof Local) {
      int index = indexMapper.getLocalIndex((Immediate) operand);
      mv.visitVarInsn(getLoadOpcode(operand.getType()), index);
    } else if (operand instanceof IntConstant) {
      int value = ((IntConstant) operand).getValue();
      if (value >= -1 && value <= 5) {
        mv.visitInsn(Opcodes.ICONST_0 + value); // Load small integer constants
      } else {
        mv.visitLdcInsn(value); // Load larger integer constants
      }
    } else if (operand instanceof StringConstant) {
      mv.visitLdcInsn(((StringConstant) operand).getValue());
    } else if (operand instanceof NullConstant) {
      mv.visitInsn(Opcodes.ACONST_NULL); // Push null onto the stack
    } else if (operand instanceof ClassConstant) {
      mv.visitLdcInsn(((ClassConstant) operand).getValue()); // Push class constant onto the stack
    } else {
      throw new UnsupportedOperationException(
              "Unsupported operand type: " + operand.getClass().getName());
    }
  }

  // Helper method to check if the comparison involves references
  private boolean isReferenceComparison(Value op1, Value op2) {
    return op1.getType().toString().equals("java.lang.Object") || op2.getType().toString().equals("java.lang.Object");
  }

}
