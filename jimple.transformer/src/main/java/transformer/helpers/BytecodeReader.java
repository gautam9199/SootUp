package transformer.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;

public class BytecodeReader {

  public String readByteClass(String name, String root) {

    String filePath =
        "jimple.transformer/src/main/resources/java6/"
            + root
            + "/"
            + name.replace('.', '/')
            + ".class";

    if (checkFileExists(filePath)) {
      Path path = Paths.get(filePath);
      try (InputStream inputStream = Files.newInputStream(path)) {
        ClassReader cr = new ClassReader(inputStream);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        // Use the custom visitor to collect, sort, and output methods alphabetically
        CustomClassVisitor customVisitor = new CustomClassVisitor(printWriter);
        cr.accept(customVisitor, 0);

        return stringWriter.toString();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return "File does not exist at the specified location.";
  }

  public boolean checkFileExists(String filePath) {
    Path path = Paths.get(filePath);
    boolean exists = Files.exists(path);

    if (exists) {
      System.out.println("The file exists at the specified location.");
    } else {
      System.out.println("The file does not exist at the specified location.");
    }

    return exists;
  }

  // Custom ClassVisitor to handle alphabetical method sorting
  class CustomClassVisitor extends ClassVisitor {
    private final List<MethodNode> methodList = new ArrayList<>();
    private final PrintWriter printWriter;
    private final ClassNode classNode = new ClassNode(); // Collect the full class structure

    public CustomClassVisitor(PrintWriter printWriter) {
      super(Opcodes.ASM9);
      this.printWriter = printWriter;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      // Pass class header to the ClassNode to ensure full class structure is captured
      classNode.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodNode methodNode = new MethodNode(access, name, descriptor, signature, exceptions);
      methodList.add(methodNode); // Collect methods
      return methodNode; // Collect the method nodes without processing immediately
    }

    @Override
    public void visitEnd() {
      // Sort methods alphabetically by name
      Collections.sort(methodList, Comparator.comparing(m -> m.name));

      // Now add sorted methods to the classNode
      classNode.methods.addAll(methodList);

      // Use TraceClassVisitor to print the full class structure, including sorted methods
      TraceClassVisitor traceVisitor = new TraceClassVisitor(printWriter);
      classNode.accept(traceVisitor); // ClassNode accepts the visitor to print the complete class

      super.visitEnd();
    }
  }

  public static String printClassContent(byte[] classBytes) throws IOException {
    System.out.println("Transformed code");
    ClassReader reader = new ClassReader(classBytes);
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);

    // Using a custom class visitor to sort methods alphabetically
    ClassNode classNode = new ClassNode();
    try {
      reader.accept(classNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    } catch (Exception e) {
      if (e instanceof IndexOutOfBoundsException) {
        System.out.println("Exception during print" + e.getMessage());
      }
    }

    // Sort the methods alphabetically
    classNode.methods.sort((MethodNode m1, MethodNode m2) -> m1.name.compareTo(m2.name));

    // Use TraceClassVisitor to print the sorted methods
    TraceClassVisitor traceVisitor = new TraceClassVisitor(printWriter);
    classNode.accept(traceVisitor);

    printWriter.flush();
    return stringWriter.toString();
  }
}
