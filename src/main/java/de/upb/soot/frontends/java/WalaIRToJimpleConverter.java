/*
 * @author Linghui Luo
 * @version 1.0
 */
package de.upb.soot.frontends.java;

import de.upb.soot.core.Body;
import de.upb.soot.core.Modifier;
import de.upb.soot.core.SootClass;
import de.upb.soot.core.SootField;
import de.upb.soot.core.SootMethod;
import de.upb.soot.jimple.Jimple;
import de.upb.soot.jimple.basic.Local;
import de.upb.soot.jimple.basic.LocalGenerator;
import de.upb.soot.jimple.common.stmt.IStmt;
import de.upb.soot.jimple.common.type.ArrayType;
import de.upb.soot.jimple.common.type.BooleanType;
import de.upb.soot.jimple.common.type.ByteType;
import de.upb.soot.jimple.common.type.CharType;
import de.upb.soot.jimple.common.type.DoubleType;
import de.upb.soot.jimple.common.type.FloatType;
import de.upb.soot.jimple.common.type.IntType;
import de.upb.soot.jimple.common.type.LongType;
import de.upb.soot.jimple.common.type.NullType;
import de.upb.soot.jimple.common.type.RefType;
import de.upb.soot.jimple.common.type.ShortType;
import de.upb.soot.jimple.common.type.Type;
import de.upb.soot.jimple.common.type.VoidType;
import de.upb.soot.namespaces.INamespace;
import de.upb.soot.namespaces.JavaSourcePathNamespace;
import de.upb.soot.namespaces.classprovider.AbstractClassSource;
import de.upb.soot.namespaces.classprovider.java.JavaClassSource;
import de.upb.soot.signatures.ClassSignature;
import de.upb.soot.signatures.DefaultSignatureFactory;
import de.upb.soot.views.IView;
import de.upb.soot.views.JavaView;

import com.ibm.wala.cast.java.ssa.AstJavaInvokeInstruction;
import com.ibm.wala.cast.loader.AstClass;
import com.ibm.wala.cast.loader.AstField;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.AstMethod.DebuggingInformation;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cfg.AbstractCFG;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeClass;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.intset.FixedSizeBitVector;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Converter which converts WALA IR to jimple.
 * 
 * @author Linghui Luo created on 17.09.18
 *
 */
public class WalaIRToJimpleConverter {
  private IView view;
  private INamespace srcNamespace;
  private HashMap<String, Integer> clsWithInnerCls;
  private HashMap<String, String> walaToSootNameTable;

  public WalaIRToJimpleConverter(String sourceDirPath) {
    srcNamespace = new JavaSourcePathNamespace(sourceDirPath);
    view = new JavaView(null);
    clsWithInnerCls = new HashMap<>();
    walaToSootNameTable = new HashMap<>();
  }

  /**
   * Convert a wala {@link AstClass} to {@link SootClass}.
   * 
   * @param walaClass
   * @return A SootClass converted from walaClass
   */
  public SootClass convertClass(AstClass walaClass) {
    AbstractClassSource classSource = createClassSource(walaClass);
    SootClass sootClass = new SootClass(view, classSource, converModifiers(walaClass));
    view.addSootClass(sootClass);
    sootClass.setApplicationClass();
    // set super class
    IClass sc = walaClass.getSuperclass();
    if (sc != null) {
      SootClass superClass = null;
      if (sc instanceof AstClass) {
        superClass = convertClass((AstClass) sc);
      } else if (sc instanceof ShrikeClass) {
        superClass = convertClass((ShrikeClass) sc);
      }
      if (superClass != null) {
        sootClass.setSuperclass(superClass);
      }
    }

    // convert fields
    Set<IField> fields = HashSetFactory.make(walaClass.getDeclaredInstanceFields());
    fields.addAll(walaClass.getDeclaredStaticFields());
    for (IField walaField : fields) {
      SootField sootField = convertField((AstField) walaField);
      if (sootClass.getFieldUnsafe(sootField.getName(), sootField.getType()) == null) {
        sootClass.addField(sootField);
      }
    }
    // convert methods
    for (IMethod walaMethod : walaClass.getDeclaredMethods()) {
      if (!walaMethod.isAbstract()) {
        convertMethod(sootClass, (AstMethod) walaMethod);
      }
    }
    // add source position
    Position position = walaClass.getSourcePosition();
    sootClass.setPosition(position);
    return sootClass;
  }

