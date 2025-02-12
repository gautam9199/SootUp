package transformer.textsimilarity;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch.Diff;

public class TextComparisonTool extends JFrame {

  private static final long serialVersionUID = 1L;
  private JTextPane textPane1;
  private JTextPane textPane2;

  public TextComparisonTool() {
    setTitle("Git-like Textual Difference Checker");
    setSize(800, 600);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setLocationRelativeTo(null);

    textPane1 = new JTextPane();
    textPane2 = new JTextPane();

    JPanel panel = new JPanel(new GridLayout(1, 2));
    panel.add(new JScrollPane(textPane1));
    panel.add(new JScrollPane(textPane2));

    getContentPane().add(panel);
  }

  public void compareTexts(String text1, String text2) {
    DiffMatchPatch dmp = new DiffMatchPatch();

    // Split the texts into lines for a line-by-line comparison.
    List<String> lines1 = splitIntoLines(text1);
    List<String> lines2 = splitIntoLines(text2);

    LinkedList<Diff> diffs = dmp.diffMain(text1, text2);
    dmp.diffCleanupSemantic(diffs);

    // Display line-based diffs
    displayLineBasedDiffs(textPane1, textPane2, diffs, lines1, lines2);
  }

  // Splitting text into lines for line-based comparison
  private List<String> splitIntoLines(String text) {
    return List.of(text.split("\\n"));
  }

  private void displayLineBasedDiffs(
      JTextPane originalPane,
      JTextPane modifiedPane,
      LinkedList<Diff> diffs,
      List<String> originalLines,
      List<String> modifiedLines) {
    StyledDocument originalDoc = originalPane.getStyledDocument();
    StyledDocument modifiedDoc = modifiedPane.getStyledDocument();

    SimpleAttributeSet normal = new SimpleAttributeSet();
    SimpleAttributeSet insert = new SimpleAttributeSet();
    SimpleAttributeSet delete = new SimpleAttributeSet();
    SimpleAttributeSet change = new SimpleAttributeSet();

    // Define Git-like diff colors for each change type
    StyleConstants.setBackground(insert, Color.GREEN); // Added lines (Green)
    StyleConstants.setBackground(delete, Color.RED); // Deleted lines (Red)
    StyleConstants.setBackground(change, Color.YELLOW); // Changed lines (Yellow)

    try {
      originalDoc.remove(0, originalDoc.getLength());
      modifiedDoc.remove(0, modifiedDoc.getLength());

      for (Diff diff : diffs) {
        AttributeSet originalAttr = normal;
        AttributeSet modifiedAttr = normal;

        switch (diff.operation) {
          case DELETE:
            originalAttr = delete; // Show deletions in original
            break;
          case INSERT:
            modifiedAttr = insert; // Show insertions in modified
            break;
          case EQUAL:
            originalAttr = normal;
            modifiedAttr = normal;
            break;
        }

        // Insert into both original and modified views depending on the type of operation
        if (diff.operation != DiffMatchPatch.Operation.INSERT) {
          originalDoc.insertString(originalDoc.getLength(), diff.text, originalAttr);
        }
        if (diff.operation != DiffMatchPatch.Operation.DELETE) {
          modifiedDoc.insertString(modifiedDoc.getLength(), diff.text, modifiedAttr);
        }
      }

    } catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          TextComparisonTool tool = new TextComparisonTool();
          tool.setVisible(true);

          // Example texts with some changes
          String text1 = "This is line 1\nThis is line 2\nThis is line 3";
          String text2 = "This is line 1\nThis was line 2\nThis is line 3\nThis is a new line 4";

          tool.compareTexts(text1, text2);
        });
  }
}
