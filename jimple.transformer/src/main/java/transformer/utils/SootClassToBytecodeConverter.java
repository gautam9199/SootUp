package transformer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.checkerframework.checker.units.qual.m;
import org.eclipse.jdt.internal.compiler.codegen.IntegerCache;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sootup.core.graph.BasicBlock;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.constant.BooleanConstant;
import sootup.core.jimple.common.constant.ClassConstant;
import sootup.core.jimple.common.constant.DoubleConstant;
import sootup.core.jimple.common.constant.EnumConstant;
import sootup.core.jimple.common.constant.FloatConstant;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.constant.LongConstant;
import sootup.core.jimple.common.constant.NullConstant;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.ref.JThisRef;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.JGotoStmt;
import sootup.core.jimple.common.stmt.JIdentityStmt;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.common.stmt.JInvokeStmt;
import sootup.core.jimple.common.stmt.JNopStmt;
import sootup.core.jimple.common.stmt.JReturnStmt;
import sootup.core.jimple.common.stmt.JReturnVoidStmt;
import sootup.core.jimple.common.stmt.JThrowStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.javabytecode.stmt.JEnterMonitorStmt;
import sootup.core.jimple.javabytecode.stmt.JExitMonitorStmt;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;
import sootup.core.model.Body;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;
import sootup.java.bytecode.frontend.conversion.AsmUtil;
import sootup.java.core.AnnotationUsage;

public class SootClassToBytecodeConverter {
    static Logger logger = LoggerFactory.getLogger(SootClassToBytecodeConverter.class);

    public byte[] convert(SootClass sootClass) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        String className = sootClass.getName().replace('.', '/');
        String superClassName = sootClass.getSuperclass()
                .map(superClass -> superClass.getFullyQualifiedName().replace('.', '/'))
                .orElse("java/lang/Object");

        String[] interfaces = sootClass.getInterfaces().stream()
                .map(ClassType::getFullyQualifiedName)
                .map(name -> name.replace('.', '/'))
                .toArray(String[]::new);

        int accessFlags = convertClassModifiers(sootClass.getModifiers());
        cw.visit(Opcodes.V11, accessFlags , className, null, superClassName, interfaces);
       

        // Convert fields
        logger.info("Converting Fields...");
       // sootClass.getFields().forEach(field -> new SootFieldConverter().convertField(field, cw));

