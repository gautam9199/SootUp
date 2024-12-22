package transformer.utils;

import java.util.List;
import java.util.Map;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import sootup.core.graph.BasicBlock;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;
import sootup.core.model.SootMethod;

public class SwitchStatementConverter {

	public void convertSwitchStmt(JSwitchStmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper, Map<BasicBlock<?>, Label> blockLabels, SootMethod method) {
	    // Get the key used for the switch
	    Immediate key = stmt.getKey();
	    int keyIndex = indexMapper.getLocalIndex((Immediate) key);

	    // Load the key onto the stack
	    mv.visitVarInsn(Opcodes.ILOAD, keyIndex);

	    // Check if it's a TableSwitch or LookupSwitch
	    if (stmt.isTableSwitch()) {
	        handleTableSwitch(stmt, mv, blockLabels, method);
	    } else {
	        handleLookupSwitch(stmt, mv, blockLabels, method);
	    }
	}

	private void handleTableSwitch(JSwitchStmt stmt, MethodVisitor mv, Map<BasicBlock<?>, Label> blockLabels, SootMethod method) {
	    // Determine the range of keys
	    List<IntConstant> values = stmt.getValues();
	    int minKey = values.get(0).getValue();
	    int maxKey = values.get(values.size() - 1).getValue();

	    // Prepare labels
	    Label[] caseLabels = new Label[values.size()];
	    for (int i = 0; i < values.size(); i++) {
	        caseLabels[i] = blockLabels.get(method.getBody().getStmtGraph().getBlockOf(method.getBody().getBranchTargetsOf(stmt).get(i)));
	    }

	    // Default label
	    Label defaultLabel =blockLabels.get(method.getBody().getStmtGraph().getBlockOf(method.getBody().getBranchTargetsOf(stmt).get(values.size())));

	    // Emit a TableSwitch instruction
	    mv.visitTableSwitchInsn(minKey, maxKey, defaultLabel, caseLabels);
	}

	private void handleLookupSwitch(JSwitchStmt stmt, MethodVisitor mv, Map<BasicBlock<?>, Label> blockLabels, SootMethod method) {
	    // Extract keys and their corresponding targets
	    List<IntConstant> values = stmt.getValues();
	    int[] keys = values.stream().mapToInt(IntConstant::getValue).toArray();

	    Label[] caseLabels = new Label[values.size()];
	    for (int i = 0; i < values.size(); i++) {
	        caseLabels[i] = blockLabels.get(method.getBody().getStmtGraph().getBlockOf(method.getBody().getBranchTargetsOf(stmt).get(i)));
	    }

	    // Default label
	    Label defaultLabel = blockLabels.get(method.getBody().getStmtGraph().getBlockOf(method.getBody().getBranchTargetsOf(stmt).get(values.size())));

	    // Emit a LookupSwitch instruction
	    mv.visitLookupSwitchInsn(defaultLabel, keys, caseLabels);
	}

}
