package transformer.utils;

import static org.objectweb.asm.Opcodes.ACONST_NULL;

import java.util.List;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.constant.*;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JDynamicInvokeExpr;
import sootup.core.jimple.common.expr.JInterfaceInvokeExpr;
import sootup.core.jimple.common.expr.JSpecialInvokeExpr;
import sootup.core.jimple.common.expr.JStaticInvokeExpr;
import sootup.core.jimple.common.expr.JVirtualInvokeExpr;
import sootup.core.jimple.common.stmt.JInvokeStmt;
import sootup.core.signatures.MethodSignature;

public class InvokeStatementConverter extends ConversionUtil {

  static Logger logger = LoggerFactory.getLogger(InvokeStatementConverter.class);

  public void convertInvokeStmt(
      JInvokeStmt stmt,
      MethodVisitor mv,
      LocalIndexMapper indexMapper,
      Boolean isConstructor,
      Boolean isStatic) {

    AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr().get();

    // Convert the method descriptor to ASM format
    String asmMethodDescriptor = toAsmMethodDescriptor(invokeExpr.getMethodSignature());

    // Handle different types of invoke expressions
    if (invokeExpr instanceof JStaticInvokeExpr) {
      handleStaticInvokeExpr((JStaticInvokeExpr) invokeExpr, mv, asmMethodDescriptor, indexMapper);
    } else if (invokeExpr instanceof JSpecialInvokeExpr) {
      handleSpecialInvokeExpr(
          (JSpecialInvokeExpr) invokeExpr,
          mv,
          indexMapper,
          asmMethodDescriptor,
          isConstructor,
          isStatic);
    } else if (invokeExpr instanceof JInterfaceInvokeExpr) {
      handleInterfaceInvokeExpr(
          (JInterfaceInvokeExpr) invokeExpr, mv, indexMapper, asmMethodDescriptor);
    } else if (invokeExpr instanceof JVirtualInvokeExpr) {
      handleVirtualInvokeExpr(
          (JVirtualInvokeExpr) invokeExpr,
          mv,
          indexMapper,
          asmMethodDescriptor,
          isStatic,
          isConstructor);
    } else if (invokeExpr instanceof JDynamicInvokeExpr) {
      handleDynamicInvokeExpr((JDynamicInvokeExpr) invokeExpr, mv, indexMapper);
    } else {
      throw new UnsupportedOperationException(
          "Unsupported invoke expression type: " + invokeExpr.getClass().getName());
    }
  }

