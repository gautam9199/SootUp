package transformer.utils;

import java.util.Map;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import sootup.core.graph.BasicBlock;
import sootup.core.jimple.javabytecode.stmt.JEnterMonitorStmt;
import sootup.core.jimple.javabytecode.stmt.JExitMonitorStmt;
import sootup.core.model.SootMethod;

public class MonitorStatementConverter {
	
	public void convertEnterMonitorStmt(JEnterMonitorStmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper, Map<BasicBlock<?>, Label> blockLabels, SootMethod method) {
		int index = indexMapper.getLocalIndex(stmt.getOp());
		mv.visitVarInsn(Opcodes.ALOAD, index); // Load the object
        mv.visitInsn(Opcodes.MONITORENTER); // Enter the monitor
	}
	
	public void convertExitMonitorStmt(JExitMonitorStmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper, Map<BasicBlock<?>, Label> blockLabels, SootMethod method) {
		int index = indexMapper.getLocalIndex(stmt.getOp());
			mv.visitVarInsn(Opcodes.ALOAD, index); // Load the object
	        mv.visitInsn(Opcodes.MONITOREXIT); // Exit the monitor
	}
}
