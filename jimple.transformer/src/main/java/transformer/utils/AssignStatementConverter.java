package transformer.utils;

import static org.objectweb.asm.Opcodes.*;

import java.util.List;
import java.util.Objects;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.*;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JFieldRef;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;
import sootup.core.types.VoidType;
import sootup.java.core.AnnotationUsage;
import sootup.java.core.jimple.basic.JavaLocal;

public class AssignStatementConverter extends ConversionUtil {
  static Logger logger = LoggerFactory.getLogger(AssignStatementConverter.class);

  // This conversion creates useless ASTORE ALOAD insn
  public void convertAssignStmt(JAssignStmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper) {
    LValue leftOp = stmt.getLeftOp();
    Value rightOp = stmt.getRightOp();

    // Skip redundant storage for temporary variables or when leftOp and rightOp are the same
    if (leftOp instanceof Local && rightOp instanceof Local && !indexMapper.isStackVariable((Local) rightOp) ) {
      indexMapper.updateExceptionReference((Local) rightOp,indexMapper.getLocalIndex((Immediate) leftOp));
      return;
    }

    if (leftOp instanceof Local) {

        convertLocalAssignmentStmt((Local) leftOp, rightOp, mv, indexMapper);
    } else if (leftOp instanceof JFieldRef) {
      convertFieldAssignmentStmt((JFieldRef) leftOp, rightOp, mv, indexMapper);
    } else if (leftOp instanceof JArrayRef) {
      convertArrayAssignmentStmt((JArrayRef) leftOp, rightOp, mv, indexMapper);
    } else {
      throw new UnsupportedOperationException("Unsupported JAssignStmt operation.");
    }
  }

