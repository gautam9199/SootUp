package transformer.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.model.SootMethod;

public class LocalIndexMapper {
  static Logger logger = LoggerFactory.getLogger(LocalIndexMapper.class);

  private Map<Local, Integer> localIndexMap = new HashMap<>();
  private boolean isConstructor;

  private boolean isStatic;
  private List<LValue> definedLocals;
  private boolean recentObjectCreation = false;

  private boolean isExceptionBlock = false;

  private final List<String> stack = new ArrayList<>();

  public LocalIndexMapper(
      boolean isConstructor,
      boolean isStatic,
      SootMethod method,
      List<LValue> definedLocals,
      List<Local> locals) {

    this.isStatic = isStatic;
    this.definedLocals = definedLocals;
    this.isConstructor = isConstructor;

    System.out.println("Locals To Map::" + locals);
    this.localIndexMap = createLocalIndexMap(locals);
  }

  /**
   * Retrieves the local index for a given Immediate (usually a Local). If the Local is not yet
   * mapped to an index, a new index is generated.
   *
   * @param immediate The immediate value (typically a Local) for which to retrieve the index.
   * @return The local index corresponding to the Immediate.
   */
  public int getLocalIndex(Immediate immediate) {
    if (immediate instanceof Local) {

      Local local = (Local) immediate;

      if (!localIndexMap.containsKey(local)) {
        if (local.getName().contains("this")) {
          return 0;
        } else {
          int maxValue =
              localIndexMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
          localIndexMap.put(local, maxValue);
        }
      }

      return localIndexMap.get(local);

    } else {
      logger.error("Immediate type is not a Local: " + immediate.getClass().getName());
      throw new UnsupportedOperationException(
          "Unsupported Immediate type: " + immediate.getClass().getName());
    }
  }

  /** Resets the local index map to start fresh for a new method. */
  public void reset() {
    localIndexMap.clear();
  }

  public String getAllMappedIndices() {
    StringBuilder indicesInfo = new StringBuilder();
    for (Map.Entry<Local, Integer> entry : localIndexMap.entrySet()) {
      indicesInfo
          .append("Local: ")
          .append(entry.getKey() == null ? "this" : entry.getKey().toString())
          .append(", Index: ")
          .append(entry.getValue())
          .append("\n");
    }
    return indicesInfo.toString();
  }

  public Map<Local, Integer> getLocalIndexMap() {

    return this.localIndexMap;
  }

  public boolean isRecentObjectCreation() {
    return this.recentObjectCreation;
  }

  public void setRecentObjectCreatiion(boolean flag) {
    this.recentObjectCreation = flag;
  }

  public boolean isConstructor() {
    return this.isConstructor;
  }

  public boolean isStatic() {
    return this.isStatic;
  }

  public int getIndexFromDef(Local local) {
    return this.definedLocals.indexOf(local);
  }

  public boolean isStackVariable(Local local) {
    // Check if the local's name starts with "$stack"
    return local.getName().startsWith("$stack");
  }

  public static Map<Local, Integer> createLocalIndexMap(List<Local> locals) {
    Map<Local, Integer> localIndexMap = new HashMap<>();

    for (Local local : locals) {
      // Check if the local is 'this' and assign it index 0
      if (local.getName().equals("this")) {
        localIndexMap.put(local, 0);
      } else {
        // Extract the index part for other locals
        int index = extractIndexFromLocalName(local.getName());
        localIndexMap.put(local, index);
      }
    }

    return localIndexMap;
  }

  private static int extractIndexFromLocalName(String localName) {
    // Check for derived index first, e.g., "l2#1"
    if (localName.contains("#") && !localName.startsWith("#")) {
      String[] parts = localName.split("#");
      if (parts.length == 2) {
        try {
          return Integer.parseInt(parts[1]); // derived index
        } catch (NumberFormatException e) {
          return -1; // Return -1 if parsing fails
        }
      }
    }

    // If no derived index, look for primary index in strings like "l1" or "$stack2"
    String primaryPattern = "(?<=\\D)(\\d+)$";
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile(primaryPattern).matcher(localName);
    if (matcher.find()) {
      try {
        return Integer.parseInt(matcher.group(1)); // primary index
      } catch (NumberFormatException e) {
        return -1; // Return -1 if parsing fails
      }
    }

    // Return -1 if no index found
    return -1;
  }

  public void updateExceptionReference(Local l, int index) {
    localIndexMap.replace(l, index);
  }

  public boolean isExceptionBlock() {
    return isExceptionBlock;
  }

  public void setExceptionBlock(boolean isExceptionBlock) {
    this.isExceptionBlock = isExceptionBlock;
  }

  // Push an element onto the stack
  public void stackPush(String element) {
    stack.add(element);
  }

  // Pop the top element from the stack
  public String stackPop() {
    if (!stack.isEmpty()) {
      return stack.remove(stack.size() - 1);
    }
    throw new IllegalStateException("Logical stack underflow");
  }

  // Peek the top element without removing it
  public String stackPeek() {
    if (!stack.isEmpty()) {
      return stack.get(stack.size() - 1);
    }
    throw new IllegalStateException("Logical stack underflow");
  }

  // Check if an element exists on the stack
  public boolean stackContains(String element) {
    return stack.contains(element);
  }

  // Clear the stack
  public void stackClear() {
    stack.clear();
  }

  // Get the current size of the stack
  public int stackSize() {
    return stack.size();
  }

  public String stackPrint() {
    return stack.toString();
  }
}
