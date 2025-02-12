package transformer.utils;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.jimple.common.stmt.JNopStmt;

public class NopStatementConverter {
  static Logger logger = LoggerFactory.getLogger(NopStatementConverter.class);

  public void convertNopStmt(JNopStmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper) {
    // No-operation statement in Jimple corresponds to a NOP instruction in ASM
    mv.visitInsn(Opcodes.NOP);
  }
}
