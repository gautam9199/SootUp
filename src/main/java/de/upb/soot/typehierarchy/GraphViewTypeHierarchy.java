package de.upb.soot.typehierarchy;

import com.google.common.base.Suppliers;
import de.upb.soot.core.AbstractClass;
import de.upb.soot.core.SootClass;
import de.upb.soot.frontends.AbstractClassSource;
import de.upb.soot.frontends.ResolveException;
import de.upb.soot.typehierarchy.GraphViewTypeHierarchy.ScanResult.ClassNode;
import de.upb.soot.typehierarchy.GraphViewTypeHierarchy.ScanResult.InterfaceNode;
import de.upb.soot.typehierarchy.GraphViewTypeHierarchy.ScanResult.Node;
import de.upb.soot.types.JavaClassType;
import de.upb.soot.views.View;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class GraphViewTypeHierarchy implements TypeHierarchy {

  private final Supplier<ScanResult> lazyScanResult = Suppliers.memoize(this::scanView);

  @Nonnull private final View view;

  GraphViewTypeHierarchy(@Nonnull View view) {
    this.view = view;
  }

  @Nonnull
  @Override
  public Set<JavaClassType> implementersOf(@Nonnull JavaClassType interfaceType) {
    ScanResult scanResult = lazyScanResult.get();
    InterfaceNode interfaceNode = scanResult.typeToInterfaceNode.get(interfaceType);

    Set<JavaClassType> implementers = new HashSet<>();
    visitSubgraph(interfaceNode, false, subnode -> implementers.add(subnode.type));
    return implementers;
  }

  @Nonnull
  @Override
  public Set<JavaClassType> subclassesOf(@Nonnull JavaClassType classType) {
    ScanResult scanResult = lazyScanResult.get();
    ClassNode classNode = scanResult.typeToClassNode.get(classType);

    Set<JavaClassType> subclasses = new HashSet<>();
    visitSubgraph(classNode, false, subnode -> subclasses.add(subnode.type));
    return subclasses;
  }

  @Nonnull
  @Override
  public Set<JavaClassType> implementedInterfacesOf(@Nonnull JavaClassType classType) {
    return sootClassFor(classType).getInterfaces();
  }

  @Nullable
  @Override
  public JavaClassType superClassOf(@Nonnull JavaClassType classType) {
    return sootClassFor(classType).getSuperclass().orElse(null);
  }

  private static void visitSubgraph(Node node, boolean includeSelf, Consumer<Node> visitor) {
    if (includeSelf) {
      visitor.accept(node);
    }
    if (node instanceof InterfaceNode) {
      ((InterfaceNode) node)
          .directImplementers.forEach(
              directImplementer -> visitSubgraph(directImplementer, true, visitor));
      ((InterfaceNode) node)
          .extendingInterfaces.forEach(
              extendingInterface -> visitSubgraph(extendingInterface, true, visitor));
    } else if (node instanceof ClassNode) {
      ((ClassNode) node)
          .directSubclasses.forEach(directSubclass -> visitSubgraph(directSubclass, true, visitor));
    } else {
      throw new AssertionError("Unknown node type!");
    }
  }

  private ScanResult scanView() {
    Map<JavaClassType, ScanResult.ClassNode> typeToClassNode = new HashMap<>();
    Map<JavaClassType, ScanResult.InterfaceNode> typeToInterfaceNode = new HashMap<>();

    Collection<AbstractClass<? extends AbstractClassSource>> classes = view.getClasses();
    int i = 0;
    for (AbstractClass<? extends AbstractClassSource> aClass : classes) {
      System.out.println("" + i++ + " / " + classes.size());
      if (!(aClass instanceof SootClass)) {
        continue;
      }

      SootClass sootClass = (SootClass) aClass;

      if (sootClass.isInterface()) {
        InterfaceNode node =
            typeToInterfaceNode.computeIfAbsent(sootClass.getType(), InterfaceNode::new);
        for (JavaClassType extendedInterface : sootClass.getInterfaces()) {
          InterfaceNode extendedInterfaceNode =
              typeToInterfaceNode.computeIfAbsent(extendedInterface, InterfaceNode::new);
          extendedInterfaceNode.extendingInterfaces.add(node);
        }
      } else {
        ClassNode node = typeToClassNode.computeIfAbsent(sootClass.getType(), ClassNode::new);
        for (JavaClassType implementedInterface : sootClass.getInterfaces()) {
          InterfaceNode implementedInterfaceNode =
              typeToInterfaceNode.computeIfAbsent(implementedInterface, InterfaceNode::new);
          implementedInterfaceNode.directImplementers.add(node);
        }
        sootClass
            .getSuperclass()
            .ifPresent(
                superClass -> {
                  ClassNode superClassNode =
                      typeToClassNode.computeIfAbsent(superClass, ClassNode::new);
                  superClassNode.directSubclasses.add(node);
                });
      }
    }
    return new ScanResult(typeToClassNode, typeToInterfaceNode);
  }

  @Nonnull
  private SootClass sootClassFor(@Nonnull JavaClassType classType) {
    AbstractClass<? extends AbstractClassSource> aClass =
        view.getClass(classType)
            .orElseThrow(
                () -> new ResolveException("Could not find " + classType + " in view " + view));
    if (!(aClass instanceof SootClass)) {
      throw new ResolveException("" + classType + " is not a regular Java class");
    }
    return (SootClass) aClass;
  }

  static class ScanResult {
    final Map<JavaClassType, ClassNode> typeToClassNode;
    final Map<JavaClassType, InterfaceNode> typeToInterfaceNode;

    ScanResult(
        Map<JavaClassType, ClassNode> typeToClassNode,
        Map<JavaClassType, InterfaceNode> typeToInterfaceNode) {
      this.typeToClassNode = typeToClassNode;
      this.typeToInterfaceNode = typeToInterfaceNode;
    }

    static class Node {
      final JavaClassType type;

      Node(JavaClassType type) {
        this.type = type;
      }
    }

    static class InterfaceNode extends Node {
      final Set<ClassNode> directImplementers;
      final Set<InterfaceNode> extendingInterfaces;

      InterfaceNode(
          JavaClassType type,
          Set<ClassNode> directImplementers,
          Set<InterfaceNode> extendingInterfaces) {
        super(type);
        this.directImplementers = directImplementers;
        this.extendingInterfaces = extendingInterfaces;
      }

      InterfaceNode(JavaClassType type) {
        this(type, new HashSet<>(), new HashSet<>());
      }
    }

    static class ClassNode extends Node {
      final Set<ClassNode> directSubclasses;

      ClassNode(JavaClassType type, Set<ClassNode> directSubclasses) {
        super(type);
        this.directSubclasses = directSubclasses;
      }

      ClassNode(JavaClassType type) {
        this(type, new HashSet<>());
      }
    }
  }
}
