package transformer.utils;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.ref.JFieldRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.ref.JStaticFieldRef;

public class FieldReferenceIndexer extends ConversionUtil {

  static Logger logger = LoggerFactory.getLogger(FieldReferenceIndexer.class);
  private final LocalIndexMapper indexMapper;

  public FieldReferenceIndexer(LocalIndexMapper indexMapper) {
    this.indexMapper = indexMapper;
  }

  public int getObjectReferenceIndex(JFieldRef fieldRef) {
    if (fieldRef instanceof JInstanceFieldRef) {
      // For instance fields, get the base (object reference) and retrieve its index
      Local baseLocal =
          ((JInstanceFieldRef) fieldRef)
              .getBase(); // Retrieves the 'base' object for instance fields
      return indexMapper.getLocalIndex(baseLocal);
    } else if (fieldRef instanceof JStaticFieldRef) {
      // Static fields do not have an associated object reference, return -1 to indicate this
      return -1;
    } else {
      throw new UnsupportedOperationException("Unsupported JFieldRef type.");
    }
  }

  public void loadFieldReference(MethodVisitor mv, JFieldRef fieldRef) {
    int objectRefIndex = getObjectReferenceIndex(fieldRef);
    String owner = fieldRef.getFieldSignature().getDeclClassType().toString().replace('.', '/');
    String name = fieldRef.getFieldSignature().getName();
    String desc = getTypeDescriptor(fieldRef.getFieldSignature().getType().toString());

    if (objectRefIndex >= 0) {
      // Instance field: Load the object reference and then the field
      mv.visitVarInsn(Opcodes.ALOAD, objectRefIndex);
      mv.visitFieldInsn(Opcodes.GETFIELD, owner, name, desc);
    } else {
      // Static field: No object reference needed
      mv.visitFieldInsn(Opcodes.GETSTATIC, owner, name, desc);
    }
  }

  public void storeFieldReference(MethodVisitor mv, JFieldRef fieldRef) {
    int objectRefIndex = getObjectReferenceIndex(fieldRef);
    String owner = fieldRef.getFieldSignature().getDeclClassType().toString().replace('.', '/');
    String name = fieldRef.getFieldSignature().getName();
    String desc = getTypeDescriptor(fieldRef.getFieldSignature().getType().toString());

    if (objectRefIndex >= 0) {
      // Instance field: Load the object reference and then store the value in the field
      // mv.visitVarInsn(Opcodes.ALOAD, objectRefIndex);
      mv.visitFieldInsn(Opcodes.PUTFIELD, owner, name, desc);
    } else {
      // Static field: No object reference needed

      mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, name, desc);
    }
  }

  private String getTypeDescriptor(String typeName) {
    if (typeName.startsWith("[")) {
      // Array types are already in correct descriptor form
      return typeName;
    } else if (typeName.contains(".")) {
      // Convert to object type descriptor form
      return "L" + typeName.replace('.', '/') + ";";
    } else {
      // Primitive types
      switch (typeName) {
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
          return "L" + typeName + ";";
      }
    }
  }
}