        // Convert methods
        logger.info("Converting Methods..."+ sootClass.getMethods());
        for (SootMethod method : sootClass.getMethods()) {
        	 System.out.println("Method name::" + method.getName());
            if (!method.isConcrete()) {
            	convertNonConcreteMethod(method,cw);
               
            }else {
            	 convertMethod(method, cw);
            }
           
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void convertMethod(SootMethod method, ClassWriter cw) {
        System.out.println("Method name::" + method.getName());
        String methodName = method.getSignature().getSubSignature().getName();
        String methodDescriptor = getMethodDescriptor(method);
        MethodVisitor mv = cw.visitMethod(convertClassModifiers(method.getModifiers()), methodName, methodDescriptor, null, getExceptions(method));

        boolean isStatic = method.isStatic();
        boolean isConstructor = methodName.equals("<init>");
        mv.visitCode();

        // Store label mapping for each block
        Map<BasicBlock<?>, Label> blockLabels = new HashMap<>();
        
        
       
        List<? extends BasicBlock<?>> blocks = method.getBody().getStmtGraph().getBlocksSorted();
      
        blocks =  method.getBody().getStmtGraph().getBlocks().stream().collect(Collectors.toList());
        blocks = generateBlockChain(method.getBody().getStmtGraph(), method);
        
        
        List<Local> locals = method.getBody().getLocals().stream().collect(Collectors.toList());
        List<LValue> definedLocals = method.getBody().getDefs().stream().collect(Collectors.toList());
        Collections.reverse(definedLocals);
        System.out.println("Locals:::"+definedLocals);
        LocalIndexMapper indexMapper = new LocalIndexMapper(isConstructor, isStatic, method, definedLocals, locals);
        Stmt stmtToRemove = isStatic ? method.getBody().getStmts().get(0) : null;


        System.out.println("PARAMETERLOCALS:::"+method.getBody().getParameterLocals().toString());

        if (!blocks.isEmpty()) {
            // Assign labels to each block, including exceptional blocks
            assignLabelsToBlocks(blockLabels, blocks);
           // assignLabelsToExceptionalSuccessors(blocks, blockLabels);

            // Process normal blocks
            for (BasicBlock<?> block : blocks) {
            	System.out.println("CurrentBlock:::"+block);
            	System.out.println("NRMSuccessor:::"+block.getSuccessors());
            	System.out.println("EXPSuccesor:::"+block.getExceptionalSuccessors());

            	
                Label blockLabel = blockLabels.get(block);

                mv.visitLabel(blockLabel);
                int lineNo = block.getHead().getPositionInfo().getStmtPosition().getFirstLine()-1;
                mv.visitLineNumber(lineNo, blockLabel);

                
                	indexMapper.setExceptionBlock(isExceptionalBlock(listExceptionalBlocks(blocks), block));
                
                	processBlockStatements(block, mv, indexMapper, blockLabels, stmtToRemove, method, isConstructor, isStatic);

     
                    indexMapper.setExceptionBlock(Boolean.FALSE);


            }

            StmtGraph<?> graph = method.getBody().getStmtGraph();

            graph.buildTraps().forEach((trap)->{
            	
            	
            	Label startLabel = blockLabels.get(graph.getBlockOf(trap.getBeginStmt()));
            	Label endLabel = blockLabels.get(graph.getBlockOf(trap.getEndStmt()));
            	Label handlerLabel = blockLabels.get(graph.getBlockOf(trap.getHandlerStmt()));
            	
            	
            	
            	//if(!startLabel.equals(endLabel) && !startLabel.equals(handlerLabel) && !handlerLabel.equals(startLabel)) {
            		System.out.println("Traps::::"+ trap.toString());
            	 mv.visitTryCatchBlock(startLabel,endLabel, handlerLabel,trap.getExceptionType().getFullyQualifiedName().replace('.', '/'));
            	//}
            });


            logger.info("Local variables mapped: " + indexMapper.getAllMappedIndices());
        }

        try {
            mv.visitMaxs(0, 0); // Let ASM compute max stack and locals automatically
        } catch (Exception e) {
            if(e instanceof IndexOutOfBoundsException){
                System.out.println("Exception during transformation of"+methodName);
                System.out.println("Exception "+e.toString());
            }
        }

        mv.visitEnd();
    }

    private void assignLabelsToBlocks(Map<BasicBlock<?>, Label> blockLabels, List<? extends BasicBlock<?>> blocks) {
        for (BasicBlock<?> block : blocks) {
            blockLabels.put(block, new Label());
        }
    }

    private void assignLabelsToExceptionalSuccessors(List<? extends BasicBlock<?>> blocks, Map<BasicBlock<?>, Label> blockLabels) {
        for (BasicBlock<?> block : blocks) {
            Map<ClassType, BasicBlock<?>> exceptionalSuccessors = (Map<ClassType, BasicBlock<?>>) block.getExceptionalSuccessors();
            if(!exceptionalSuccessors.isEmpty()) {
            	 for (BasicBlock<?> exceptionBlock : exceptionalSuccessors.values()) {
                     blockLabels.putIfAbsent(exceptionBlock, new Label());
                     if(!exceptionBlock.getSuccessors().isEmpty()) {
                    	 assignLabelsToBlocks(blockLabels, exceptionBlock.getSuccessors());
                     }
                 }
            }

        }
    }

    private void processBlockStatements(BasicBlock<?> block, MethodVisitor mv, LocalIndexMapper indexMapper, Map<BasicBlock<?>, Label> blockLabels, Stmt stmtToRemove, SootMethod method, boolean isConstructor, boolean isStatic) {
        // Process each statement in the block
        for (Stmt stmt : block.getStmts()) {
            logger.info("Current Stmt::" + stmt.toString() + "  Type: " + stmt.getClass());

            if (stmt.equals(stmtToRemove) && isStatic && stmt instanceof JIdentityStmt && ((JIdentityStmt) stmt).getRightOp() instanceof JThisRef) {
                continue; // Skip the first identity statement if it's for static method and this ref
            }

            processStmt(stmt, mv, indexMapper, blockLabels, method, isConstructor, isStatic);
        }
    }

    private void processStmt(Stmt stmt, MethodVisitor mv, LocalIndexMapper indexMapper, Map<BasicBlock<?>, Label> blockLabels, SootMethod method, boolean isConstructor, boolean isStatic) {


        if (stmt instanceof JAssignStmt) {
            new AssignStatementConverter().convertAssignStmt((JAssignStmt) stmt, mv, indexMapper);
        } else if (stmt instanceof JInvokeStmt) {
            new InvokeStatementConverter().convertInvokeStmt((JInvokeStmt) stmt, mv, indexMapper, isConstructor, isStatic);
        } else if (stmt instanceof JIfStmt) {
            processIfStmt((JIfStmt) stmt, mv, blockLabels, method, indexMapper);
        } else if (stmt instanceof JGotoStmt) {
            processGotoStmt((JGotoStmt) stmt, mv, blockLabels, method);
        } else if (stmt instanceof JIdentityStmt) {

          new IdentityStatementConverter().convertIdentityStmt((JIdentityStmt) stmt, mv, indexMapper, method);
      } else if (stmt instanceof JReturnStmt) {
          new ReturnStatementConverter().convertReturnStmt((JReturnStmt) stmt, mv, indexMapper);
      } else if (stmt instanceof JThrowStmt) {

          new ThrowStatementConverter().convertThrowStmt((JThrowStmt) stmt, mv, indexMapper);
      } else if (stmt instanceof JReturnVoidStmt) {
          new ReturnStatementConverter().convertReturnVoidStmt((JReturnVoidStmt) stmt, mv);
      } else if (stmt instanceof JNopStmt) {
          new NopStatementConverter().convertNopStmt((JNopStmt) stmt, mv, indexMapper);
      } else if (stmt instanceof JSwitchStmt) {
    	  new SwitchStatementConverter().convertSwitchStmt((JSwitchStmt) stmt, mv, indexMapper, blockLabels, method);
      } else if(stmt instanceof JEnterMonitorStmt) {
    	new MonitorStatementConverter().convertEnterMonitorStmt((JEnterMonitorStmt)stmt, mv, indexMapper, blockLabels, method);
      } else if (stmt instanceof JExitMonitorStmt) {
    	  new MonitorStatementConverter().convertExitMonitorStmt((JExitMonitorStmt)stmt, mv, indexMapper, blockLabels, method);
      }
      else {
          logger.info("Unsupported statement type: " + stmt.getClass().getName());
          throw new UnsupportedOperationException("Unsupported statement type: " + stmt.getClass().getName());
      }
    }

    private void processIfStmt(JIfStmt stmt, MethodVisitor mv, Map<BasicBlock<?>, Label> blockLabels, SootMethod method, LocalIndexMapper indexMapper) {
    	 JIfStmt ifStmt = stmt;
    	  BasicBlock<?> trueBlock = getTrueBlock(new ArrayList<>(blockLabels.keySet()), stmt, method.getBody());
          BasicBlock<?> falseBlock = getFalseBlock(new ArrayList<>(blockLabels.keySet()), stmt);
          Label trueLabel = blockLabels.get(trueBlock);
          Label falseLabel = blockLabels.getOrDefault(falseBlock, new Label());


         // Emit condition code for ifStmt, then jump to trueBlock or falseBlock

         if (falseBlock != null) {
             // Insert jump for false branch
             falseLabel = blockLabels.get(falseBlock);
           //  mv.visitJumpInsn(Opcodes.GOTO, falseLabel);
         }
         new IfStatementConverter().convertIfStmt(ifStmt, mv, indexMapper, trueLabel, falseLabel);


    }

    private void processGotoStmt(JGotoStmt stmt, MethodVisitor mv, Map<BasicBlock<?>, Label> blockLabels, SootMethod method) {
    	System.out.println("GOTO STMT:::"+ stmt.toString());
    	List<Stmt> successors = method.getBody().getStmtGraph().successors(stmt).stream().distinct().collect(Collectors.toList());
    	System.out.println("GOTO STMT SUCCESORS:::"+ successors);
        for (Stmt successor : successors) {
        	if(! (successor instanceof JIfStmt)) {
        		BasicBlock<?> targetBlock = method.getBody().getStmtGraph().getBlockOf(successor);
                
           	 Label targetLabel = blockLabels.get(targetBlock);
                 mv.visitJumpInsn(Opcodes.GOTO, targetLabel);
        	}
            
            
        }
    	


    }

    private void handleExceptionalSuccessors(BasicBlock<?> block, MethodVisitor mv, Map<BasicBlock<?>, Label> blockLabels, LocalIndexMapper indexMapper, SootMethod method, boolean isConstructor, boolean isStatic) {

        Label exceptionLabel = blockLabels.get(block);
        mv.visitLabel(exceptionLabel);
        //mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Exception"});


        // Process the statements in the exceptional successor block
     // Now explicitly process each statement within the exceptional successor block
        for (Stmt stmt : block.getStmts()) {
            logger.info("Exceptional Stmt::" + stmt.toString() + " Type: " + stmt.getClass());
            if(stmt.equals(block.getStmts().get(0)) && stmt instanceof JIdentityStmt) {
            	continue;
            }
            processStmt(stmt, mv, indexMapper, blockLabels, method, isConstructor, isStatic);
        }
        
        if(!block.getSuccessors().isEmpty()) {
        	for(BasicBlock<?> sblock: block.getSuccessors()) {
        				
        			 Label blockLabel = blockLabels.get(sblock);
        			 
        			 mv.visitJumpInsn(Opcodes.GOTO, blockLabel);
        			 
                     mv.visitLabel(blockLabel);
                     int lineNo = sblock.getHead().getPositionInfo().getStmtPosition().getFirstLine();
                     mv.visitLineNumber(lineNo, blockLabel);
                     for (Stmt stmt : sblock.getStmts()) {
                         logger.info("Current Stmt::" + stmt.toString() + "  Type: " + stmt.getClass());

                         processStmt(stmt, mv, indexMapper, blockLabels, method, isConstructor, isStatic);
                     }
                    
        		
        	}
        }

    }


    private static int convertClassModifiers(Set<?> modifiers) {
    	System.out.println("Modifiers:::"+modifiers);
        int asmModifiers = 0;
        for (Object modifier : modifiers) {
            switch (modifier.toString()) {
                case "PUBLIC":
                    asmModifiers |= Opcodes.ACC_PUBLIC;
                    break;
                case "PRIVATE":
                    asmModifiers |= Opcodes.ACC_PRIVATE;
                    break;
                case "PROTECTED":
                    asmModifiers |= Opcodes.ACC_PROTECTED;
                    break;
                case "STATIC":
                    asmModifiers |= Opcodes.ACC_STATIC;
                    break;
                case "FINAL":
                    asmModifiers |= Opcodes.ACC_FINAL;
                    break;
                case "ABSTRACT":
                    asmModifiers |= Opcodes.ACC_ABSTRACT;
                    break;
                case "INTERFACE":
                    asmModifiers |= Opcodes.ACC_INTERFACE;
                    break;
                case "SUPER":
                	asmModifiers |= Opcodes.ACC_SUPER;
                	break;
                case "SYNCHRONIZED":
                	asmModifiers |= Opcodes.ACC_SYNCHRONIZED;
                	break;
                case "ANNOTATION":
                	asmModifiers |= Opcodes.ACC_ANNOTATION;
                	break;
                // Handle other modifiers as necessary
            }
        }
        return asmModifiers;
    }

    private static String getMethodDescriptor(SootMethod sootMethod) {
        StringBuilder descriptor = new StringBuilder("(");
        sootMethod.getParameterTypes().forEach(paramType -> descriptor.append(getTypeDescriptor(paramType)));
        descriptor.append(")").append(getTypeDescriptor(sootMethod.getReturnType()));
        return descriptor.toString();
    }

    private static String getTypeDescriptor(Type type) {
        if (type instanceof PrimitiveType) {
            return getPrimitiveDescriptor(type);
        } else if (type instanceof ArrayType) {
            return "[" + getTypeDescriptor(((ArrayType) type).getBaseType());
        } else if (type instanceof ClassType) {
            return "L" + ((ClassType) type).getFullyQualifiedName().replace('.', '/') + ";";
        } else if (type.toString().equals("void")) {
            return "V";
        } else {
            logger.info("Unsupported type: " + type);
            throw new UnsupportedOperationException("Unsupported type: " + type);
        }
    }

    private static String getPrimitiveDescriptor(Type type) {
        switch (type.toString()) {
            case "int":
                return "I";
            case "boolean":
                return "Z";
            case "byte":
                return "B";
            case "char":
                return "C";
            case "short":
                return "S";
            case "long":
                return "J";
            case "float":
                return "F";
            case "double":
                return "D";
            case "void":
                return "V";
            default:
                logger.info("Unsupported primitive type: " + type);
                throw new UnsupportedOperationException("Unsupported primitive type: " + type);
        }
    }

    private static String[] getExceptions(SootMethod sootMethod) {
        List<ClassType> exceptions = sootMethod.getExceptionSignatures();
        String[] exceptionArray = new String[exceptions.size()];
        for (int i = 0; i < exceptions.size(); i++) {
            exceptionArray[i] = exceptions.get(i).getFullyQualifiedName().replace('.', '/');
        }
        return exceptionArray;
    }

    // Helper methods to get the appropriate blocks for jumps
    private BasicBlock<?> getTrueBlock(List<BasicBlock> blocks, JIfStmt ifStmt, Body body) {
        // Get the target statements of the if statement
        List<Stmt> targetStmts = ifStmt.getTargetStmts(body);

        if (targetStmts == null ) { //|| targetStmts.size() <= JIfStmt.TRUE_BRANCH_IDX
            throw new IllegalStateException("True branch target not found for JIfStmt");
        }

        // The true branch corresponds to index 1 (TRUE_BRANCH_IDX) in the target list
        Stmt trueTargetStmt = targetStmts.get(JIfStmt.TRUE_BRANCH_IDX-1);

        // Find the block corresponding to this true target statement
        BasicBlock<?> trueBlock = findBlockByStmt(blocks, trueTargetStmt);
        if (trueBlock == null) {
            throw new IllegalStateException("True branch target block not found for JIfStmt");
        }

        return trueBlock;
    }




    private BasicBlock<?> getFalseBlock(List<BasicBlock> blocks, JIfStmt ifStmt) {
        // The false branch is usually the next block in sequence after the "true" one
        // Modify this to match your CFG structure.
        for (BasicBlock<?> block : blocks) {
            if (block.getStmts().contains(ifStmt)) {
                int currentIndex = blocks.indexOf(block);
                return currentIndex + 1 < blocks.size() ? blocks.get(currentIndex + 1) : null;
            }
        }
        return null;
    }

    private BasicBlock<?> getTargetBlock(List<BasicBlock> blocks, JGotoStmt gotoStmt, Body body) {
        List<Stmt> targetStmts = gotoStmt.getTargetStmts(body);
        if (!targetStmts.isEmpty()) {
            return findBlockByStmt(blocks, targetStmts.get(JGotoStmt.BRANCH_IDX));
        }
        throw new IllegalStateException("Target block not found for JGotoStmt");
    }

    private BasicBlock<?> findBlockByStmt(List<BasicBlock> blocks, Stmt targetStmt) {
        // This helper method finds the block containing a specific target statement
        for (BasicBlock<?> block : blocks) {
            if (block.getStmts().contains(targetStmt)) {
                return block;
            }
        }
        return null;
    }


    private void convertNonConcreteMethod(SootMethod method, ClassWriter cw) {
        System.out.println("Non-concrete Method name::" + method.getName());

        // Generate method descriptor
        String methodName = method.getSignature().getSubSignature().getName();
        String methodDescriptor = getMethodDescriptor(method);
        String[] exceptions = getExceptions(method);
        int methodModifiers = convertClassModifiers(method.getModifiers());
        if(method.isNative()) {
        	 methodModifiers |= Opcodes.ACC_NATIVE;
        } 
   
        // Visit the method without generating any code
        MethodVisitor mv = cw.visitMethod(methodModifiers, methodName, methodDescriptor, null, exceptions);
       // System.out.println("Resolve annotation::"+method.getBodySource().resolveAnnotationsDefaultValue());
       // System.out.println("Resolve annotation"+method.getBodySource().resolveAnnotationsDefaultValue().getClass());
     // Check if there is a default value and set it
        
        Object defaultValue = method.getBodySource().resolveAnnotationsDefaultValue();
    
        if (!(defaultValue instanceof NullConstant)) {
            AnnotationVisitor av = mv.visitAnnotationDefault();
            processDefaultValue(av, defaultValue);
            av.visitEnd();
        }

        
        mv.visitEnd(); // No method body is generated for non-concrete methods
    }

    private void processDefaultValue(AnnotationVisitor av, Object defaultValue) {
        if (defaultValue instanceof StringConstant) {
            StringConstant c = (StringConstant) defaultValue;
            av.visit(null, c.getValue());

        } else if (defaultValue instanceof IntConstant) {
            IntConstant c = (IntConstant) defaultValue;
            av.visit(null, c.getValue());

        } else if (defaultValue instanceof FloatConstant) {
            FloatConstant c = (FloatConstant) defaultValue;
            av.visit(null, c.getValue());

        } else if (defaultValue instanceof DoubleConstant) {
            DoubleConstant c = (DoubleConstant) defaultValue;
            av.visit(null, c.getValue());

        } else if (defaultValue instanceof BooleanConstant) {
            BooleanConstant c = (BooleanConstant) defaultValue;
            av.visit(null, c.toString().equals("1") ? Boolean.TRUE : Boolean.FALSE);

        } else if (defaultValue instanceof LongConstant) {
            LongConstant c = (LongConstant) defaultValue;
            av.visit(null, c.getValue());

        } else if (defaultValue instanceof EnumConstant) {
            EnumConstant c = (EnumConstant) defaultValue;
            av.visitEnum(null, getTypeDescriptor(c.getType()), c.getValue());

        } else if (defaultValue instanceof ArrayList) {
            // Handle lists (arrays)
            AnnotationVisitor arrayVisitor = av.visitArray(null);
            for (Object element : (ArrayList<?>) defaultValue) {
                processDefaultValue(arrayVisitor, element);
            }
            arrayVisitor.visitEnd();

        } else if (defaultValue instanceof AnnotationNode) {
            AnnotationNode annotationNode = (AnnotationNode) defaultValue;
            AnnotationVisitor nestedVisitor = av.visitAnnotation(null, annotationNode.desc);
            ((AnnotationNode) AsmUtil.createAnnotationUsage(Collections.singletonList(annotationNode))).accept(nestedVisitor);
            nestedVisitor.visitEnd();

        } else if (defaultValue instanceof ClassConstant) {
            ClassConstant c = (ClassConstant) defaultValue;
            av.visit(null, c.getValue());

        } else if (defaultValue instanceof AnnotationUsage) {
            AnnotationUsage annotationUsage = (AnnotationUsage) defaultValue;
            try {
                // Attempt to get default values with proper initialization

                Map<String, Object> values = annotationUsage.getValues();
                AnnotationVisitor nestedVisitor = av.visitAnnotation(null, 
                    annotationUsage.getAnnotation().getFullyQualifiedName().replace('.', '/'));
                
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    processDefaultValue(nestedVisitor.visitArray(entry.getKey()), entry.getValue());
                }
                nestedVisitor.visitEnd();
            } catch (IllegalArgumentException e) {
                logger.warn("Annotation default values not properly initialized: " + annotationUsage.getAnnotation(), e);
                // Optionally handle or skip if defaults cannot be fetched
            }
        } else {
            throw new UnsupportedOperationException("Unsupported default value type: " + defaultValue.getClass().getName());
        }
    }
    private List<? extends BasicBlock<?>>  generateBlockChain(StmtGraph<?> graph, SootMethod method){
    	
    	List<BasicBlock<?>> blocks = new ArrayList<BasicBlock<?>>();
    	System.out.println("GRAPH:::PRINT::"+graph);
    	graph.validateStmtConnectionsInGraph();
    	method.getBody().getStmts().forEach(s->{
    		BasicBlock<?> blockToBeAdded = graph.getBlockOf(s);
    		if(!blocks.contains(blockToBeAdded)) {
    			blocks.add(blockToBeAdded);
    		}
    	});
    	
    	System.out.println("GENERATED BLOCKCHAIN:::"+blocks);
    	 	
    	return blocks;
    }
    
    private List<? extends BasicBlock<?>> listExceptionalBlocks(List<? extends BasicBlock<?>> allBlocks){
    	List<BasicBlock<?>> exceptionalBlocks = new ArrayList<BasicBlock<?>>();
    	
    	allBlocks.forEach(b->{
    		if(!b.getExceptionalPredecessors().isEmpty()) {
    			exceptionalBlocks.addAll(b.getExceptionalSuccessors().values().stream().collect(Collectors.toList()));
    		}
    	});
    	
    	return exceptionalBlocks;
    }
    
    private boolean isExceptionalBlock(List<? extends BasicBlock<?>> exceptionalBlocks,BasicBlock<?> currentBlock ) {
    	Boolean flag  = exceptionalBlocks.contains(currentBlock);
    	return flag;
    }
}