  private void convertLocalAssignmentStmt(
      Local leftOp, Value rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    int leftOpIndex = indexMapper.getLocalIndex(leftOp);

    // Skip redundant assignments where the leftOp and rightOp are the same
    if (rightOp instanceof Local && indexMapper.getLocalIndex((Local) rightOp) == leftOpIndex) {
      return; // No need to re-assign the same value to the same local variable
    }

    if (rightOp instanceof Immediate) {
      if (rightOp instanceof Local && indexMapper.getLocalIndex((Local) rightOp) == leftOpIndex) {
        return; // Skip re-storing the same value
      }
      convertImmediateToLocalAssignmentStmt(leftOp, (Immediate) rightOp, mv, indexMapper);
    } else if (rightOp instanceof Expr) {
      convertExpressionToLocalAssignmentStmt(leftOp, (Expr) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JFieldRef) {
      convertFieldRefToLocalAssignmentStmt(leftOp, (JFieldRef) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JArrayRef) {
      convertArrayRefToLocalAssignmentStmt(leftOp, (JArrayRef) rightOp, mv, indexMapper);
    } else {
      throw new UnsupportedOperationException("Unsupported local assignment operation.");
    }
  }

  private void convertImmediateToLocalAssignmentStmt(
      Local leftOp, Immediate rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    Type type = rightOp.getType();
    if (leftOp.getName().contains("#")
        && rightOp instanceof Local
        && indexMapper.isStackVariable((Local) rightOp)) {
      indexMapper.updateExceptionReference(leftOp, indexMapper.getLocalIndex(rightOp));
    }
    int index = indexMapper.getLocalIndex(leftOp); // Get the local variable index for 'leftOp'

    if (rightOp instanceof IntConstant) {
      IntConstant constant = (IntConstant) rightOp;
      if (constant.getValue() > 0 && constant.getValue() < Integer.MAX_VALUE) {
        // Store it in the local variable
        if (getLoadConstantOpcode(constant.getValue()) == BIPUSH) {
          mv.visitIntInsn(getLoadConstantOpcode(constant.getValue()), constant.getValue());
        } else if (getLoadConstantOpcode(constant.getValue()) == LDC) {
          mv.visitLdcInsn(constant.getValue());
        } else {

          mv.visitInsn(getLoadConstantOpcode(constant.getValue()));
        }

      } else {
        mv.visitLdcInsn(constant.getValue());
      }
      mv.visitVarInsn(getStoreOpcode(type), index);

    } else if (rightOp instanceof FloatConstant) {
      FloatConstant constant = (FloatConstant) rightOp;
      mv.visitLdcInsn(constant.getValue()); // Load the float constant onto the stack
      mv.visitVarInsn(getStoreOpcode(type), index); // Store it in the local variable

    } else if (rightOp instanceof StringConstant) {
      StringConstant constant = (StringConstant) rightOp;
      mv.visitLdcInsn(constant.getValue()); // Load the string constant onto the stack
      mv.visitVarInsn(getStoreOpcode(type), index); // Store it in the local variable

    } else if (rightOp instanceof JavaLocal) {
      convertJavaLocalToLocalAssignmentStmt(leftOp, (JavaLocal) rightOp, mv, indexMapper);

    } else if (rightOp instanceof LongConstant) {
      LongConstant constant = (LongConstant) rightOp;
      mv.visitLdcInsn(constant.getValue()); // Load the long constant onto the stack
      mv.visitVarInsn(LSTORE, index); // Store it in the local variable for long

    } else if (rightOp instanceof DoubleConstant) {
      DoubleConstant constant = (DoubleConstant) rightOp;
      mv.visitLdcInsn(constant.getValue()); // Load the double constant onto the stack
      mv.visitVarInsn(DSTORE, index); // Store it in the local variable for double

    } else if (rightOp instanceof NullConstant || rightOp.toString().equals("null")) {
      // Handle null constants
      mv.visitInsn(ACONST_NULL);
      mv.visitVarInsn(ASTORE, indexMapper.getLocalIndex(leftOp));
    } else if (rightOp instanceof ClassConstant) {
      String constantValue = ((ClassConstant) rightOp).getValue();
      mv.visitLdcInsn(constantValue);

    } else if (rightOp instanceof Local) {

      mv.visitVarInsn(getLoadOpcode(rightOp.getType()), index);

      // Store the value into the target local variable (leftOp)
      mv.visitVarInsn(getStoreOpcode(leftOp.getType()), indexMapper.getLocalIndex(leftOp));
    } else {
      throw new UnsupportedOperationException(
          "Unsupported immediate assignment operation: " + rightOp.getClass().getName());
    }
  }

  private void convertExpressionToLocalAssignmentStmt(
      Local leftOp, Expr rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    if (rightOp instanceof AbstractBinopExpr) {
      convertBinaryExpressionToLocalAssignmentStmt(
          leftOp, (AbstractBinopExpr) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JCastExpr) {
      convertCastExpressionToLocalAssignmentStmt(leftOp, (JCastExpr) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JInstanceOfExpr) {
      convertInstanceOfExpressionToLocalAssignmentStmt(
          leftOp, (JInstanceOfExpr) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JNewExpr) {
      convertNewExpressionToLocalAssignmentStmt(leftOp, (JNewExpr) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JDynamicInvokeExpr) {
      convertDynamicInvokeExprToLocalAssignmentStmt(
          leftOp, (JDynamicInvokeExpr) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JStaticInvokeExpr) {
      convertStaticInvokeExprToLocalAssignmentStmt(
          leftOp, (JStaticInvokeExpr) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JVirtualInvokeExpr) {
      String asmMethodDescriptor =
          toAsmMethodDescriptor(((AbstractInvokeExpr) rightOp).getMethodSignature());
      handleVirtualInvokeExpr(
          (JVirtualInvokeExpr) rightOp,
          mv,
          indexMapper,
          asmMethodDescriptor,
          indexMapper.isStatic(),
          indexMapper.isConstructor());
      mv.visitVarInsn(getStoreOpcode(leftOp.getType()), indexMapper.getLocalIndex(leftOp));

    } else if (rightOp instanceof JSpecialInvokeExpr) {
      String asmMethodDescriptor =
          toAsmMethodDescriptor(((AbstractInvokeExpr) rightOp).getMethodSignature());
      handleSpecialInvokeExpr(
          (JSpecialInvokeExpr) rightOp,
          mv,
          indexMapper,
          asmMethodDescriptor,
          indexMapper.isConstructor(),
          indexMapper.isStatic());
    } else if (rightOp instanceof JNewArrayExpr) {
      // Handle new array expressions
      convertNewArrayExpression((JNewArrayExpr) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JLengthExpr) {
      // Handle array length expression
      convertLengthExpression((JLengthExpr) rightOp, mv, indexMapper);
    } else if (rightOp instanceof JInterfaceInvokeExpr) {
      String asmMethodDescriptor =
          toAsmMethodDescriptor(((AbstractInvokeExpr) rightOp).getMethodSignature());
      handleInterfaceInvokeExpr(
          (JInterfaceInvokeExpr) rightOp, mv, indexMapper, asmMethodDescriptor);
    } else if (rightOp instanceof JNewMultiArrayExpr) {
      convertNewMultiArrayExpression(mv, (JNewMultiArrayExpr) rightOp, indexMapper);
    } else if (rightOp instanceof JNegExpr) {
      convertNegExpression(
          mv,
          indexMapper.getLocalIndex(((JNegExpr) rightOp).getOp()),
          ((JNegExpr) rightOp).getOp().getType());
    } else {
      throw new UnsupportedOperationException(
          "Unsupported expression assignment operation.::" + rightOp.getClass());
    }
  }

  private void convertNewMultiArrayExpression(
      MethodVisitor mv, JNewMultiArrayExpr expr, LocalIndexMapper indexMapper) {
    // Get the base type and dimensions
    List<Immediate> sizes = expr.getSizes();
    int dimensions = sizes.size();
    for (Immediate immediate : sizes) {
      if (immediate instanceof IntConstant) {
        int size = ((IntConstant) immediate).getValue();
        loadIntConstant(mv, size);
      } else if (immediate instanceof Local) {
        mv.visitVarInsn(
            getLoadOpcode(immediate.getType()), indexMapper.getLocalIndex(immediate));
      } else {
        throw new UnsupportedOperationException(
            "Unsupported dimension size type: " + immediate.getClass().getName());
      }
    }

    // Generate the multi-dimensional array creation instruction
    String baseDescriptor = toAsmTypeDescriptor(expr.getBaseType());
    mv.visitMultiANewArrayInsn(baseDescriptor, dimensions);
  }

  private void handleInterfaceInvokeExpr(
      JInterfaceInvokeExpr invokeExpr,
      MethodVisitor mv,
      LocalIndexMapper indexMapper,
      String asmMethodDescriptor) {
    // Load the instance (this reference) onto the stack
    Local base = invokeExpr.getBase();
    // mv.visitVarInsn(Opcodes.ALOAD, indexMapper.getLocalIndex(base));

    // Load method arguments onto the stack
    // loadArguments(invokeExpr.getArgs(), mv, indexMapper);

    // Invoke interface method
    MethodSignature methodSignature = invokeExpr.getMethodSignature();
    mv.visitMethodInsn(
        INVOKEINTERFACE,
        methodSignature.getDeclClassType().toString().replace('.', '/'),
        methodSignature.getName(),
        asmMethodDescriptor,
        true);
  }

  private void convertLengthExpression(
          JLengthExpr lengthExpr, MethodVisitor mv, LocalIndexMapper indexMapper) {
    Value arrayValue = lengthExpr.getOp();

      if (arrayValue instanceof Local) {
      // Load the array reference onto the stack
      mv.visitVarInsn(
              getLoadOpcode(arrayValue.getType()), indexMapper.getLocalIndex((Local) arrayValue));
      // Get the length of the array
      mv.visitInsn(ARRAYLENGTH);
    } else {
      throw new UnsupportedOperationException(
              "Unsupported array type for length operation: " + arrayValue.getClass().getName());
    }
  }

  private void convertNewArrayExpression(
          JNewArrayExpr newArrayExpr, MethodVisitor mv, LocalIndexMapper indexMapper) {
    // Validate input
    if (newArrayExpr == null) {
      throw new IllegalArgumentException("JNewArrayExpr cannot be null.");
    }

    Type elementType = newArrayExpr.getBaseType();
    Value sizeValue = newArrayExpr.getSize();

      if (elementType instanceof ArrayType) {
      ArrayType arrayType = (ArrayType) elementType;
      int dimensions = arrayType.getDimension();

      // Multi-dimensional array
      for (int i = 0; i < dimensions; i++) {
        if (sizeValue instanceof IntConstant) {
          int size = ((IntConstant) sizeValue).getValue();
          loadIntConstant(mv, size);
        } else if (sizeValue instanceof Local) {
          mv.visitVarInsn(
                  getLoadOpcode(sizeValue.getType()), indexMapper.getLocalIndex((Local) sizeValue));
        } else {
          throw new UnsupportedOperationException(
                  "Unsupported dimension size type: " + sizeValue.getClass().getName());
        }
      }

      // Generate the multi-dimensional array creation instruction
      String baseDescriptor = toAsmTypeDescriptor(arrayType.getBaseType());
      mv.visitMultiANewArrayInsn(baseDescriptor, dimensions);

    } else {
      // Single-dimensional array
      if (sizeValue instanceof IntConstant) {
        int size = ((IntConstant) sizeValue).getValue();
        loadIntConstant(mv, size);
      } else if (sizeValue instanceof Local) {
        mv.visitVarInsn(
                getLoadOpcode(sizeValue.getType()), indexMapper.getLocalIndex((Local) sizeValue));
      } else {
        throw new UnsupportedOperationException(
                "Unsupported array size type: " + sizeValue.getClass().getName());
      }

      // Create the array
      if (elementType instanceof PrimitiveType) {
        int arrayTypeOpcode = getPrimitiveArrayTypeOpcode(elementType);
        mv.visitIntInsn(NEWARRAY, arrayTypeOpcode);
        mv.visitInsn(DUP);
      } else if (elementType instanceof ClassType) {
        String className = ((ClassType) elementType).getFullyQualifiedName().replace('.', '/');
        mv.visitTypeInsn(ANEWARRAY, className);
        mv.visitInsn(DUP);
      } else {
        throw new UnsupportedOperationException("Unsupported array type: " + elementType);
      }
    }
  }


  private void handleSpecialInvokeExpr(
      JSpecialInvokeExpr invokeExpr,
      MethodVisitor mv,
      LocalIndexMapper indexMapper,
      String asmMethodDescriptor,
      boolean isConstructor,
      boolean isStatic) {
    if (!isConstructor && !indexMapper.isRecentObjectCreation()) {
      // Load 'this' for instance methods
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      indexMapper.setRecentObjectCreatiion(false);
    }

    // Load method arguments onto the stack
    loadArguments(invokeExpr.getArgs(), mv, indexMapper);

    // Call the method
    MethodSignature methodSignature = invokeExpr.getMethodSignature();
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        methodSignature.getDeclClassType().toString().replace('.', '/'),
        methodSignature.getName(),
        asmMethodDescriptor,
        false);
  }

  private void handleVirtualInvokeExpr(
      JVirtualInvokeExpr invokeExpr,
      MethodVisitor mv,
      LocalIndexMapper indexMapper,
      String asmMethodDescriptor,
      Boolean isStatic,
      Boolean isConstructor) {
    // Load the base reference (the target object)
    Local base = invokeExpr.getBase();
    int baseIndex = indexMapper.getLocalIndex(base);
    mv.visitVarInsn(Opcodes.ALOAD, baseIndex); // Load base reference for instance method

    // Load method arguments onto the stack
    loadArguments(invokeExpr.getArgs(), mv, indexMapper);

    // Invoke the virtual method
    MethodSignature methodSignature = invokeExpr.getMethodSignature();
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        methodSignature.getDeclClassType().toString().replace('.', '/'),
        methodSignature.getName(),
        asmMethodDescriptor,
        false);

  }

  private void convertStaticInvokeExprToLocalAssignmentStmt(
      Local leftOp, JStaticInvokeExpr rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    // Load method arguments onto the stack
    loadArguments(rightOp.getArgs(), mv, indexMapper);

    // Get the method signature
    MethodSignature methodSignature = rightOp.getMethodSignature();
    String asmMethodDescriptor = toAsmMethodDescriptor(methodSignature);

    // Invoke the static method
    mv.visitMethodInsn(
        INVOKESTATIC,
        methodSignature.getDeclClassType().toString().replace('.', '/'),
        methodSignature.getName(),
        asmMethodDescriptor,
        false);

    // Store the result in the local variable if the method has a return type
    if (!methodSignature.getType().equals(VoidType.getInstance())) {
      mv.visitVarInsn(getStoreOpcode(leftOp.getType()), indexMapper.getLocalIndex(leftOp));
    }
  }

  private void convertDynamicInvokeExprToLocalAssignmentStmt(
      Local leftOp, JDynamicInvokeExpr rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    // Load the method arguments onto the stack
    if (!indexMapper.isExceptionBlock()) {
      loadArguments(rightOp.getArgs(), mv, indexMapper);
    }

    // Convert the bootstrap method and its arguments to ASM Handle and arguments
    Handle bootstrapMethodHandle = toAsmHandle(rightOp.getBootstrapMethodSignature());
    Object[] bootstrapArgs = convertBootstrapArguments(rightOp.getBootstrapArgs(), indexMapper);

    // Convert the method descriptor to ASM format
    String asmMethodDescriptor = toAsmMethodDescriptor(rightOp.getMethodSignature());

    // Invoke the dynamic method
    mv.visitInvokeDynamicInsn(
        rightOp.getMethodSignature().getName(),
        asmMethodDescriptor,
        bootstrapMethodHandle,
        bootstrapArgs);

    // Store the result in the local variable if the method has a return type
    if (!rightOp.getMethodSignature().getType().equals(VoidType.getInstance())) {
      mv.visitVarInsn(getStoreOpcode(leftOp.getType()), indexMapper.getLocalIndex(leftOp));
    }
  }

  private void convertNewExpressionToLocalAssignmentStmt(
      Local leftOp, JNewExpr rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    String internalTypeName = rightOp.getType().getFullyQualifiedName().replace('.', '/');

    // Create a new instance of the object
    mv.visitTypeInsn(NEW, internalTypeName); // NEW: Pushes a reference to the uninitialized object

    // Duplicate the reference for later use (e.g., for a constructor call)
    mv.visitInsn(DUP);
    indexMapper.getLocalIndex(leftOp);
    indexMapper.setRecentObjectCreatiion(true);
    // Store the duplicated reference into the local variable
    // mv.visitVarInsn(getStoreOpcode(leftOp.getType()), indexMapper.getLocalIndex(leftOp));

    // Do not invoke the constructor here; it should be handled elsewhere
  }

  private void convertNegExpression(MethodVisitor mv, int localIndex, Type operandType) {
    // Step 1: Load the operand from local variable
    if (operandType.equals(PrimitiveType.getInt())) {
      mv.visitVarInsn(Opcodes.ILOAD, localIndex); // Load int
      mv.visitInsn(Opcodes.INEG); // Negate
      mv.visitVarInsn(Opcodes.ISTORE, localIndex); // Store back
    } else if (operandType.equals(PrimitiveType.getLong())) {
      mv.visitVarInsn(Opcodes.LLOAD, localIndex); // Load long
      mv.visitInsn(Opcodes.LNEG); // Negate
      mv.visitVarInsn(Opcodes.LSTORE, localIndex); // Store back
    } else if (operandType.equals(PrimitiveType.getFloat())) {
      mv.visitVarInsn(Opcodes.FLOAD, localIndex); // Load float
      mv.visitInsn(Opcodes.FNEG); // Negate
      mv.visitVarInsn(Opcodes.FSTORE, localIndex); // Store back
    } else if (operandType.equals(PrimitiveType.getDouble())) {
      mv.visitVarInsn(Opcodes.DLOAD, localIndex); // Load double
      mv.visitInsn(Opcodes.DNEG); // Negate
      mv.visitVarInsn(Opcodes.DSTORE, localIndex); // Store back
    } else if (operandType.equals(PrimitiveType.getChar())) {
      mv.visitVarInsn(Opcodes.ILOAD, localIndex); // Load char (treated as int)
      mv.visitInsn(Opcodes.INEG); // Negate the int value
      mv.visitVarInsn(Opcodes.ISTORE, localIndex); // Store back as int
    } else {

      throw new IllegalArgumentException("Unsupported operand type: " + operandType);
    }
  }

  private void convertBinaryExpressionToLocalAssignmentStmt(
      Local leftOp, AbstractBinopExpr rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    // Load the first operand
    loadOperand(rightOp.getOp1(), mv, indexMapper);

    // Load the second operand
    loadOperand(rightOp.getOp2(), mv, indexMapper);

    // Perform the binary operation
    mv.visitInsn(getOpcodeForBinopExpr(rightOp));

    // Store the result back into the left local variable
    int indexLeftOp = indexMapper.getLocalIndex(leftOp);
    mv.visitVarInsn(getStoreOpcode(leftOp.getType()), indexLeftOp);
  }

  private void convertCastExpressionToLocalAssignmentStmt(
          Local leftOp, JCastExpr rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {

    if (!leftOp.getName().contains("stack#")) {
      System.out.println("RightOP::CASTEXPR" + rightOp);

      Type rightType = rightOp.getOp().getType();
      Type leftType = rightOp.getType();

      // Load the right operand into the stack
      if (rightOp.getOp() instanceof Local) {
        Local rightOpOp = (Local) rightOp.getOp();
        if (!indexMapper.isStackVariable(rightOpOp)) {
          mv.visitVarInsn(getLoadOpcode(rightType), indexMapper.getLocalIndex(rightOpOp));
        }
      }

      // Handle primitive casts
      if (rightType instanceof PrimitiveType && leftType instanceof PrimitiveType) {
        handlePrimitiveCast(rightType, leftType, mv);
      }
      // Handle object casts
      else {
        mv.visitTypeInsn(CHECKCAST, leftType.toString().replace(".", "/"));
      }

      // Store the result of the cast in leftOp
      if(!leftOp.getName().contains("#")){
        mv.visitVarInsn(getStoreOpcode(leftType), indexMapper.getLocalIndex(leftOp));
      }


    } else {
      // Handle exception-related locals
      System.out.println(leftOp);
      System.out.println(rightOp);
      indexMapper.updateExceptionReference(leftOp, indexMapper.getLocalIndex(rightOp.getOp()));
    }
  }

  // Helper method for primitive casts
  private void handlePrimitiveCast(Type rightType, Type leftType, MethodVisitor mv) {
    // From int
    if (rightType.equals(PrimitiveType.getInt())) {
      if (leftType.equals(PrimitiveType.getByte())) {
        mv.visitInsn(Opcodes.I2B); // int to byte
      } else if (leftType.equals(PrimitiveType.getChar())) {
        mv.visitInsn(Opcodes.I2C); // int to char
      } else if (leftType.equals(PrimitiveType.getShort())) {
        mv.visitInsn(Opcodes.I2S); // int to short
      } else if (leftType.equals(PrimitiveType.getLong())) {
        mv.visitInsn(Opcodes.I2L); // int to long
      } else if (leftType.equals(PrimitiveType.getFloat())) {
        mv.visitInsn(Opcodes.I2F); // int to float
      } else if (leftType.equals(PrimitiveType.getDouble())) {
        mv.visitInsn(Opcodes.I2D); // int to double
      }
    }
    // From boolean
    else if (rightType.equals(PrimitiveType.getBoolean())) {
      if (leftType.equals(PrimitiveType.getByte())) {
        mv.visitInsn(Opcodes.ICONST_1); // Push 1 for true
        mv.visitInsn(Opcodes.ICONST_0); // Push 0 for false
        mv.visitInsn(Opcodes.IAND); // Perform AND operation
        mv.visitInsn(Opcodes.I2B); // Convert to byte
      } else if (leftType.equals(PrimitiveType.getChar())) {
        mv.visitInsn(Opcodes.ICONST_1); // Push 1 for true
        mv.visitInsn(Opcodes.ICONST_0); // Push 0 for false
        mv.visitInsn(Opcodes.IAND); // Perform AND operation
        mv.visitInsn(Opcodes.I2C); // Convert to char
      } else if (leftType.equals(PrimitiveType.getShort())) {
        mv.visitInsn(Opcodes.ICONST_1); // Push 1 for true
        mv.visitInsn(Opcodes.ICONST_0); // Push 0 for false
        mv.visitInsn(Opcodes.IAND); // Perform AND operation
        mv.visitInsn(Opcodes.I2S); // Convert to short
      } else if (leftType.equals(PrimitiveType.getInt())) {
        // Boolean to int: no explicit cast needed, treated as 1/0
      } else if (leftType.equals(PrimitiveType.getLong())) {
        mv.visitInsn(Opcodes.ICONST_1); // Push 1 for true
        mv.visitInsn(Opcodes.ICONST_0); // Push 0 for false
        mv.visitInsn(Opcodes.IAND); // Perform AND operation
        mv.visitInsn(Opcodes.I2L); // Convert to long
      } else if (leftType.equals(PrimitiveType.getFloat())) {
        mv.visitInsn(Opcodes.ICONST_1); // Push 1 for true
        mv.visitInsn(Opcodes.ICONST_0); // Push 0 for false
        mv.visitInsn(Opcodes.IAND); // Perform AND operation
        mv.visitInsn(Opcodes.I2F); // Convert to float
      } else if (leftType.equals(PrimitiveType.getDouble())) {
        mv.visitInsn(Opcodes.ICONST_1); // Push 1 for true
        mv.visitInsn(Opcodes.ICONST_0); // Push 0 for false
        mv.visitInsn(Opcodes.IAND); // Perform AND operation
        mv.visitInsn(Opcodes.I2D); // Convert to double
      } else {
        throw new UnsupportedOperationException("Unsupported cast from boolean to " + leftType);
      }
    }
    // From long
    else if (rightType.equals(PrimitiveType.getLong())) {
      if (leftType.equals(PrimitiveType.getInt())) {
        mv.visitInsn(Opcodes.L2I); // long to int
      } else if (leftType.equals(PrimitiveType.getFloat())) {
        mv.visitInsn(Opcodes.L2F); // long to float
      } else if (leftType.equals(PrimitiveType.getDouble())) {
        mv.visitInsn(Opcodes.L2D); // long to double
      } else if (leftType.equals(PrimitiveType.getByte())) {
        mv.visitInsn(Opcodes.L2I); // long to int
        mv.visitInsn(Opcodes.I2B); // int to byte
      } else if (leftType.equals(PrimitiveType.getShort())) {
        mv.visitInsn(Opcodes.L2I); // long to int
        mv.visitInsn(Opcodes.I2S); // int to short
      } else if (leftType.equals(PrimitiveType.getChar())) {
        mv.visitInsn(Opcodes.L2I); // long to int
        mv.visitInsn(Opcodes.I2C); // int to char
      }
    }
    // From float
    else if (rightType.equals(PrimitiveType.getFloat())) {
      if (leftType.equals(PrimitiveType.getInt())) {
        mv.visitInsn(Opcodes.F2I); // float to int
      } else if (leftType.equals(PrimitiveType.getLong())) {
        mv.visitInsn(Opcodes.F2L); // float to long
      } else if (leftType.equals(PrimitiveType.getDouble())) {
        mv.visitInsn(Opcodes.F2D); // float to double
      } else if (leftType.equals(PrimitiveType.getByte())) {
        mv.visitInsn(Opcodes.F2I); // float to int
        mv.visitInsn(Opcodes.I2B); // int to byte
      } else if (leftType.equals(PrimitiveType.getShort())) {
        mv.visitInsn(Opcodes.F2I); // float to int
        mv.visitInsn(Opcodes.I2S); // int to short
      } else if (leftType.equals(PrimitiveType.getChar())) {
        mv.visitInsn(Opcodes.F2I); // float to int
        mv.visitInsn(Opcodes.I2C); // int to char
      }
    }
    // From double
    else if (rightType.equals(PrimitiveType.getDouble())) {
      if (leftType.equals(PrimitiveType.getInt())) {
        mv.visitInsn(Opcodes.D2I); // double to int
      } else if (leftType.equals(PrimitiveType.getLong())) {
        mv.visitInsn(Opcodes.D2L); // double to long
      } else if (leftType.equals(PrimitiveType.getFloat())) {
        mv.visitInsn(Opcodes.D2F); // double to float
      } else if (leftType.equals(PrimitiveType.getByte())) {
        mv.visitInsn(Opcodes.D2I); // double to int
        mv.visitInsn(Opcodes.I2B); // int to byte
      } else if (leftType.equals(PrimitiveType.getShort())) {
        mv.visitInsn(Opcodes.D2I); // double to int
        mv.visitInsn(Opcodes.I2S); // int to short
      } else if (leftType.equals(PrimitiveType.getChar())) {
        mv.visitInsn(Opcodes.D2I); // double to int
        mv.visitInsn(Opcodes.I2C); // int to char
      }
    }
    // From byte
    else if (rightType.equals(PrimitiveType.getByte())) {
      if (leftType.equals(PrimitiveType.getInt())) {
        // No explicit operation needed, treated as int
      } else if (leftType.equals(PrimitiveType.getChar())) {
        mv.visitInsn(Opcodes.I2C); // byte to char
      } else if (leftType.equals(PrimitiveType.getShort())) {
        mv.visitInsn(Opcodes.I2S); // byte to short
      } else if (leftType.equals(PrimitiveType.getLong())) {
        mv.visitInsn(Opcodes.I2L); // byte to long
      } else if (leftType.equals(PrimitiveType.getFloat())) {
        mv.visitInsn(Opcodes.I2F); // byte to float
      } else if (leftType.equals(PrimitiveType.getDouble())) {
        mv.visitInsn(Opcodes.I2D); // byte to double
      }
    }
    // From short
    else if (rightType.equals(PrimitiveType.getShort())) {
      if (leftType.equals(PrimitiveType.getInt())) {
        // No explicit operation needed, treated as int
      } else if (leftType.equals(PrimitiveType.getChar())) {
        mv.visitInsn(Opcodes.I2C); // short to char
      } else if (leftType.equals(PrimitiveType.getByte())) {
        mv.visitInsn(Opcodes.I2B); // short to byte
      } else if (leftType.equals(PrimitiveType.getLong())) {
        mv.visitInsn(Opcodes.I2L); // short to long
      } else if (leftType.equals(PrimitiveType.getFloat())) {
        mv.visitInsn(Opcodes.I2F); // short to float
      } else if (leftType.equals(PrimitiveType.getDouble())) {
        mv.visitInsn(Opcodes.I2D); // short to double
      }
    }
    // From char
    else if (rightType.equals(PrimitiveType.getChar())) {
      if (leftType.equals(PrimitiveType.getInt())) {
        mv.visitInsn(Opcodes.I2C); // char to int
      } else if (leftType.equals(PrimitiveType.getShort())) {
        mv.visitInsn(Opcodes.I2S); // char to short
      } else if (leftType.equals(PrimitiveType.getByte())) {
        mv.visitInsn(Opcodes.I2B); // char to byte
      } else if (leftType.equals(PrimitiveType.getLong())) {
        mv.visitInsn(Opcodes.I2L); // char to long
      } else if (leftType.equals(PrimitiveType.getFloat())) {
        mv.visitInsn(Opcodes.I2F); // char to float
      } else if (leftType.equals(PrimitiveType.getDouble())) {
        mv.visitInsn(Opcodes.I2D); // char to double
      }
    }
    // Unsupported cast
    else {
      throw new UnsupportedOperationException(
              "Unsupported primitive cast from " + rightType + " to " + leftType);
    }
  }







  private void convertInstanceOfExpressionToLocalAssignmentStmt(
      Local leftOp, JInstanceOfExpr rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    if (rightOp.getOp() instanceof Local) {
      Local operandLocal = (Local) rightOp.getOp();
      mv.visitVarInsn(ALOAD, indexMapper.getLocalIndex(operandLocal));
    } else {
      throw new UnsupportedOperationException(
          "The operand for the instanceof check is not a Local.");
    }
    String internalTypeName = rightOp.getCheckType().toString().replace('.', '/');
    mv.visitTypeInsn(INSTANCEOF, internalTypeName);
    mv.visitVarInsn(ISTORE, indexMapper.getLocalIndex(leftOp));
  }

  private void convertArrayRefToLocalAssignmentStmt(
      Local leftOp, JArrayRef rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    // Load the array reference
    Local arrayLocal = rightOp.getBase();
    if (!indexMapper.isStackVariable(arrayLocal)) {
      mv.visitVarInsn(getLoadOpcode(arrayLocal.getType()), indexMapper.getLocalIndex(arrayLocal));
    }

    // Load the index
    Value indexValue = rightOp.getIndex();
    if (indexValue instanceof Local) {
      mv.visitVarInsn(
          getLoadOpcode(indexValue.getType()), indexMapper.getLocalIndex((Local) indexValue));
    } else if (indexValue instanceof IntConstant) {
      loadIntConstant(mv, ((IntConstant) indexValue).getValue());
    } else {
      throw new UnsupportedOperationException(
          "Unsupported array index type: " + indexValue.getClass().getName());
    }

    // Load the array element onto the stack
    mv.visitInsn(getLoadArrayOpcode(rightOp.getType()));

    // Check if the leftOp is already a stack variable
    if (!indexMapper.isStackVariable(leftOp)) {
      // Store the array element into the local variable
      mv.visitVarInsn(getStoreOpcode(leftOp.getType()), indexMapper.getLocalIndex(leftOp));
    }
  }

  private void convertFieldRefToLocalAssignmentStmt(
      Local leftOp, JFieldRef rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    FieldReferenceIndexer fieldIndexer = new FieldReferenceIndexer(indexMapper);
    fieldIndexer.loadFieldReference(mv, rightOp);
    if (rightOp instanceof JStaticFieldRef) {
      return;
    } else {
      if ((leftOp.getType().equals(rightOp.getFieldSignature().getType()))) {
        int index =
            indexMapper.isStatic()
                ? indexMapper.getIndexFromDef(leftOp)
                : indexMapper.getLocalIndex(leftOp);
        // mv.visitVarInsn(getStoreOpcode(rightOp.getFieldSignature().getType()), index);
      }
    }
  }

  private void convertFieldAssignmentStmt(
      JFieldRef leftOp, Value rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    FieldReferenceIndexer fieldIndexer = new FieldReferenceIndexer(indexMapper);
    try {
      if(fieldIndexer.getObjectReferenceIndex(leftOp) != -1){
        mv.visitVarInsn(ALOAD, fieldIndexer.getObjectReferenceIndex(leftOp));
      }


    } catch (Exception e) {
      if (e instanceof IndexOutOfBoundsException) {
        System.out.println("Exception during finding field reference of:::" + leftOp);
      }
    }

    if (rightOp instanceof Local) {
      // Load local variable
      if(indexMapper.isStackVariable((Local) rightOp)){
        mv.visitInsn(SWAP);
      }else{
        mv.visitVarInsn(getLoadOpcode(rightOp.getType()), indexMapper.getLocalIndex((Local) rightOp));
      }

    } else if (rightOp instanceof IntConstant) {
      // Load integer constant
      int constantValue = ((IntConstant) rightOp).getValue();
      loadIntConstant(mv, constantValue);
    } else if (rightOp instanceof FloatConstant) {
      // Load float constant
      float constantValue = ((FloatConstant) rightOp).getValue();
      mv.visitLdcInsn(constantValue);
    } else if (rightOp instanceof DoubleConstant) {
      // Load double constant
      double constantValue = ((DoubleConstant) rightOp).getValue();
      mv.visitLdcInsn(constantValue);
    } else if (rightOp instanceof LongConstant) {
      // Load long constant
      long constantValue = ((LongConstant) rightOp).getValue();
      mv.visitLdcInsn(constantValue);
    } else if (rightOp instanceof BooleanConstant) {
      // Load boolean constant
      boolean constantValue =
          Objects.equals(((BooleanConstant) rightOp).toString(), "1")
              ? Boolean.TRUE
              : Boolean.FALSE;
      mv.visitInsn(constantValue ? ICONST_1 : ICONST_0);
    } else if (rightOp instanceof StringConstant) {
      // Load string constant
      String constantValue = ((StringConstant) rightOp).getValue();
      mv.visitLdcInsn(constantValue);
    } else if (rightOp instanceof ClassConstant) {
      // Load class constant
      String constantValue = ((ClassConstant) rightOp).getValue();
      mv.visitLdcInsn(constantValue);
    } else if (rightOp instanceof NullConstant) {
      // Load null constant
      mv.visitInsn(ACONST_NULL);
    } else if (rightOp instanceof JCastExpr) {
      mv.visitTypeInsn(CHECKCAST, rightOp.getType().toString().replace(".", "/"));

    } else {
      throw new UnsupportedOperationException(
          "Unsupported constant type: " + rightOp.getClass().getName());
    }

    // Store the value into the field
    fieldIndexer.storeFieldReference(mv, leftOp);
  }

  private void loadIntConstant(MethodVisitor mv, int value) {
    if (value >= -1 && value <= 5) {
      // Use ICONST_x for -1 to 5
      mv.visitInsn(ICONST_0 + value);
    } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
      // Use BIPUSH for byte-range values
      mv.visitIntInsn(BIPUSH, value);
    } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
      // Use SIPUSH for short-range values
      mv.visitIntInsn(SIPUSH, value);
    } else {
      // Use LDC for other int values
      mv.visitLdcInsn(value);
    }
  }

  private void convertArrayAssignmentStmt(
      JArrayRef leftOp, Value rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    // Load the array reference (e.g., $stack7)
    if (!indexMapper.isStackVariable(leftOp.getBase())) {
      mv.visitVarInsn(ALOAD, indexMapper.getLocalIndex(leftOp.getBase()));
    }

    // Load the index (e.g., [0])
    Value indexValue = leftOp.getIndex();
    if (indexValue instanceof IntConstant) {
      // If the index is a constant (e.g., 0), load it directly
      loadIntConstant(mv, ((IntConstant) indexValue).getValue());
    } else if (indexValue instanceof Local) {
      // If the index is a variable, load it from the local variable table
      mv.visitVarInsn(ILOAD, indexMapper.getLocalIndex((Local) indexValue));
    } else {
      throw new UnsupportedOperationException(
          "Unsupported array index type: " + indexValue.getClass().getName());
    }

    // Load the value to be assigned (e.g., 4, "Hello", etc.)
    if (rightOp instanceof IntConstant) {
      loadIntConstant(mv, ((IntConstant) rightOp).getValue());
    } else if (rightOp instanceof FloatConstant) {
      mv.visitLdcInsn(((FloatConstant) rightOp).getValue());
    } else if (rightOp instanceof DoubleConstant) {
      mv.visitLdcInsn(((DoubleConstant) rightOp).getValue());
    } else if (rightOp instanceof LongConstant) {
      mv.visitLdcInsn(((LongConstant) rightOp).getValue());
    } else if (rightOp instanceof StringConstant) {
      // Handle string constants
      mv.visitLdcInsn(((StringConstant) rightOp).getValue());
    } else if (rightOp instanceof Local) {
      mv.visitVarInsn(getLoadOpcode(rightOp.getType()), indexMapper.getLocalIndex((Local) rightOp));
    } else if (rightOp instanceof NullConstant) {
      mv.visitInsn(Opcodes.ACONST_NULL);
    } else if (rightOp instanceof ClassConstant) {
      String constantValue = ((ClassConstant) rightOp).getValue();
      mv.visitLdcInsn(constantValue);
    } else if (rightOp instanceof JCastExpr) {
      // Handle cast expressions
      handleCastExpression((JCastExpr) rightOp, mv, indexMapper);
    } else {
      throw new UnsupportedOperationException(
          "Unsupported value type for array assignment: " + rightOp.getClass().getName());
    }

    // Store the value in the array
    mv.visitInsn(getStoreArrayOpcode(leftOp.getBase().getType()));
  }

  private void handleCastExpression(
      JCastExpr rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    Type rightType = rightOp.getOp().getType();
    if (rightOp.getOp() instanceof Local) {
      Local rightOpOp = (Local) rightOp.getOp();
      if (!indexMapper.isStackVariable(rightOpOp)) {
        mv.visitVarInsn(getLoadOpcode(rightType), indexMapper.getLocalIndex(rightOpOp));
      }
    } else {
      throw new UnsupportedOperationException(
          "Unsupported operand type for cast expression: " + rightOp.getOp().getClass());
    }
    mv.visitTypeInsn(CHECKCAST, rightOp.getType().toString().replace('.', '/'));
  }

  private void convertJavaLocalToLocalAssignmentStmt(
      Local leftOp, JavaLocal rightOp, MethodVisitor mv, LocalIndexMapper indexMapper) {
    // Load the JavaLocal variable onto the stack
    int index = indexMapper.getLocalIndex(leftOp);
    // mv.visitVarInsn(getLoadOpcode(rightOp.getType()), index);
    if (!indexMapper.isExceptionBlock() && !indexMapper.isStackVariable(rightOp)) {
      mv.visitVarInsn(getLoadOpcode(rightOp.getType()), index);
    }
    // Store the value into the target local variable (leftOp)
    mv.visitVarInsn(getStoreOpcode(leftOp.getType()), indexMapper.getLocalIndex(leftOp));

    // If there are annotations on the JavaLocal, you can handle them here if necessary
    // For example, you might want to add some bytecode related to the annotations
    for (AnnotationUsage annotation : rightOp.getAnnotations()) {
      // Process each annotation as needed
      // Example: If you want to log annotations, you could print or process them here
      // AnnotationVisitor av = mv.
      System.out.println("Annotation: " + annotation.toString());
      // TODO:add custom logic to handle specific annotations if needed
    }
  }

  private int getOpcodeForBinopExpr(AbstractBinopExpr expr) {

    // Handle long arithmetic
    if (expr instanceof JAddExpr && expr.getOp1().getType().toString().equals("long")) {
      return LADD;
    } else if (expr instanceof JSubExpr && expr.getOp1().getType().toString().equals("long")) {
      return LSUB;
    } else if (expr instanceof JMulExpr && expr.getOp1().getType().toString().equals("long")) {
      return LMUL;
    } else if (expr instanceof JDivExpr && expr.getOp1().getType().toString().equals("long")) {
      return LDIV;
    } else if (expr instanceof JRemExpr && expr.getOp1().getType().toString().equals("long")) {
      return LREM;
    }

    // Handle float arithmetic
    if (expr instanceof JAddExpr && expr.getOp1().getType().toString().equals("float")) {
      return FADD;
    } else if (expr instanceof JSubExpr && expr.getOp1().getType().toString().equals("float")) {
      return FSUB;
    } else if (expr instanceof JMulExpr && expr.getOp1().getType().toString().equals("float")) {
      return FMUL;
    } else if (expr instanceof JDivExpr && expr.getOp1().getType().toString().equals("float")) {
      return FDIV;
    } else if (expr instanceof JRemExpr && expr.getOp1().getType().toString().equals("float")) {
      return FREM;
    }

    // Handle double arithmetic
    if (expr instanceof JAddExpr && expr.getOp1().getType().toString().equals("double")) {
      return DADD;
    } else if (expr instanceof JSubExpr && expr.getOp1().getType().toString().equals("double")) {
      return DSUB;
    } else if (expr instanceof JMulExpr && expr.getOp1().getType().toString().equals("double")) {
      return DMUL;
    } else if (expr instanceof JDivExpr && expr.getOp1().getType().toString().equals("double")) {
      return DDIV;
    } else if (expr instanceof JRemExpr && expr.getOp1().getType().toString().equals("double")) {
      return DREM;
    }

    // Handle logical and bitwise operations
    if (expr instanceof JAndExpr) {
      return IAND;
    } else if (expr instanceof JOrExpr) {
      return IOR;
    } else if (expr instanceof JXorExpr) {
      return IXOR;
    } else if (expr instanceof JShlExpr) {
      return ISHL;
    } else if (expr instanceof JShrExpr) {
      return ISHR;
    } else if (expr instanceof JUshrExpr) {
      return IUSHR;
    }

    // Handle integer arithmetic
    if (expr instanceof JAddExpr) {
      return IADD;
    } else if (expr instanceof JSubExpr) {
      return ISUB;
    } else if (expr instanceof JMulExpr) {
      return IMUL;
    } else if (expr instanceof JDivExpr) {
      return IDIV;
    } else if (expr instanceof JRemExpr) {
      return IREM;
    }

    if (expr instanceof JCmpExpr) {
      System.out.println(expr.toString());
      return LCMP;
    } else if (expr instanceof JCmplExpr) {
      return DCMPL;
    } else if (expr instanceof JCmpgExpr) {
      return DCMPG;
    }

    throw new UnsupportedOperationException(
        "Unsupported binary operation: " + expr.getClass().getSimpleName());
  }

  private void loadArguments(List<Immediate> args, MethodVisitor mv, LocalIndexMapper indexMapper) {
    for (Immediate arg : args) {
      if (arg instanceof Local) {
        // Load a local variable onto the stack
        int index = indexMapper.getLocalIndex(arg);
        System.out.println(arg + " Index:: " + index);
        System.out.println(indexMapper.getLocalIndexMap());
        mv.visitVarInsn(getLoadOpcode(arg.getType()), index);

      } else if (arg instanceof StringConstant) {
        // Load a string constant onto the stack (e.g., "HelloWorld")
        mv.visitLdcInsn(((StringConstant) arg).getValue());
      } else if (arg instanceof IntConstant) {
        // Load an integer constant onto the stack
        int value = ((IntConstant) arg).getValue();
        if (value >= -1 && value <= 5) {
          mv.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
          mv.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
          mv.visitIntInsn(SIPUSH, value);
        } else {
          mv.visitLdcInsn(value);
        }
      } else if (arg instanceof FloatConstant) {
        // Load a float constant onto the stack
        mv.visitLdcInsn(((FloatConstant) arg).getValue());
      } else if (arg instanceof DoubleConstant) {
        DoubleConstant constant = (DoubleConstant) arg;
        mv.visitLdcInsn(constant.getValue()); // Load the double constant onto the stack
        // mv.visitVarInsn(Opcodes.DSTORE, index);  // Store it in the local variable for double

      } else if (arg instanceof ClassConstant) {
        String constantValue = ((ClassConstant) arg).getValue();
        mv.visitLdcInsn(constantValue);
      } else if (arg instanceof LongConstant) {
        LongConstant constant = (LongConstant) arg;
        mv.visitLdcInsn(constant.getValue());
      } else if (arg instanceof NullConstant) {
        mv.visitInsn(Opcodes.ACONST_NULL);

      } else {
        throw new UnsupportedOperationException(
            "Unsupported argument type: " + arg.getClass().getSimpleName());
      }
    }
  }

  private Handle toAsmHandle(MethodSignature bootstrapMethodSignature) {
    // Convert MethodSignature to ASM Handle
    return new Handle(
        H_INVOKESTATIC, // Assuming static handle, adjust if needed
        bootstrapMethodSignature.getDeclClassType().toString().replace('.', '/'),
        bootstrapMethodSignature.getName(),
        toAsmMethodDescriptor(bootstrapMethodSignature),
        false);
  }

  // Converts a Soot method signature to an ASM method descriptor
  @Override
  public String toAsmMethodDescriptor(MethodSignature methodSignature) {
    StringBuilder descriptor = new StringBuilder();
    descriptor.append("(");

    // Append parameter types
    for (sootup.core.types.Type parameterType : methodSignature.getParameterTypes()) {
      descriptor.append(toAsmTypeDescriptor(parameterType));
    }

    descriptor.append(")");
    // Append return type
    descriptor.append(toAsmTypeDescriptor(methodSignature.getType()));
    return descriptor.toString();
  }
  // Converts a Soot type to an ASM type descriptor
  @Override
  public String toAsmTypeDescriptor(sootup.core.types.Type type) {
    if (type instanceof PrimitiveType || type instanceof VoidType) {
      return getPrimitiveTypeDescriptor(type);
    } else if (type instanceof ArrayType) {
      ArrayType arrayType = (ArrayType) type;
      return "[".repeat(arrayType.getDimension()) + toAsmTypeDescriptor(arrayType.getBaseType());
    } else if (type instanceof ClassType) {
      return "L" + ((ClassType) type).getFullyQualifiedName().replace('.', '/') + ";";
    } else {
      throw new UnsupportedOperationException("Unsupported type for ASM descriptor: " + type);
    }
  }

  private Object[] convertBootstrapArguments(
      List<Immediate> bootstrapArgs, LocalIndexMapper indexMapper) {
    Object[] args = new Object[bootstrapArgs.size()];
    for (int i = 0; i < bootstrapArgs.size(); i++) {
      Immediate arg = bootstrapArgs.get(i);
      if (arg instanceof Local) {
        args[i] = indexMapper.getLocalIndex(arg);
      } else if (arg instanceof IntConstant) {
        args[i] = ((IntConstant) arg).getValue();
      } else if (arg instanceof FloatConstant) {
        args[i] = ((FloatConstant) arg).getValue();
      } else if (arg instanceof StringConstant) {
        args[i] = ((StringConstant) arg).getValue();
      } else if (arg instanceof MethodType) {
        // Handle method type
        args[i] = org.objectweb.asm.Type.getMethodType(arg.toString());
      } else if (arg instanceof MethodHandle) {
        // Handle method handle (if necessary)
        args[i] =
            convertToASMHandle(
                (MethodHandle) arg) /* Use appropriate logic to convert the method handle */;
      } else if (arg instanceof ClassConstant) {
        ClassConstant c = (ClassConstant) arg;
        args[i] = org.objectweb.asm.Type.getType("L" + c.getValue().replace('.', '/') + ";");

      } else {
        throw new UnsupportedOperationException(
            "Unsupported bootstrap argument type: "
                + arg.toString()
                + " of type: "
                + arg.getType());
      }
    }
    return args;
  }

  private Handle convertToASMHandle(MethodHandle methodHandle) {
    int asmTag = getASMHandleTag(methodHandle.getKind());
    String owner =
        methodHandle.getReferenceSignature().getSubSignature().getName().replace('.', '/');
    String name = methodHandle.getReferenceSignature().getName();
    String descriptor = methodHandle.getReferenceSignature().getSubSignature().getName();

    boolean isInterface = methodHandle.getKind() == MethodHandle.Kind.REF_INVOKE_INTERFACE;
    return new Handle(asmTag, owner, name, descriptor, isInterface);
  }

  private int getASMHandleTag(MethodHandle.Kind kind) {
    switch (kind) {
      case REF_GET_FIELD:
        return Opcodes.H_GETFIELD;
      case REF_GET_FIELD_STATIC:
        return Opcodes.H_GETSTATIC;
      case REF_PUT_FIELD:
        return Opcodes.H_PUTFIELD;
      case REF_PUT_FIELD_STATIC:
        return Opcodes.H_PUTSTATIC;
      case REF_INVOKE_VIRTUAL:
        return Opcodes.H_INVOKEVIRTUAL;
      case REF_INVOKE_STATIC:
        return Opcodes.H_INVOKESTATIC;
      case REF_INVOKE_SPECIAL:
        return Opcodes.H_INVOKESPECIAL;
      case REF_INVOKE_CONSTRUCTOR:
        return Opcodes.H_NEWINVOKESPECIAL;
      case REF_INVOKE_INTERFACE:
        return Opcodes.H_INVOKEINTERFACE;
      default:
        throw new UnsupportedOperationException("Unsupported MethodHandle.Kind: " + kind);
    }
  }

  // Returns the descriptor for a primitive type
  private String getPrimitiveTypeDescriptor(Type type) {
    if (type == PrimitiveType.BooleanType.getInstance()) {
      return "Z";
    } else if (type == PrimitiveType.ByteType.getInstance()) {
      return "B";
    } else if (type == PrimitiveType.CharType.getInstance()) {
      return "C";
    } else if (type == PrimitiveType.ShortType.getInstance()) {
      return "S";
    } else if (type == PrimitiveType.IntType.getInstance()) {
      return "I";
    } else if (type == PrimitiveType.LongType.getInstance()) {
      return "J";
    } else if (type == PrimitiveType.FloatType.getInstance()) {
      return "F";
    } else if (type == PrimitiveType.DoubleType.getInstance()) {
      return "D";
    } else if (type == VoidType.getInstance()) {
      return "V";
    } else {
      throw new UnsupportedOperationException("Unsupported primitive type: " + type);
    }
  }

  private void loadOperand(Value operand, MethodVisitor mv, LocalIndexMapper indexMapper) {
    if (operand instanceof Local) {
      int index = indexMapper.getLocalIndex((Local) operand);
      mv.visitVarInsn(getLoadOpcode(operand.getType()), index);
    } else if (operand instanceof IntConstant) {
      // Load constant integers directly onto the stack
      int value = ((IntConstant) operand).getValue();
      if (value >= -1 && value <= 5) {
        mv.visitInsn(ICONST_0 + value); // Use ICONST_0 through ICONST_5 for small values
      } else {
        mv.visitLdcInsn(value); // Use LDC for larger constants
      }
    } else if (operand instanceof DoubleConstant) {
      mv.visitLdcInsn(((DoubleConstant) operand).getValue());
    } else if (operand instanceof LongConstant) {
      mv.visitLdcInsn(((LongConstant) operand).getValue());
    } else if (operand instanceof FloatConstant) {
      mv.visitLdcInsn(((FloatConstant) operand).getValue());
    } else {
      throw new UnsupportedOperationException(
          "Unsupported operand type: " + operand.getClass().getName());
    }
  }

  private int getPrimitiveArrayTypeOpcode(Type type) {
    switch (type.toString()) {
      case "int":
        return T_INT;
      case "boolean":
        return T_BOOLEAN;
      case "byte":
        return T_BYTE;
      case "char":
        return T_CHAR;
      case "short":
        return T_SHORT;
      case "long":
        return T_LONG;
      case "float":
        return T_FLOAT;
      case "double":
        return T_DOUBLE;
      default:
        throw new UnsupportedOperationException("Unsupported primitive array type: " + type);
    }
  }
}
