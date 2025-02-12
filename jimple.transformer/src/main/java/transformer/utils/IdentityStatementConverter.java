package transformer.utils;

import java.util.stream.Collectors;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.ref.IdentityRef;
import sootup.core.jimple.common.ref.JCaughtExceptionRef;
import sootup.core.jimple.common.ref.JParameterRef;
import sootup.core.jimple.common.ref.JThisRef;
import sootup.core.jimple.common.stmt.JIdentityStmt;
import sootup.core.model.SootMethod;

public class IdentityStatementConverter extends ConversionUtil {
  static Logger logger = LoggerFactory.getLogger(IdentityStatementConverter.class);

  public void convertIdentityStmt(
      JIdentityStmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper, SootMethod method) {
    IdentityRef rightOp = stmt.getRightOp();
    Local leftOp = stmt.getLeftOp();

    if (rightOp instanceof JThisRef) {
      // 'this' is handled by LocalIndexMapper
      if (indexMapper.isConstructor()) {
        indexMapper.getLocalIndex(leftOp);
        int index = indexMapper.getLocalIndex(leftOp);
        mv.visitVarInsn(Opcodes.ALOAD, index);
        return;
      }

      // int index = indexMapper.getLocalIndex(leftOp);
      // mv.visitVarInsn(Opcodes.ALOAD, index);
    } else if (rightOp instanceof JParameterRef) {
      // Handle method parameter references
      System.out.println(
          "Uses" + method.getBody().getUses().distinct().collect(Collectors.toList()));
      System.out.println("Uses2" + leftOp.getUses().collect(Collectors.toList()));
      JParameterRef paramRef = (JParameterRef) rightOp;

      int paramIndex = paramRef.getIndex() + 1; // Get the parameter index
      indexMapper.getLocalIndex(leftOp);
      if (leftOp.getUses().findAny().isPresent()) {
        if (paramRef.getIndex() == 0) {

          int loadOpcode = getLoadOpcode(paramRef.getType());
          mv.visitVarInsn(loadOpcode, paramIndex); // Load the parameter
          return;
        }
        int loadOpcode = getLoadOpcode(paramRef.getType());
        mv.visitVarInsn(
            getStoreOpcode(leftOp.getType()),
            indexMapper.getLocalIndex(leftOp)); // Store in local variable
        mv.visitVarInsn(loadOpcode, paramIndex); // Load the parameter
        // mv.visitVarInsn(getStoreOpcode(paramRef.getType()), paramIndex);

      }

    } else if (rightOp instanceof JCaughtExceptionRef) {
      // Handle caught exception reference
      System.out.println(stmt);

      convertCaughtExceptionRef((JCaughtExceptionRef) rightOp, leftOp, mv, indexMapper);
    } else {
      throw new UnsupportedOperationException(
          "Unsupported IdentityRef type: " + rightOp.getClass().getName());
    }
  }

  private void convertCaughtExceptionRef(
      JCaughtExceptionRef caughtExceptionRef,
      Local leftOp,
      MethodVisitor mv,
      LocalIndexMapper indexMapper) {
    // We need to store the caught exception in a local variable
    int localIndex = indexMapper.getLocalIndex(leftOp);

    // The caught exception is already on the stack after a catch block is entered
    mv.visitVarInsn(
        Opcodes.ASTORE, localIndex); // Store the exception reference into the local variable

    logger.info("Caught exception stored in local variable index: " + localIndex);
  }
}