  /**
   * Create a dummy {@link SootClass} for walaClass read from wala's byte code front-end. This is used for java library
   * classes used as super classes of application classes.
   * 
   * @param walaClass
   * @return
   */
  private SootClass convertClass(ShrikeClass walaClass) {
    String fullyQualifiedClassName = convertClassNameFromWala(walaClass.getName().toString());
    ClassSignature classSignature = new DefaultSignatureFactory() {
    }.getClassSignature(fullyQualifiedClassName);
    SootClass cl = new SootClass(view, classSignature);
    return cl;
  }

  /**
   * Create a {@link JavaClassSource} object for the given walaClass.
   * 
   * @param walaClass
   * @return
   */
  public AbstractClassSource createClassSource(IClass walaClass) {
    if (walaClass instanceof AstClass) {
      AstClass cl = (AstClass) walaClass;
      String fullyQualifiedClassName = convertClassNameFromWala(walaClass.getName().toString());
      ClassSignature classSignature = new DefaultSignatureFactory() {
      }.getClassSignature(fullyQualifiedClassName);
      URL url = cl.getSourceURL();
      Path sourcePath = Paths.get(url.getPath());
      return new JavaClassSource(srcNamespace, sourcePath, classSignature);
    }
    if (walaClass instanceof ShrikeClass) {
      ShrikeClass cl = (ShrikeClass) walaClass;
      System.out.println(cl.getSourceFileName());
    }
    return null;
  }

  /**
   * Convert a wala {@link AstField} to {@link SootField}.
   * 
   * @param walaField
   * @return A SootField object converted from walaField.
   */
  public SootField convertField(AstField walaField) {
    Type type = convertType(walaField.getFieldTypeReference());
    walaField.isFinal();
    String name = walaField.getName().toString();
    EnumSet<Modifier> modifiers = convertModifiers(walaField);
    SootField sootField = new SootField(view, name, type, modifiers);
    return sootField;
  }

  /**
   * Convert a wala {@link AstMethod} to {@link SootMethod} and add it into the given sootClass.
   *
   * @param sootClass
   *          the SootClass which should contain the converted SootMethod
   * @param walaMethod
   *          the walMethod to be converted
   */
  public SootMethod convertMethod(SootClass sootClass, AstMethod walaMethod) {
    // create SootMethond instance
    String name = walaMethod.getName().toString();
    List<Type> paraTypes = new ArrayList<>();
    for (int i = 0; i < walaMethod.getNumberOfParameters(); i++) {
      Type paraType = convertType(walaMethod.getParameterType(i));
      paraTypes.add(paraType);
    }
    Type returnType = convertType(walaMethod.getReturnType());
    EnumSet<Modifier> modifier = convertModifiers(walaMethod);

    List<SootClass> thrownExceptions = new ArrayList<>();
    try {
      for (TypeReference exception : walaMethod.getDeclaredExceptions()) {
        String exceptionName = convertClassNameFromWala(exception.getName().toString());
        if (!view.getSootClass(new DefaultSignatureFactory() {
        }.getClassSignature(exceptionName)).isPresent()) {
          // create exception class if it doesn't exist yet in the view.
          SootClass exceptionClass = new SootClass(view, new DefaultSignatureFactory().getClassSignature(exceptionName));
          view.addSootClass(exceptionClass);
          thrownExceptions.add(exceptionClass);
        }
      }
    } catch (UnsupportedOperationException e) {
      e.printStackTrace();
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
    }
    SootMethod sootMethod = new SootMethod(view, name, paraTypes, returnType, modifier, thrownExceptions);
    sootClass.addMethod(sootMethod);
    // create and set active body of the SootMethod
    Optional<Body> body = createBody(sootMethod, walaMethod);
    if (body.isPresent()) {
      sootMethod.setPhantom(false);
      sootMethod.setActiveBody(body.get());
    } else {
      sootMethod.setPhantom(true);
    }
    // add debug info
    DebuggingInformation debugInfo = walaMethod.debugInfo();
    sootMethod.setDebugInfo(debugInfo);
    return sootMethod;
  }

