package referencejimple;

import de.upb.swt.soot.core.DefaultIdentifierFactory;
import de.upb.swt.soot.core.frontend.EagerJavaClassSource;
import de.upb.swt.soot.core.frontend.EagerMethodSource;
import de.upb.swt.soot.core.frontend.MethodSource;
import de.upb.swt.soot.core.inputlocation.DefaultSourceTypeSpecifier;
import de.upb.swt.soot.core.inputlocation.EagerInputLocation;
import de.upb.swt.soot.core.jimple.Jimple;
import de.upb.swt.soot.core.jimple.basic.Local;
import de.upb.swt.soot.core.jimple.basic.LocalGenerator;
import de.upb.swt.soot.core.jimple.basic.NoPositionInformation;
import de.upb.swt.soot.core.jimple.basic.PositionInfo;
import de.upb.swt.soot.core.jimple.basic.Trap;
import de.upb.swt.soot.core.jimple.basic.Value;
import de.upb.swt.soot.core.jimple.common.constant.IntConstant;
import de.upb.swt.soot.core.jimple.common.stmt.Stmt;
import de.upb.swt.soot.core.model.Body;
import de.upb.swt.soot.core.model.Modifier;
import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SootField;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.signatures.FieldSignature;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.core.types.JavaClassType;
import de.upb.swt.soot.core.types.PrimitiveType;
import de.upb.swt.soot.core.types.VoidType;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/** @author Markus Schmidt */
public class IdentityStmtTest extends JimpleInstructionsTestBase {

  JavaClassType classSignature;

  @Override
  public void build() {
    DefaultIdentifierFactory dif = DefaultIdentifierFactory.getInstance();

    Path dummyPath = Paths.get(URI.create("file:/C:/nonexistent.java"));

    JavaClassType superClassSignature = dif.getClassType("java.lang.Object");
    classSignature = dif.getClassType("de.upb.soot.instructions.stmt.IdentityStmt");

    Set<SootField> fields = new LinkedHashSet<>();

    // Decl field
    fields.add(
        new SootField(
            dif.getFieldSignature("declProperty", classSignature, PrimitiveType.getInt()),
            EnumSet.noneOf(Modifier.class)));

    FieldSignature initFieldSignature =
        dif.getFieldSignature("initProperty", classSignature, PrimitiveType.getInt());

    // Init field
    fields.add(new SootField(initFieldSignature, EnumSet.noneOf(Modifier.class)));

    Set<SootMethod> methods = new LinkedHashSet<>();

    methods.add(init(initFieldSignature));

    // methods.add( atThis());
    // atParameterPrimitive();
    // atParameterNonPrimitive();
    // atExceptionThrow();
    // atExceptionThrowAndCatch();

    EagerJavaClassSource javaClassSource =
        new EagerJavaClassSource(
            new EagerInputLocation(DefaultSourceTypeSpecifier.getInstance()),
            dummyPath,
            dif.getClassType("de.upb.soot.instructions.stmt.IdentityStmt"),
            superClassSignature,
            new HashSet<>(),
            null,
            fields,
            methods,
            new NoPositionInformation(),
            EnumSet.of(Modifier.PUBLIC));

    sootClass = new SootClass(javaClassSource);
  }

