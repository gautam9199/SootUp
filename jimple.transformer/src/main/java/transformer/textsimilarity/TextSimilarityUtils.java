package transformer.textsimilarity;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.LevenshteinDistance;

public class TextSimilarityUtils {

    public static int computeLevenshteinDistance(String original, String transformed) {
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
        return levenshteinDistance.apply(original, transformed);
    }

    public static double computeJaccardSimilarity(String original, String transformed) {
        Set<String> set1 = new HashSet<>();
        for (String word : original.split("\\s+")) { // Split by whitespace
            set1.add(word);
        }

        Set<String> set2 = new HashSet<>();
        for (String word : transformed.split("\\s+")) { // Split by whitespace
            set2.add(word);
        }

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    public static double computeJaroWinklerDistance(String original, String transformed) {
        JaroWinklerDistance jaroWinklerDistance = new JaroWinklerDistance();
        return jaroWinklerDistance.apply(original, transformed);
    }

    public static double computeNormalizedScore(String original, String transformed) {
        // Compute individual scores
        String cleanedOriginal = preprocessString(original);
        String cleanedTransformed = preprocessString(transformed);
        int levenshteinDist = computeLevenshteinDistance(cleanedOriginal, cleanedTransformed);
        double jaccardSimilarity = computeJaccardSimilarity(cleanedOriginal, cleanedTransformed);
        double jaroWinklerDist = computeJaroWinklerDistance(cleanedOriginal, cleanedTransformed);

        // Normalize Levenshtein Distance
        int maxLen = Math.max(original.length(), transformed.length());
        double normalizedLevenshtein = 1.0 - ((double) levenshteinDist / maxLen);

        // Calculate combined score as average
        return (normalizedLevenshtein + jaccardSimilarity + jaroWinklerDist) / 3.0;
    }

    public static double computeWeightedScore(String original, String transformed) {
        // Compute individual scores
        String cleanedOriginal = preprocessString(original);
        String cleanedTransformed = preprocessString(transformed);
        int levenshteinDist = computeLevenshteinDistance(cleanedOriginal, cleanedTransformed);
        double jaccardSimilarity = computeJaccardSimilarity(cleanedOriginal, cleanedTransformed);
        double jaroWinklerDist = computeJaroWinklerDistance(cleanedOriginal, cleanedTransformed);

        // Normalize Levenshtein Distance
        int maxLen = Math.max(original.length(), transformed.length());
        double normalizedLevenshtein = 1.0 - ((double) levenshteinDist / maxLen);

        // Assign weights to each metric
        double levenshteinWeight = 0.4;  // Assuming Levenshtein is most important
        double jaccardWeight = 0.3;
        double jaroWinklerWeight = 0.3;

        // Calculate weighted score
        return (normalizedLevenshtein * levenshteinWeight) +
               (jaccardSimilarity * jaccardWeight) +
               (jaroWinklerDist * jaroWinklerWeight);
    }


    public static void main(String[] args) {
        String original = "Hello world";
        String transformed = "HelloWorld";

        double normalizedScore = computeNormalizedScore(original, transformed);
        System.out.println("Normalized Similarity Score: " + normalizedScore);
    }
    private static String preprocessString(String input) {
        // Regular expression to match LINENUMBER <number> L<something>
        String regex = "LINENUMBER\\s+\\d+\\s+L\\S+";
        Pattern pattern = Pattern.compile(regex);

        // Remove all matches of the pattern
        return pattern.matcher(input)
                .replaceAll("")  // Remove the matched patterns
                .replaceAll("\\s+", " ") // Replace multiple spaces with a single space
                .trim(); // Trim leading and trailing spaces
    }
}