  public Type convertType(TypeReference type) {
    if (type.isPrimitiveType()) {
      if (type.equals(TypeReference.Boolean)) {
        return BooleanType.getInstance();
      } else if (type.equals(TypeReference.Byte)) {
        return ByteType.getInstance();
      } else if (type.equals(TypeReference.Char)) {
        return CharType.getInstance();
      } else if (type.equals(TypeReference.Short)) {
        return ShortType.getInstance();
      } else if (type.equals(TypeReference.Int)) {
        return IntType.getInstance();
      } else if (type.equals(TypeReference.Long)) {
        return LongType.getInstance();
      } else if (type.equals(TypeReference.Float)) {
        return FloatType.getInstance();
      } else if (type.equals(TypeReference.Double)) {
        return DoubleType.getInstance();
      } else if (type.equals(TypeReference.Void)) {
        return VoidType.getInstance();
      }
    } else if (type.isReferenceType()) {
      if (type.isArrayType()) {
        TypeReference t = type.getInnermostElementType();
        Type baseType = convertType(t);
        int dim = type.getDimensionality();
        return ArrayType.getInstance(baseType, dim);
      } else if (type.isClassType()) {
        if (type.equals(TypeReference.Null)) {
          return NullType.getInstance();
        } else {
          String className = convertClassNameFromWala(type.getName().toString());
          return new RefType(view, className);
        }
      }
    }
    throw new RuntimeException("Unsupported tpye: " + type);
  }

  /**
   * Return all modifiers for the given field.
   * 
   * @param field
   * @return
   */
  public EnumSet<Modifier> convertModifiers(AstField field) {
    EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
    if (field.isFinal()) {
      modifiers.add(Modifier.FINAL);
    }
    if (field.isPrivate()) {
      modifiers.add(Modifier.PRIVATE);
    }
    if (field.isProtected()) {
      modifiers.add(Modifier.PROTECTED);
    }
    if (field.isPublic()) {
      modifiers.add(Modifier.PUBLIC);
    }
    if (field.isStatic()) {
      modifiers.add(Modifier.STATIC);
    }
    if (field.isVolatile()) {
      modifiers.add(Modifier.VOLATILE);
    }
    // TODO: TRANSIENT field
    return modifiers;
  }

  /**
   * Return all modifiers for the given method.
   * 
   * @param method
   * @return
   */
  public EnumSet<Modifier> convertModifiers(AstMethod method) {
    EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
    if (method.isPrivate()) {
      modifiers.add(Modifier.PRIVATE);
    }
    if (method.isProtected()) {
      modifiers.add(Modifier.PROTECTED);
    }
    if (method.isPublic()) {
      modifiers.add(Modifier.PUBLIC);
    }
    if (method.isStatic()) {
      modifiers.add(Modifier.STATIC);
    }
    if (method.isFinal()) {
      modifiers.add(Modifier.FINAL);
    }
    if (method.isAbstract()) {
      modifiers.add(Modifier.ABSTRACT);
    }
    if (method.isSynchronized()) {
      modifiers.add(Modifier.SYNCHRONIZED);
    }
    if (method.isNative()) {
      modifiers.add(Modifier.NATIVE);
    }
    if (method.isSynthetic()) {
      modifiers.add(Modifier.SYNTHETIC);
    }
    if (method.isBridge()) {
      // TODO: what is this?
    }
    if (method.isInit()) {
      // TODO:
    }
    if (method.isClinit()) {
      // TODO:
    }
    // TODO: strictfp and annotation
    return modifiers;
  }

