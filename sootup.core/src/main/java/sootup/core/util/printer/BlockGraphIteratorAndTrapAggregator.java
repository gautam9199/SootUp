package sootup.core.util.printer;

import sootup.core.graph.BasicBlock;
import sootup.core.graph.BlockGraphIterator;
import sootup.core.graph.MutableBasicBlockImpl;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.basic.StmtPositionInfo;
import sootup.core.jimple.basic.Trap;
import sootup.core.jimple.common.stmt.JNopStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.types.ClassType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Iterates over the Blocks and collects/aggregates Trap information
 * It is used to collect and aggregate traps for serializing Jimple in the JimplePrinter
 */
class BlockGraphIteratorAndTrapAggregator extends BlockGraphIterator {

    @Nonnull
    private final List<Trap> collectedTraps = new ArrayList<>();

    Map<ClassType, Stmt> activeTraps = new HashMap<>();
    BasicBlock<?> lastIteratedBlock; // dummy value to remove n-1 unnecessary null-checks
    JNopStmt lastStmt = null;

    /*
     * @param dummyBlock is just an empty instantiation of type V - as neither BasicBlock nor V instantiable we need a concrete object from the using subclass itclass.
     * */
    public BlockGraphIteratorAndTrapAggregator(StmtGraph stmtGraph) {
        super(stmtGraph);
        lastIteratedBlock = new MutableBasicBlockImpl();
    }

    @Nullable
    public JNopStmt getLastStmt() {
        return lastStmt;
    }

    @Nonnull
    @Override
    public BasicBlock<?> next() {
        final BasicBlock<?> block = super.next();

        final Map<? extends ClassType, ? extends BasicBlock<?>> currentBlocksExceptions =
                block.getExceptionalSuccessors();
        final Map<? extends ClassType, ? extends BasicBlock<?>> lastBlocksExceptions =
                lastIteratedBlock.getExceptionalSuccessors();

        // former trap info is not in the current blocks info -&gt; add it to the trap collection
        lastBlocksExceptions.forEach(
                (type, trapHandlerBlock) -> {
                    if (trapHandlerBlock != block.getExceptionalSuccessors().get(type)) {
                        final Stmt trapBeginStmt = activeTraps.remove(type);
                        if (trapBeginStmt == null) {
                            throw new IllegalStateException("Trap start for '" + type + "' is not in the Map!");
                        }
                        // trapend is exclusive!
                        collectedTraps.add(
                                new Trap(type, trapBeginStmt, block.getHead(), trapHandlerBlock.getHead()));
                    }
                });

        // is there a new trap in the current block -&gt; add it to currentTraps
        block
                .getExceptionalSuccessors()
                .forEach(
                        (type, trapHandlerBlock) -> {
                            if (trapHandlerBlock != lastBlocksExceptions.get(type)) {
                                activeTraps.put(type, block.getHead());
                            }
                        });

        lastIteratedBlock = block;
        return block;
    }

    /**
     * for jimple serialization - this info contains only valid/useful information if all stmts are
     * iterated i.e. hasNext() == false!
     *
     * @return List of Traps
     */
    public List<Trap> getTraps() {

        if (hasNext()) {
            throw new IllegalStateException("Iterator needs to be iterated completely!");
        }

        // check for dangling traps that are not collected as the endStmt was not visited.
        if (!activeTraps.isEmpty()) {
            lastStmt = new JNopStmt(StmtPositionInfo.getNoStmtPositionInfo());
//            throw new IllegalArgumentException(
//                    "Invalid StmtGraph. A Trap is not created as a traps endStmt was not visited during the iteration of all Stmts.");
            activeTraps.forEach((type, beginning) -> collectedTraps.add(new Trap(type, beginning, lastStmt, lastIteratedBlock.getExceptionalSuccessors().get(type).getHead())));
        }
        return collectedTraps;
    }
}
