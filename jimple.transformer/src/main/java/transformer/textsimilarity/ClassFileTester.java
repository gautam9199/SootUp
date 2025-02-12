package transformer.textsimilarity;

import com.google.gson.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ClassFileTester {

  private final List<JsonObject> results; // Store results for all classes
  private final Gson gson;

  public ClassFileTester() {
    this.gson = new GsonBuilder().setPrettyPrinting().create();
    this.results = new ArrayList<>();
  }

  public void testClassesInDirectory(String directoryPath, String outputPath) {
    File directory = new File(directoryPath);
    if (!directory.exists() || !directory.isDirectory()) {
      System.out.println("Invalid directory path: " + directoryPath);
      return;
    }

    // Traverse the directory recursively
    traverseAndTest(directory, directoryPath);

    // Write results to JSON file
    writeResultsToJson(outputPath);
  }

  private void traverseAndTest(File file, String rootDirectory) {
    if (file.isDirectory()) {
      for (File child : Objects.requireNonNull(file.listFiles())) {
        traverseAndTest(child, rootDirectory);
      }
    } else if (file.getName().endsWith(".class")) {
      // Process the class file
      try {
        testClass(file, rootDirectory);
      } catch (VerifyError ve) {
        // Log verification errors
        System.err.println(
            "Verification failed for class " + file.getName() + ": " + ve.getMessage());
        recordError(file.getName(), "VerifyError", ve.getMessage());
      } catch (Throwable e) {
        // Log any other runtime exceptions
        System.err.println("Error testing class " + file.getName() + ": " + e.getMessage());
        recordError(file.getName(), e.getClass().getSimpleName(), e.getMessage());
      }
    }
  }

  private void testClass(File classFile, String rootDirectory)
      throws IOException, ClassNotFoundException {
    // Convert the file path to a fully qualified class name
    Path classPath = Paths.get(rootDirectory).relativize(classFile.toPath());
    String className = classPath.toString().replace(File.separatorChar, '.').replace(".class", "");

    // Load the class dynamically
    ClassLoader classLoader = new CustomClassLoader(rootDirectory);
    Class<?> clazz = classLoader.loadClass(className);

    // Perform basic semantic tests
    JsonObject classResult = new JsonObject();
    classResult.addProperty("className", className);

    // Example: Collect method details
    JsonArray methodsArray = new JsonArray();
    for (Method method : clazz.getDeclaredMethods()) {
      JsonObject methodJson = new JsonObject();
      methodJson.addProperty("name", method.getName());
      methodJson.addProperty("returnType", method.getReturnType().getName());
      methodsArray.add(methodJson);
    }
    classResult.add("methods", methodsArray);

    // Example: Test default no-argument constructor
    try {
      clazz.getDeclaredConstructor().newInstance();
      classResult.addProperty("instanceCreation", "success");
    } catch (NoSuchMethodException e) {
      classResult.addProperty("instanceCreation", "no-arg constructor not available");
    } catch (Exception e) {
      classResult.addProperty("instanceCreation", "failed: " + e.getMessage());
    }

    // Add result for this class
    results.add(classResult);
  }

  private void recordError(String className, String errorType, String errorMessage) {
    JsonObject errorResult = new JsonObject();
    errorResult.addProperty("className", className);
    errorResult.addProperty("errorType", errorType);
    errorResult.addProperty("errorMessage", errorMessage);
    results.add(errorResult);
  }

  private void writeResultsToJson(String outputPath) {
    try (FileWriter writer = new FileWriter(outputPath)) {
      gson.toJson(results, writer);
      System.out.println("Results written to: " + outputPath);
    } catch (IOException e) {
      System.err.println("Failed to write results to JSON: " + e.getMessage());
    }
  }

  static class CustomClassLoader extends ClassLoader {
    private final String rootDirectory;

    public CustomClassLoader(String rootDirectory) {
      this.rootDirectory = rootDirectory;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      try {
        Path classFilePath =
            Paths.get(rootDirectory, name.replace('.', File.separatorChar) + ".class");
        byte[] classBytes = Files.readAllBytes(classFilePath);
        return defineClass(name, classBytes, 0, classBytes.length);
      } catch (IOException e) {
        throw new ClassNotFoundException(name, e);
      }
    }
  }
}
