package transformer.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

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

    public void writeClassToFile(byte[] classBytes, String className) throws IOException {
        // Define the path for the JAR file
        String jarFilePath = "jimple.transformer/src/main/resources/results/minTest.jar";

        // Write the class bytes to the JAR file
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFilePath))) {
            // Create the path for the class inside the JAR file, replacing '.' with '/' and adding .class extension
            JarEntry jarEntry = new JarEntry(className.replace('.', '/') + ".class");

            // Add entry to JAR and write class bytes
            jos.putNextEntry(jarEntry);
            jos.write(classBytes);
            jos.closeEntry();
        }

        logger.info("Class file written to JAR: " + jarFilePath);
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
                    return Opcodes.IASTORE;   // For int[]
                case "boolean":
                    return Opcodes.BASTORE;   // For boolean[]
                case "byte":
                    return Opcodes.BASTORE;   // For byte[]
                case "char":
                    return Opcodes.CASTORE;   // For char[]
                case "short":
                    return Opcodes.SASTORE;   // For short[]
                case "long":
                    return Opcodes.LASTORE;   // For long[]
                case "float":
                    return Opcodes.FASTORE;   // For float[]
                case "double":
                    return Opcodes.DASTORE;   // For double[]
                default:
                	if(arr.getBaseType() instanceof ArrayType || arr.getBaseType() instanceof ClassType) {
                		 return Opcodes.AASTORE;
                	}else {
                		throw new UnsupportedOperationException("Unsupported primitive type for array storing: " + type);
                	}

            }
        } else if (type instanceof ArrayType || type instanceof ClassType) {
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
                    return Opcodes.IALOAD;   // For int[]
                case "boolean":
                    return Opcodes.BALOAD;   // For boolean[]
                case "byte":
                    return Opcodes.BALOAD;   // For byte[]
                case "char":
                    return Opcodes.CALOAD;   // For char[]
                case "short":
                    return Opcodes.SALOAD;   // For short[]
                case "long":
                    return Opcodes.LALOAD;   // For long[]
                case "float":
                    return Opcodes.FALOAD;   // For float[]
                case "double":
                    return Opcodes.DALOAD;   // For double[]
                default:
                    throw new UnsupportedOperationException("Unsupported primitive type for array loading: " + type);
            }
        } else if (type instanceof ArrayType || type instanceof ClassType) {
            // For reference types (e.g., String[], Object[])
            return Opcodes.AALOAD;
        } else {
            throw new UnsupportedOperationException("Unsupported type for array loading: " + type);
        }
    }



}