  public EnumSet<Modifier> converModifiers(AstClass klass) {
    int modif = klass.getModifiers();
    EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
    if (klass.isAbstract()) {
      modifiers.add(Modifier.ABSTRACT);
    }
    if (klass.isPrivate()) {
      modifiers.add(Modifier.PRIVATE);
    }
    if (klass.isSynthetic()) {
      modifiers.add(Modifier.SYNTHETIC);
    }
    if (klass.isPublic()) {
      modifiers.add(Modifier.PUBLIC);
    }
    if (klass.isInterface()) {
      modifiers.add(Modifier.INTERFACE);
    }

    // TODO: final, enum, annotation
    return modifiers;
  }

  private Optional<Body> createBody(SootMethod sootMethod, AstMethod walaMethod) {
    AbstractCFG<?, ?> cfg = walaMethod.cfg();
    if (cfg != null) {
      // convert all wala instructions to jimple statements
      SSAInstruction[] insts = (SSAInstruction[]) cfg.getInstructions();
      if (insts.length > 0) {
        Body body = new Body(sootMethod);
        // set position for body
        DebuggingInformation debugInfo = walaMethod.debugInfo();
        Position bodyPos = debugInfo.getCodeBodyPosition();
        body.setPosition(bodyPos);

        /* Look AsmMethodSource.getBody, see AsmMethodSource.emitLocals(); */

        LocalGenerator localGenerator = new LocalGenerator(body);
        if (!sootMethod.isStatic()) {
          RefType thisType = sootMethod.getDeclaringClass().getType();
          Local thisLocal = localGenerator.generateLocal(sootMethod.getDeclaringClass().getType());
          body.addLocal(thisLocal);
          body.addStmt(Jimple.newIdentityStmt(thisLocal, Jimple.newThisRef(thisType)));
        }

        for (int i = 0; i < walaMethod.getNumberOfParameters(); i++) {
          TypeReference t = walaMethod.getParameterType(i);
          // wala's first parameter can be this reference, so need to check
          if (!t.equals(walaMethod.getDeclaringClass().getReference())) {
            Type type = convertType(t);
            Local paraLocal = localGenerator.generateLocal(type);
            body.addLocal(paraLocal);
            body.addStmt(Jimple.newIdentityStmt(paraLocal, Jimple.newParameterRef(type, i)));
          }
        }

        // TODO 2. convert traps
        // get exceptions which are not caught
        FixedSizeBitVector blocks = cfg.getExceptionalToExit();

        for (SSAInstruction inst : insts) {
          IStmt stmt = convertInstruction(body, localGenerator, inst);
          // set position for each statement
          Position stmtPos = debugInfo.getInstructionPosition(inst.iindex);
          stmt.setPosition(stmtPos);
          body.addStmt(stmt);
        }

        if (walaMethod.getReturnType().equals(TypeReference.Void)) {
          body.addStmt(Jimple.newReturnVoidStmt());
        }
        return Optional.of(body);
      }
    }
    return Optional.empty();
  }

