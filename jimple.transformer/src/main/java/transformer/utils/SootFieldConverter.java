package transformer.utils;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sootup.core.model.SootField;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;

public class SootFieldConverter {
    static Logger logger = LoggerFactory.getLogger(SootFieldConverter.class);

    public void convertField(SootField field, ClassVisitor cv) {
        // Convert the type to the correct descriptor format
        String desc = getTypeDescriptor(field.getType());

        // Get the field signature, handling generics if necessary
        String signature = field.getSignature() != null ? field.getSignature().toString() : null;

        // Retrieve all modifiers, not just access modifiers
        int accessSpec = getModifier(field);

        // Handle default value if applicable (e.g., static final fields)
        Object defaultValue = getDefaultValue(field);

        // Visit the field with all the gathered information
        FieldVisitor fv = cv.visitField(accessSpec, field.getName(), desc, null, defaultValue);
        if (fv != null) {
            fv.visitEnd();
        }
    }

    // Helper method to get the type descriptor from the Soot type
    private String getTypeDescriptor(Type type) {
        if (type instanceof PrimitiveType) {
            return getPrimitiveDescriptor(type);
        } else if (type instanceof ArrayType) {
            return "[" + getTypeDescriptor(((ArrayType) type).getBaseType());
        } else if (type instanceof ClassType) {
            return "L" + ((ClassType) type).getFullyQualifiedName().replace('.', '/') + ";";
        } else {
            logger.warn("Unsupported type: " + type);
            throw new UnsupportedOperationException("Unsupported type: " + type);
        }
    }

    private String getPrimitiveDescriptor(Type type) {
        switch (type.toString()) {
            case "int": return "I";
            case "boolean": return "Z";
            case "byte": return "B";
            case "char": return "C";
            case "short": return "S";
            case "long": return "J";
            case "float": return "F";
            case "double": return "D";
            default:
                logger.warn("Unsupported primitive type: " + type);
                throw new UnsupportedOperationException("Unsupported primitive type: " + type);
        }
    }

    private int getModifier(SootField field) {
        int modifiers = 0;

        // Access modifiers
        if (field.isPrivate()) modifiers |= Opcodes.ACC_PRIVATE;
        if (field.isProtected()) modifiers |= Opcodes.ACC_PROTECTED;
        if (field.isPublic()) modifiers |= Opcodes.ACC_PUBLIC;

        // Additional modifiers
        if (field.isStatic()) modifiers |= Opcodes.ACC_STATIC;
        if (field.isFinal()) modifiers |= Opcodes.ACC_FINAL;
        
        //if (field.isVolatile()) modifiers |= Opcodes.ACC_VOLATILE;
        //if (field.isTransient()) modifiers |= Opcodes.ACC_TRANSIENT;

        return modifiers;
    }

    private Object getDefaultValue(SootField field) {
        // If the field is static and final, it might have a default value
        if (field.isStatic() && field.isFinal()) {
            // For primitive types or strings, set default values if available
            if (field.getType() instanceof PrimitiveType) {
                switch (field.getType().toString()) {
                    case "int": return 0;
                    case "boolean": return false;
                    case "byte": return (byte) 0;
                    case "char": return '\0';
                    case "short": return (short) 0;
                    case "long": return 0L;
                    case "float": return 0.0f;
                    case "double": return 0.0;
                }
            } else if (field.getType().toString().equals("java.lang.String")) {
                return "";
            }
        }
        return null;
    }
}