  SootMethod init(@Nonnull FieldSignature initFieldSignature) {
    PositionInfo nop = PositionInfo.createNoPositionInfo();
    DefaultIdentifierFactory dif = DefaultIdentifierFactory.getInstance();
    LocalGenerator generator = new LocalGenerator(new HashSet<>());

    MethodSignature methodSignature =
        dif.getMethodSignature(
            "<init>", classSignature, VoidType.getInstance().toString(), Arrays.asList(""));

    HashSet<Local> locals = new HashSet<>();
    List<Trap> traps = new LinkedList<>();
    List<Stmt> stmts = new LinkedList<>();

    JavaClassType typeSignature = dif.getClassType("de.upb.soot.instructions.stmt.IdentityStmt");
    //    new RefType(view, dsm.getTypeSignature("de.upb.soot.instructions.stmt.IdentityStmt"));
    //    RefType type = RefType.getInstance("de.upb.soot.instructions.stmt.IdentityStmt");

    Local r0 = generator.generateField(typeSignature);
    stmts.add(Jimple.newIdentityStmt(r0, Jimple.newThisRef(typeSignature), nop));

    // TODO: how to add expr to body?
    // add(Jimple.newSpecialInvokeExpr( r0 , currentMethod ));

    Value value = IntConstant.getInstance(42);
    stmts.add(Jimple.newAssignStmt(Jimple.newInstanceFieldRef(r0, initFieldSignature), value, nop));

    stmts.add(Jimple.newReturnVoidStmt(nop));

    Body body = new Body(locals, traps, stmts, new NoPositionInformation());
    MethodSource methodSource = new EagerMethodSource(methodSignature, body);

    return new SootMethod(
        methodSource, methodSignature, EnumSet.of(Modifier.PUBLIC), Collections.emptyList());
  }

