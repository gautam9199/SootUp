package transformer.utils;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.*;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;
import sootup.core.types.UnknownType;
import sootup.core.types.VoidType;
import sootup.java.core.types.JavaClassType;

public class ConversionUtil {

  static Logger logger = LoggerFactory.getLogger(ConversionUtil.class);

  // Handles storing values in variables based on type (primitive, array, object)
  protected int getStoreOpcode(Type type) {
    if (type == null) {
      throw new IllegalArgumentException("Type cannot be null in getStoreOpcode.");
    }
    if (Type.isIntLikeType(type)) {
      return Opcodes.ISTORE;
    } else if (type == PrimitiveType.FloatType.getInstance()) {
      return Opcodes.FSTORE;
    } else if (type == PrimitiveType.LongType.getInstance()) {
      return Opcodes.LSTORE;
    } else if (type == PrimitiveType.DoubleType.getInstance()) {
      return Opcodes.DSTORE;
    } else if (type instanceof ArrayType || Type.isObject(type) || type instanceof JavaClassType) {
      // Handle storing arrays and object types (like String[])
      return Opcodes.ASTORE;
    } else if (type instanceof UnknownType) {
      logger.warn("UnknownType detected, falling back to ASTORE.");
      return Opcodes.ASTORE;
    } else {
      throw new UnsupportedOperationException("Unsupported type for storing: " + type);
    }
  }

