package transformer;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.SootClass;
import sootup.core.model.SourceType;
import sootup.core.transform.BodyInterceptor;
import sootup.core.types.ClassType;
import sootup.core.views.View;
import sootup.interceptors.DeadAssignmentEliminator;
import sootup.interceptors.LocalPacker;
import sootup.interceptors.LocalSplitter;
import sootup.interceptors.TypeAssigner;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;

import sootup.java.core.views.JavaView;
import transformer.helpers.BytecodeReader;
import transformer.helpers.Stats;
import transformer.textsimilarity.TextComparisonTool;
import transformer.textsimilarity.TextSimilarityUtils;
import transformer.utils.ConversionUtil;
import transformer.utils.SootClassToBytecodeConverter;

public class Main {
    public static void main(String[] args) {
        Path pathToBinary = Paths.get("jimple.transformer/src/main/resources/java6/binary");

        List<BodyInterceptor> bodyInterceptors = new ArrayList<>();
        bodyInterceptors.add(new LocalSplitter());
        bodyInterceptors.add(new TypeAssigner());

        bodyInterceptors.add(new LocalPacker());
        //bodyInterceptors.add(new DeadAssignmentEliminator());
        // AnalysisInputLocation inputLocation =
        // PathBasedAnalysisInputLocation.create(pathToBinary, null);
        AnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation(pathToBinary.toString(),
                SourceType.Application, bodyInterceptors);

        View view = new JavaView(inputLocation);

        List<String> classNames = new ArrayList<>();
        view.getClasses().forEach(field -> classNames.add(field.getName()));



        //classNames = List.of("RecordTest");
        List<Stats> states = new ArrayList<>();
        for (String className : classNames) {
            if(className.contains("$")){
                continue;
            }
            boolean visibility = false;
            Stats s = new Stats();
            // String classToTransform = "IfTest";
            String classToTransform = className;
            ClassType classType = view.getIdentifierFactory().getClassType(classToTransform);

            SootClass sootClass = view.getClass(classType).get();

            // byte[] bytecode =

            SootClassToBytecodeConverter converter = new SootClassToBytecodeConverter();
            byte[] bytecode = converter.convert(sootClass);

            BytecodeReader br = new BytecodeReader();
            String original = br.readByteClass(classToTransform);
            String transformed = "";
            try {

                transformed = BytecodeReader.printClassContent(bytecode);
            } catch (IOException e) {

                    s.setErrorMessage(e.getMessage());
                continue;


            }
            System.out.println("Original::" + original);
            System.out.println("Transformed::" + transformed);

            TextComparisonTool tool = new TextComparisonTool();
            tool.setVisible(visibility);
            tool.compareTexts(original, transformed);

            ConversionUtil cutil = new ConversionUtil();
            try {
                cutil.writeClassToFile(bytecode, classToTransform);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            double normalizedScore = TextSimilarityUtils.computeNormalizedScore(original, transformed);
            double weightedScore = TextSimilarityUtils.computeWeightedScore(original, transformed);
            System.out.println("Normalized Similarity Score: " + normalizedScore);
            System.out.println("Weighted Similarity Score: " + weightedScore);

            s.setClassName(className);
            s.setNormalizedScore(normalizedScore);
            s.setWeightedScore(weightedScore);
            states.add(s);
        }
        Gson gson = new Gson();
        // convert your list to json
        String jsonCartList = gson.toJson(states);
        // print your generated json
        System.out.println("STATS: " + jsonCartList);

    }
}