  /*
   *
   * SootMethod atThis(){
   *
   * Remove dsm = new Remove();
   *
   * SootMethod currentMethod = new SootMethod(view, "atThis", Arrays.asList(new Type[]{}), VoidType.INSTANCE,
   * EnumSet.of(Modifier.PUBLIC) ); sootClass.addMethod(currentMethod);
   *
   * SootMethod println = new SootMethod(view, "println", Arrays.asList(new Type[]{ IntType.INSTANCE} ), VoidType.INSTANCE,
   * EnumSet.of(Modifier.PUBLIC, Modifier.STATIC) ); println.setDeclaringClass( new SootClass(view,
   * dsm.getClassSignature("java.lang.System") , EnumSet.of(Modifier.PUBLIC, Modifier.FINAL)) );
   *
   * Body body = Jimple.newBody(currentMethod); LocalGenerator generator = new LocalGenerator(body);
   *
   * //view.addRefType(new RefType(view, "de.upb.soot.instructions.stmt.IdentityStmt")); RefType type =
   * RefType.getInstance("de.upb.soot.instructions.stmt.IdentityStmt"); // view.addRefType(new RefType(view,
   * "java.io.PrintStream")); RefType printStream = RefType.getInstance("java.io.PrintStream"); // view.addRefType(new
   * RefType(view, "java.lang.System")); RefType system = RefType.getInstance("java.lang.System");
   *
   *
   * Local r0 = generator.generateField( type ); body.addStmt(Jimple.newIdentityStmt( r0 , Jimple.newThisRef(type) ));
   *
   * Local r1 = generator.generateLocal( printStream); SootField out = new SootField(view, "out", system,
   * EnumSet.<Modifier>of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL) );
   *
   *
   * // TODO: expr to body // Jimple.newAssignStmt( r1, Jimple.newStaticField(view, out ) ); // Jimple.newAssignStmt( r1,
   * Jimple.newInstanceFieldRef(r1,Jimple.newStaticField(view, out )) );
   *
   *
   *
   * Local i0 = generator.generateLocal( IntType.INSTANCE); Value value = IntConstant.getInstance( 42 );
   * body.addStmt(Jimple.newAssignStmt( i0, Jimple.newInstanceFieldRef( r0 , declField ) )); // TODO: add to body //
   * Jimple.newVirtualInvokeExpr(r1, println, i0);
   *
   *
   * Local i1 = generator.generateLocal( IntType.INSTANCE); Local i2 = generator.generateLocal( IntType.INSTANCE); Local i3 =
   * generator.generateLocal( IntType.INSTANCE);
   *
   * Local r2 = generator.generateLocal( printStream); Local r3 = generator.generateLocal( printStream); Local r4 =
   * generator.generateLocal( printStream);
   *
   *
   *
   * body.addStmt(Jimple.newReturnVoidStmt() ); currentMethod.setActiveBody(body);
   *
   * }
   *
   * SootMethod atParameterPrimitive(){
   *
   * SootMethod currentMethod = new SootMethod(view, "atParameterPrimitive", Arrays.asList(new Type[]{IntType.INSTANCE,
   * BooleanType.INSTANCE}), VoidType.INSTANCE, EnumSet.of(Modifier.PUBLIC) ); sootClass.addMethod(currentMethod);
   *
   * Body body = Jimple.newBody(currentMethod); LocalGenerator generator = new LocalGenerator(body);
   *
   * RefType type = RefType.getInstance("de.upb.soot.instructions.stmt.IdentityStmt");
   *
   * Local r0 = generator.generateField( type ); body.addStmt(Jimple.newIdentityStmt( r0 , Jimple.newThisRef(type) ));
   *
   * Local i0 = generator.generateField( IntType.INSTANCE); body.addStmt( Jimple.newIdentityStmt( i0, Jimple.newParameterRef(
   * IntType.INSTANCE, 0)) );
   *
   * Local z0 = generator.generateField( BooleanType.INSTANCE); body.addStmt( Jimple.newIdentityStmt( z0,
   * Jimple.newParameterRef( BooleanType.INSTANCE, 1)) );
   *
   *
   * type = RefType.getInstance("java.io.PrintStream"); Local r1 = generator.generateLocal( type ); Local r2 =
   * generator.generateLocal( type );
   *
   *
   *
   *
   * body.addStmt(Jimple.newReturnVoidStmt() ); currentMethod.setActiveBody(body);
   *
   *
   *
   * }
   *
   * SootMethod atParameterNonPrimitive(){
   *
   * SootMethod currentMethod = new SootMethod(view, "atParameterNonPrimitive", Arrays.asList(new Type[]{
   * RefType.getInstance("java.lang.Integer"), RefType.getInstance("java.lang.String"),
   * RefType.getInstance("java.lang.Boolean"), RefType.getInstance("int[]")
   *
   * }), VoidType.INSTANCE, EnumSet.of(Modifier.PUBLIC) ); sootClass.addMethod(currentMethod);
   *
   * Body body = Jimple.newBody(currentMethod); LocalGenerator generator = new LocalGenerator(body);
   *
   * RefType type = RefType.getInstance("de.upb.soot.instructions.stmt.IdentityStmt"); Local r0 = generator.generateField(
   * type ); body.addStmt(Jimple.newIdentityStmt( r0 , Jimple.newThisRef(type) ));
   *
   * type = RefType.getInstance("java.lang.Integer"); Local r1= generator.generateField( type ); body.addStmt(
   * Jimple.newIdentityStmt( r1, Jimple.newParameterRef( type, 0)) );
   *
   * type = RefType.getInstance("java.lang.String"); Local r2 = generator.generateField( type ); body.addStmt(
   * Jimple.newIdentityStmt( r2, Jimple.newParameterRef( type, 1)) );
   *
   * type = RefType.getInstance("java.lang.Boolean"); Local r3 = generator.generateField( type ); body.addStmt(
   * Jimple.newIdentityStmt( r3, Jimple.newParameterRef( type, 2)) );
   *
   * type = RefType.getInstance("int[]"); Local r4 = generator.generateField( type ); body.addStmt( Jimple.newIdentityStmt(
   * r4, Jimple.newParameterRef( type, 3)) );
   *
   *
   *
   *
   *
   * type = RefType.getInstance("java.io.PrintStream"); Local r5 = generator.generateLocal( type ); Local r6 =
   * generator.generateLocal( type ); Local r7 = generator.generateLocal( type ); Local r8 = generator.generateLocal( type );
   *
   *
   * body.addStmt(Jimple.newReturnVoidStmt() ); currentMethod.setActiveBody(body);
   *
   * }
   *
   * SootMethod atExceptionThrow(){
   *
   * SootClass exception = new SootClass(view, new Remove().getClassSignature("java.lang.Exception"));
   * SootMethod currentMethod = new SootMethod(view, "atExceptionThrow", Arrays.asList(new Type[]{}), VoidType.INSTANCE,
   * EnumSet.of(Modifier.PUBLIC) , Arrays.asList(exception) ); sootClass.addMethod(currentMethod);
   *
   * Body body = Jimple.newBody(currentMethod); LocalGenerator generator = new LocalGenerator(body);
   *
   * RefType type = RefType.getInstance("de.upb.soot.instructions.stmt.IdentityStmt"); Local r0 = generator.generateField(
   * type ); body.addStmt(Jimple.newIdentityStmt( r0 , Jimple.newThisRef(type) ));
   *
   * type = RefType.getInstance("java.lang.Exception"); Local r1 = generator.generateLocal( type );
   * body.addStmt(Jimple.newAssignStmt( r1, Jimple.newNewExpr( type)) );
   *
   * // TODO: // specialinvoke $r1.<java.lang.Exception: void <init>(java.lang.String)>("Issue");
   *
   * body.addStmt(Jimple.newThrowStmt(r1) ); body.addStmt(Jimple.newReturnVoidStmt() ); currentMethod.setActiveBody(body);
   *
   *
   * }
   *
   * SootMethod atExceptionThrowAndCatch() {
   *
   * SootMethod currentMethod = new SootMethod(view, "atExceptionThrowAndCatch", Arrays.asList(new Type[]{}),
   * VoidType.INSTANCE, EnumSet.of(Modifier.PUBLIC) ); sootClass.addMethod(currentMethod);
   *
   * Body body = Jimple.newBody(currentMethod); LocalGenerator generator = new LocalGenerator(body);
   *
   * RefType type = RefType.getInstance("de.upb.soot.instructions.stmt.IdentityStmt"); Local r0 = generator.generateField(
   * type ); body.addStmt(Jimple.newIdentityStmt( r0 , Jimple.newThisRef(type) ));
   *
   * type = RefType.getInstance("java.io.PrintStream"); Local r1 = generator.generateLocal( type ); Local r2 =
   * generator.generateLocal( type ); Local r3 = generator.generateLocal( type );
   *
   * Local i0 = generator.generateLocal( IntType.INSTANCE ); Local i1 = generator.generateLocal( IntType.INSTANCE ); Local i2
   * = generator.generateField( IntType.INSTANCE );
   *
   * type = RefType.getInstance("java.lang.Exception"); Local r4 = generator.generateLocal( type );
   * body.addStmt(Jimple.newAssignStmt( r4, Jimple.newNewExpr( type)) );
   *
   * Local r5 = generator.generateLocal( RefType.getInstance("java.io.PrintStream") );
   *
   *
   * /* TODO label1: $r1 = <java.lang.System: java.io.PrintStream out>; virtualinvoke $r1.<java.io.PrintStream: void
   * println(java.lang.String)>("A1"); $i1 = r0.<de.upb.soot.instructions.stmt.IdentityStmt: int declProperty>; $i0 =
   * r0.<de.upb.soot.instructions.stmt.IdentityStmt: int initProperty>; i2 = $i1 * $i0; $r2 = <java.lang.System:
   * java.io.PrintStream out>; virtualinvoke $r2.<java.io.PrintStream: void println(int)>(i2);
   *
   * ...
   *
   * /
   *
   *
   *
   *
   *
   * // body.addStmt(Jimple.newThrowStmt(r1) ); body.addStmt(Jimple.newReturnVoidStmt() ); currentMethod.setActiveBody(body);
   *
   * }
   *
   * SootMethod exceptionMultiple(){
   *
   * }
   *
   * SootMethod exceptionFinally(){
   *
   *
   * }
   *
   */
}
