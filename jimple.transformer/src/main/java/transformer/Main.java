package transformer;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.SootClass;
import sootup.core.model.SourceType;
import sootup.core.transform.BodyInterceptor;
import sootup.core.types.ClassType;
import sootup.core.views.View;
import sootup.interceptors.LocalPacker;
import sootup.interceptors.LocalSplitter;
import sootup.interceptors.TypeAssigner;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.views.JavaView;
import transformer.helpers.BytecodeReader;
import transformer.helpers.Stats;
import transformer.textsimilarity.ClassFileTester;
import transformer.textsimilarity.TextComparisonTool;
import transformer.textsimilarity.TextSimilarityUtils;
import transformer.utils.ConversionUtil;
import transformer.utils.SootClassToBytecodeConverter;

public class Main {

  public static void main(String[] args) throws IOException {
    String root = "slf4j-simple-2.0.17-SNAPSHOT";
    Path pathToBinary = Paths.get("jimple.transformer/src/main/resources/java6/" + root);
    String sourcePath = "jimple.transformer/src/main/resources/java6/" + root;
    String destPath = "jimple.transformer/src/main/resources/results/" + root;

    List<BodyInterceptor> bodyInterceptors = new ArrayList<>();
    //bodyInterceptors.add(new LocalSplitter());
    //bodyInterceptors.add(new TypeAssigner());
    //bodyInterceptors.add(new LocalPacker());

    AnalysisInputLocation inputLocation =
        new JavaClassPathAnalysisInputLocation(
            pathToBinary.toString(), SourceType.Application, bodyInterceptors);

    View view = new JavaView(inputLocation);
    ConversionUtil cutil = new ConversionUtil();
    List<String> classNames = new ArrayList<>();
   view.getClasses().forEach(field -> classNames.add(field.getName()));

    List<Stats> statsList = new ArrayList<>();


    boolean visibility = false;
    for (String className : classNames) {
      System.out.println("Processing class: " + className);

      Stats stats = new Stats();
      stats.setClassName(className);

      // Measure Bytecode to SootClass performance
      measurePerformance(
          () -> {
            ClassType classType = view.getIdentifierFactory().getClassType(className);
            SootClass sootClass = view.getClass(classType).orElseThrow();
          },
          stats,
          "b2s");

      SootClass sootClass =
          view.getClass(view.getIdentifierFactory().getClassType(className)).orElse(null);
      if (sootClass == null) {
        System.err.println("Class not found: " + className);
        stats.setErrorMessage("Class not found");
        statsList.add(stats);
        continue;
      }

      SootClassToBytecodeConverter converter = new SootClassToBytecodeConverter();

      // Measure SootClass to Bytecode performance
      measurePerformance(
          () -> {
            try {
              converter.convert(sootClass);
            } catch (IOException e) {
              stats.setErrorMessage(e.getMessage());
            }
          },
          stats,
          "s2b");

      BytecodeReader br = new BytecodeReader();
      String original = br.readByteClass(className, root);
      String transformed = "";
      try {
        transformed = BytecodeReader.printClassContent(converter.convert(sootClass));
      } catch (IOException e) {
        System.err.println("Error transforming bytecode for class: " + className);
        stats.setErrorMessage("Error transforming bytecode: " + e.getMessage());
        statsList.add(stats);
        continue;
      }

      TextComparisonTool tool = new TextComparisonTool();
      tool.setVisible(visibility);
      tool.compareTexts(original, transformed);

      double normalizedScore = TextSimilarityUtils.computeNormalizedScore(original, transformed);
      double weightedScore = TextSimilarityUtils.computeWeightedScore(original, transformed);
      stats.setNormalizedScore(normalizedScore);
      stats.setWeightedScore(weightedScore);

      // Save the transformed class
      try {
        cutil.writeClassToFile(converter.convert(sootClass), className, root);
      } catch (IOException e) {
        System.err.println(
            "Error writing transformed class for " + className + ": " + e.getMessage());
        stats.setErrorMessage("Error writing transformed class: " + e.getMessage());
      }

      statsList.add(stats);
    }

    // Copy META-INF and convert to JAR
    cutil.copyMetaInfAndConvertToJar(sourcePath, destPath);

    // Save results to JSON
    Gson gson = new Gson();
    cutil.writeResultsToJson(gson, root + "textual_test_results.json", statsList);

    // Run semantic tests
    String outputJsonPath = root + "semantic_test_results.json";
    ClassFileTester tester = new ClassFileTester();
    tester.testClassesInDirectory(destPath, outputJsonPath);
  }

  /**
   * Utility to measure time and memory usage for a given task with high granularity.
   *
   * @param task The task to execute.
   * @param stats The Stats object to record the results.
   * @param phase The phase identifier ("b2s" or "s2b").
   */
  private static void measurePerformance(Runnable task, Stats stats, String phase) {
    Runtime runtime = Runtime.getRuntime();
    int iterations = 1; // Number of iterations for averaging measurements
    long totalElapsedTime = 0;
    long totalMemoryUsed = 0;

    for (int i = 0; i < iterations; i++) {
      runtime.gc(); // Suggest garbage collection before each iteration
      long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
      long startTime = System.nanoTime();

      try {
        task.run();
      } catch (Exception e) {
        System.err.println("Error during " + phase + ": " + e.getMessage());
        stats.setErrorMessage("Error during " + phase + ": " + e.getMessage());
        return; // Exit measurement if the task fails
      }

      long endTime = System.nanoTime();
      long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();

      totalElapsedTime += (endTime - startTime); // Nanoseconds
      totalMemoryUsed += (usedMemoryAfter - usedMemoryBefore); // Bytes
    }

    // Calculate averages
    long averageElapsedTime = totalElapsedTime / iterations; // Nanoseconds
    long averageMemoryUsed = totalMemoryUsed / iterations; // Bytes

    // Convert memory to MB for reporting purposes
    double memoryInMB =
        Math.max(
            averageMemoryUsed / (1024.0 * 1024.0), 0.01); // Avoid 0 MB, set a minimum of 0.01 MB

    if ("b2s".equals(phase)) {
      stats.setB2s(averageElapsedTime / 1_000_000); // Convert to milliseconds
      stats.setB2sMemory(memoryInMB);
    } else if ("s2b".equals(phase)) {
      stats.setS2b(averageElapsedTime / 1_000_000); // Convert to milliseconds
      stats.setS2bMemory(memoryInMB);
    }
  }
}