  private void handleStaticInvokeExpr(
      JStaticInvokeExpr invokeExpr,
      MethodVisitor mv,
      String asmMethodDescriptor,
      LocalIndexMapper indexMapper) {
    // Load method arguments onto the stack
    loadArguments(invokeExpr.getArgs(), mv, indexMapper);

    // Invoke static method
    MethodSignature methodSignature = invokeExpr.getMethodSignature();
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        methodSignature.getDeclClassType().toString().replace('.', '/'),
        methodSignature.getName(),
        asmMethodDescriptor,
        false);
  }

  private void handleSpecialInvokeExpr(
      JSpecialInvokeExpr invokeExpr,
      MethodVisitor mv,
      LocalIndexMapper indexMapper,
      String asmMethodDescriptor,
      boolean isConstructor,
      boolean isStatic) {
    if (!isConstructor && !indexMapper.isRecentObjectCreation()) {
      // Load 'this' from index 0 only for non-static, non-constructor methods
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

  private void handleInterfaceInvokeExpr(
      JInterfaceInvokeExpr invokeExpr,
      MethodVisitor mv,
      LocalIndexMapper indexMapper,
      String asmMethodDescriptor) {
    // Load the instance (this reference) onto the stack
    Local base = invokeExpr.getBase();
    mv.visitVarInsn(Opcodes.ALOAD, indexMapper.getLocalIndex(base));

    // Load method arguments onto the stack
    loadArguments(invokeExpr.getArgs(), mv, indexMapper);

    // Invoke interface method
    MethodSignature methodSignature = invokeExpr.getMethodSignature();
    mv.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        methodSignature.getDeclClassType().toString().replace('.', '/'),
        methodSignature.getName(),
        asmMethodDescriptor,
        true);
  }

  private void handleVirtualInvokeExpr(
      JVirtualInvokeExpr invokeExpr,
      MethodVisitor mv,
      LocalIndexMapper indexMapper,
      String asmMethodDescriptor,
      Boolean isStatic,
      Boolean isConstructor) {
    // Load the base reference (this or another instance)

    Local base = invokeExpr.getBase();
    int baseIndex = indexMapper.getLocalIndex(base);
    System.out.println("BASE" + base.getType().toString());
    if (!isConstructor ) {
      if (!indexMapper.isStackVariable(base)) {
        System.out.println("Use of base" + base.getUses().count());
        mv.visitVarInsn(Opcodes.ALOAD, baseIndex); // Load base reference for instance method
      }
    }

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

  private void handleDynamicInvokeExpr(
      JDynamicInvokeExpr invokeExpr, MethodVisitor mv, LocalIndexMapper indexMapper) {
    // Convert the bootstrap method and its arguments to ASM Handle and arguments
    Handle bootstrapMethodHandle = toAsmHandle(invokeExpr.getBootstrapMethodSignature());
    Object[] bootstrapArgs = convertBootstrapArguments(invokeExpr.getBootstrapArgs(), indexMapper);

    // Convert the method descriptor to ASM format
    String asmMethodDescriptor = toAsmMethodDescriptor(invokeExpr.getMethodSignature());

    // Load method arguments onto the stack
    loadArguments(invokeExpr.getArgs(), mv, indexMapper);

    // Dynamic invocation
    mv.visitInvokeDynamicInsn(
        invokeExpr.getMethodSignature().getName(),
        asmMethodDescriptor,
        bootstrapMethodHandle,
        bootstrapArgs);
  }

  private void loadArguments(List<Immediate> args, MethodVisitor mv, LocalIndexMapper indexMapper) {
    for (Immediate arg : args) {
      if (arg instanceof Local) {
        // Load a local variable onto the stack
        int index =
            indexMapper.getLocalIndex(
                arg); // indexMapper.isStatic() ? indexMapper.getIndexFromDef((Local)arg) :
        if (!indexMapper.isStackVariable((Local) arg) && !((Local) arg).getName().contains("#")) {
          mv.visitVarInsn(getLoadOpcode(arg.getType()), index);
        }

      } else if (arg instanceof StringConstant) {
        // Load a string constant onto the stack (e.g., "HelloWorld")
        mv.visitLdcInsn(((StringConstant) arg).getValue());
      } else if (arg instanceof IntConstant) {
        // Load an integer constant onto the stack
        int value = ((IntConstant) arg).getValue();
        if (value >= -1 && value <= 5) {
          mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
          mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
          mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
          mv.visitLdcInsn(value);
        }
      } else if (arg instanceof FloatConstant) {
        // Load a float constant onto the stack
        mv.visitLdcInsn(((FloatConstant) arg).getValue());
      } else if (arg instanceof LongConstant) {
        mv.visitLdcInsn(((LongConstant) arg).getValue());
      } else if (arg instanceof ClassConstant) {
          mv.visitLdcInsn(arg.getType().toString());
      } else if (arg instanceof DoubleConstant) {
        mv.visitLdcInsn(((DoubleConstant) arg).getValue());
      } else if (arg instanceof NullConstant) {
        // Load null constant
        mv.visitInsn(ACONST_NULL);
      } else {
        throw new UnsupportedOperationException(
            "Unsupported argument type: " + arg.getClass().getSimpleName());
      }
    }
  }

  private Handle toAsmHandle(MethodSignature bootstrapMethodSignature) {
    // Convert MethodSignature to ASM Handle
    return new Handle(
        Opcodes.H_INVOKESTATIC, // Assuming static handle, adjust if needed
        bootstrapMethodSignature.getDeclClassType().toString().replace('.', '/'),
        bootstrapMethodSignature.getName(),
        toAsmMethodDescriptor(bootstrapMethodSignature),
        false);
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
      } else {
        throw new UnsupportedOperationException("Unsupported bootstrap argument type.");
      }
    }
    return args;
  }
}