  // Handles loading constants into the stack
  protected int getLoadConstantOpcode(int value) {
    if (value >= -1 && value <= 5) {
      return Opcodes.ICONST_0 + value;
    } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
      return Opcodes.BIPUSH;
    } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
      return Opcodes.SIPUSH;
    } else {
      return Opcodes.LDC;
    }
  }

  // Converts a Soot method signature to an ASM method descriptor
  public String toAsmMethodDescriptor(MethodSignature methodSignature) {
    StringBuilder descriptor = new StringBuilder();
    descriptor.append("(");

    // Append parameter types
    for (Type parameterType : methodSignature.getParameterTypes()) {
      descriptor.append(toAsmTypeDescriptor(parameterType));
    }

    descriptor.append(")");
    // Append return type
    descriptor.append(toAsmTypeDescriptor(methodSignature.getType()));
    return descriptor.toString();
  }

  // Converts a Soot type to an ASM type descriptor
  public String toAsmTypeDescriptor(Type type) {
    if (type instanceof PrimitiveType || type instanceof VoidType) {
      return getPrimitiveTypeDescriptor(type);
    } else if (type instanceof ArrayType) {
      ArrayType arrayType = (ArrayType) type;
      return "[".repeat(arrayType.getDimension()) + toAsmTypeDescriptor(arrayType.getBaseType());
    } else if (type instanceof ClassType) {
      return "L" + ((ClassType) type).getFullyQualifiedName().replace('.', '/') + ";";
    } else {
      logger.warn("Unsupported type for ASM descriptor: " + type);
      throw new UnsupportedOperationException("Unsupported type for ASM descriptor: " + type);
    }
  }

  // Returns the descriptor for a primitive type
  private String getPrimitiveTypeDescriptor(Type type) {
    if (type == PrimitiveType.BooleanType.getInstance()) {
      return "Z";
    } else if (type == PrimitiveType.ByteType.getInstance()) {
      return "B";
    } else if (type == PrimitiveType.CharType.getInstance()) {
      return "C";
    } else if (type == PrimitiveType.ShortType.getInstance()) {
      return "S";
    } else if (type == PrimitiveType.IntType.getInstance()) {
      return "I";
    } else if (type == PrimitiveType.LongType.getInstance()) {
      return "J";
    } else if (type == PrimitiveType.FloatType.getInstance()) {
      return "F";
    } else if (type == PrimitiveType.DoubleType.getInstance()) {
      return "D";
    } else if (type == VoidType.getInstance()) {
      return "V";
    } else {
      throw new UnsupportedOperationException("Unsupported primitive type: " + type);
    }
  }

  public void writeClassToFile(byte[] classBytes, String className, String root)
      throws IOException {
    // Replace '.' with '/' to create a directory structure
    String classFilePath = className.replace('.', '/') + ".class";

    // Combine the root directory with the class file path
    String resultDir = "jimple.transformer/src/main/resources/results/" + root;
    File targetFile = new File(resultDir, classFilePath);

    // Ensure all directories in the path are created
    if (!targetFile.getParentFile().exists()) {
      Files.createDirectories(targetFile.getParentFile().toPath());
    }

    // Write the class bytes to the file
    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
      fos.write(classBytes);
    }

    System.out.println("Class file written to: " + targetFile.getAbsolutePath());
  }

  public void copyMetaInfAndConvertToJar(String sourcePath, String destinationPath)
      throws IOException {
    // Define the META-INF path in the source directory
    Path metaInfSource = Paths.get(sourcePath, "META-INF");

    // Check if the META-INF folder exists
    if (Files.exists(metaInfSource) && Files.isDirectory(metaInfSource)) {
      // Define the META-INF path in the destination directory
      Path metaInfDestination = Paths.get(destinationPath, "META-INF");

      // Copy META-INF folder to the destination directory
      Files.walkFileTree(
          metaInfSource,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
              // Create the directory in the destination path
              Path targetDir = metaInfDestination.resolve(metaInfSource.relativize(dir));
              Files.createDirectories(targetDir);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              // Copy each file
              Path targetFile = metaInfDestination.resolve(metaInfSource.relativize(file));
              Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
              return FileVisitResult.CONTINUE;
            }
          });

      System.out.println("META-INF folder copied successfully.");

      // Convert the parent folder of META-INF to a JAR file
      Path parentFolder = metaInfDestination.getParent(); // Parent folder of META-INF
      Path jarFilePath = Paths.get(parentFolder.toString() + ".jar");

      try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFilePath.toFile()))) {
        Files.walkFileTree(
            parentFolder,
            new SimpleFileVisitor<Path>() {
              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                  throws IOException {
                // Add files to the JAR with paths relative to the parent folder
                String entryName = parentFolder.relativize(file).toString().replace("\\", "/");
                JarEntry jarEntry = new JarEntry(entryName);
                jos.putNextEntry(jarEntry);
                Files.copy(file, jos);
                jos.closeEntry();
                return FileVisitResult.CONTINUE;
              }
            });
      }

      System.out.println("JAR file created at: " + jarFilePath.toAbsolutePath());
    } else {
      System.out.println("META-INF folder does not exist in the source path.");
    }
  }

  // Handles loading variables onto the stack based on type
  protected int getLoadOpcode(Type type) {
    if (type == null) {
      throw new IllegalArgumentException("Type cannot be null in getLoadOpcode.");
    }
    if (Type.isIntLikeType(type)) {
      return Opcodes.ILOAD;
    } else if (type == PrimitiveType.FloatType.getInstance()) {
      return Opcodes.FLOAD;
    } else if (type == PrimitiveType.LongType.getInstance()) {
      return Opcodes.LLOAD;
    } else if (type == PrimitiveType.DoubleType.getInstance()) {
      return Opcodes.DLOAD;
    } else if (type instanceof ArrayType || Type.isObject(type) || type instanceof JavaClassType) {
      return Opcodes.ALOAD;
    } else if (type instanceof UnknownType) {
      logger.warn("UnknownType detected, falling back to ALOAD.");
      return Opcodes.ALOAD;
    } else {
      throw new UnsupportedOperationException("Unsupported type for loading: " + type);
    }
  }

  protected int getStoreArrayOpcode(Type type) {

    if (type instanceof ArrayType) {
      ArrayType arr = (ArrayType) type;
      String caseE = arr.getBaseType().toString();
      switch (caseE) {
        case "int":
          return Opcodes.IASTORE; // For int[]
        case "boolean":
          return Opcodes.BASTORE; // For boolean[]
        case "byte":
          return Opcodes.BASTORE; // For byte[]
        case "char":
          return Opcodes.CASTORE; // For char[]
        case "short":
          return Opcodes.SASTORE; // For short[]
        case "long":
          return Opcodes.LASTORE; // For long[]
        case "float":
          return Opcodes.FASTORE; // For float[]
        case "double":
          return Opcodes.DASTORE; // For double[]
        default:
          if (arr.getBaseType() instanceof ArrayType || arr.getBaseType() instanceof ClassType) {
            return Opcodes.AASTORE;
          } else {
            throw new UnsupportedOperationException(
                "Unsupported primitive type for array storing: " + type);
          }
      }
    } else if (type instanceof ClassType) {
      // For reference types (e.g., String[], Object[])
      return Opcodes.AASTORE;
    } else {
      throw new UnsupportedOperationException("Unsupported type for array storing: " + type);
    }
  }

  protected int getLoadArrayOpcode(Type type) {
    if (type instanceof PrimitiveType) {
      switch (type.toString()) {
        case "int":
          return Opcodes.IALOAD; // For int[]
        case "boolean":
          return Opcodes.BALOAD; // For boolean[]
        case "byte":
          return Opcodes.BALOAD; // For byte[]
        case "char":
          return Opcodes.CALOAD; // For char[]
        case "short":
          return Opcodes.SALOAD; // For short[]
        case "long":
          return Opcodes.LALOAD; // For long[]
        case "float":
          return Opcodes.FALOAD; // For float[]
        case "double":
          return Opcodes.DALOAD; // For double[]
        default:
          throw new UnsupportedOperationException(
              "Unsupported primitive type for array loading: " + type);
      }
    } else if (type instanceof ArrayType || type instanceof ClassType) {
      // For reference types (e.g., String[], Object[])
      return Opcodes.AALOAD;
    } else {
      throw new UnsupportedOperationException("Unsupported type for array loading: " + type);
    }
  }

  public void writeResultsToJson(Gson gson, String outputPath, Object results) {
    try (FileWriter writer = new FileWriter(outputPath)) {
      gson.toJson(results, writer);
      System.out.println("Results written to: " + outputPath);
    } catch (IOException e) {
      System.err.println("Failed to write results to JSON: " + e.getMessage());
    }
  }
}