  public IStmt convertInstruction(Body body, LocalGenerator localGenerator, SSAInstruction walaInst) {

    // TODO what are the different types of SSAInstructions
    if (walaInst instanceof SSAConditionalBranchInstruction) {

    } else if (walaInst instanceof SSAGotoInstruction) {

    } else if (walaInst instanceof SSAReturnInstruction) {

    } else if (walaInst instanceof SSAThrowInstruction) {

    } else if (walaInst instanceof SSASwitchInstruction) {

    } else if (walaInst instanceof AstJavaInvokeInstruction) {
      AstJavaInvokeInstruction invokeInst = (AstJavaInvokeInstruction) walaInst;
      if (invokeInst.isSpecial()) {
        if (!body.getMethod().isStatic()) {
          Local base = body.getThisLocal();
          MethodReference target = invokeInst.getDeclaredTarget();
          // view.getSootMethod(target.getSignature());

        }
      }
    } else if (walaInst instanceof SSAFieldAccessInstruction) {
      if (walaInst instanceof SSAGetInstruction) {
        // field read instruction -> assignStmt
      } else if (walaInst instanceof SSAPutInstruction) {
        // field write instruction
      } else {
        throw new RuntimeException("Unsupported instruction type: " + walaInst.getClass().toString());
      }
    } else if (walaInst instanceof SSAArrayLengthInstruction) {

    } else if (walaInst instanceof SSAArrayReferenceInstruction) {
      if (walaInst instanceof SSAArrayLoadInstruction) {

      } else if (walaInst instanceof SSAArrayStoreInstruction) {

      } else {
        throw new RuntimeException("Unsupported instruction type: " + walaInst.getClass().toString());
      }
    } else if (walaInst instanceof SSANewInstruction) {

    } else if (walaInst instanceof SSAComparisonInstruction) {

    } else if (walaInst instanceof SSAConversionInstruction) {

    } else if (walaInst instanceof SSAInstanceofInstruction) {

    } else if (walaInst instanceof SSABinaryOpInstruction) {

    }
    if (walaInst instanceof SSALoadMetadataInstruction) {

    }
    return Jimple.newNopStmt();
  }

  /**
   * Convert className in wala-format to soot-format, e.g., wala-format: Ljava/lang/String -> soot-format: java.lang.String.
   * 
   * @param className
   *          in wala-format
   * @return className in soot.format
   */
  public String convertClassNameFromWala(String className) {
    String cl = className.intern();
    if (walaToSootNameTable.containsKey(cl)) {
      return walaToSootNameTable.get(cl);
    }
    StringBuilder sb = new StringBuilder();
    if (className.startsWith("L")) {
      className = className.substring(1);
      String[] subNames = className.split("/");
      boolean isSpecial = false;
      for (int i = 0; i < subNames.length; i++) {
        String subName = subNames[i];
        if (subName.contains("(") || subName.contains("<")) {
          // handle anonymous or inner classes
          isSpecial = true;
          break;
        }
        if (i != 0) {
          sb.append(".");
        }
        sb.append(subName);
      }
      if (isSpecial) {
        String lastSubName = subNames[subNames.length - 1];
        String[] temp = lastSubName.split(">");
        if (temp.length > 0) {
          String name = temp[temp.length - 1];
          if (!name.contains("$")) {
            // This is aN inner class
            String outClass = sb.toString();
            int count = 1;
            if (this.clsWithInnerCls.containsKey(outClass)) {
              count = this.clsWithInnerCls.get(outClass.toString()) + 1;
            }
            this.clsWithInnerCls.put(outClass, count);
            sb.append(count + "$");
          }
          sb.append(name);
        }
      }
    } else {
      throw new RuntimeException("Can not convert WALA class name: " + className);
    }
    String ret = sb.toString();
    walaToSootNameTable.put(cl, ret);
    return ret;
  }

  /**
   * Convert className in soot-format to wala-format, e.g.,soot-format: java.lang.String.-> wala-format: Ljava/lang/String
   * 
   * @param signature
   * @return
   */
  public String convertClassNameFromSoot(String signature) {
    StringBuilder sb = new StringBuilder();
    sb.append("L");
    String[] subNames = signature.split("\\.");
    for (int i = 0; i < subNames.length; i++) {
      sb.append(subNames[i]);
      if (i != subNames.length - 1) {
        sb.append("/");
      }
    }
    return sb.toString();
  }
}
