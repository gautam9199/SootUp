package transformer.utils;

import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.jimple.common.stmt.JGotoStmt;
import sootup.core.jimple.common.stmt.JReturnStmt;
import sootup.core.jimple.common.stmt.JReturnVoidStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.Body;

public class GotoStatementConverter extends ConversionUtil {

  static Logger logger = LoggerFactory.getLogger(GotoStatementConverter.class);

  // This map stores the correspondence between Stmt objects and their Labels

  private final Map<Stmt, Label> labelMap = new HashMap<>();

  // Method to add Stmt and corresponding Label to the map
  public void addLabelForStmt(Stmt stmt, Label label) {
    labelMap.put(stmt, label);
  }

  public void convertGotoStmt(
      JGotoStmt stmt, MethodVisitor mv, Body body, LocalIndexMapper indexMapper) {
    // Retrieve the target Stmt using the body and its corresponding Label
    Stmt targetStmt = stmt.getTargetStmts(body).get(0);
    Label targetLabel = labelMap.get(targetStmt);

    // Emit the ASM bytecode instruction to perform the jump

    if (targetLabel == null) {
      if (targetStmt instanceof JReturnStmt || targetStmt instanceof JReturnVoidStmt) {
        if (targetStmt instanceof JReturnVoidStmt) {
          new ReturnStatementConverter().convertReturnVoidStmt((JReturnVoidStmt) targetStmt, mv);
          return;
        } else {
          new ReturnStatementConverter()
              .convertReturnStmt((JReturnStmt) targetStmt, mv, indexMapper);
          return;
        }
      }
      throw new IllegalStateException("No label found for target statement: " + targetStmt);
    }
    mv.visitJumpInsn(org.objectweb.asm.Opcodes.GOTO, targetLabel);
  }
}
