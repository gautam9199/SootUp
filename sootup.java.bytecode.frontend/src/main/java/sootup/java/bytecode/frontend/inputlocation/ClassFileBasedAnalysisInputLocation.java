package sootup.java.bytecode.frontend.inputlocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.commons.io.FilenameUtils;
import sootup.core.IdentifierFactory;
import sootup.core.model.SourceType;
import sootup.core.transform.BodyInterceptor;
import sootup.core.types.ClassType;
import sootup.core.views.View;
import sootup.interceptors.BytecodeBodyInterceptors;
import sootup.java.bytecode.frontend.conversion.AsmJavaClassProvider;
import sootup.java.core.JavaSootClassSource;
import sootup.java.core.types.JavaClassType;

public class ClassFileBasedAnalysisInputLocation extends PathBasedAnalysisInputLocation {

  @Nonnull private final String omittedPackageName;

  public ClassFileBasedAnalysisInputLocation(
      @Nonnull Path classFilePath,
      @Nonnull String omittedPackageName,
      @Nonnull SourceType srcType) {
    this(
        classFilePath,
        omittedPackageName,
        srcType,
        BytecodeBodyInterceptors.Default.getBodyInterceptors());
  }

  public ClassFileBasedAnalysisInputLocation(
      @Nonnull Path classFilePath,
      @Nonnull String omittedPackageName,
      @Nonnull SourceType srcType,
      @Nonnull List<BodyInterceptor> bodyInterceptors) {
    super(classFilePath, srcType, bodyInterceptors);
    this.omittedPackageName = omittedPackageName;

    if (!Files.isRegularFile(classFilePath)) {
      throw new IllegalArgumentException("Needs to point to a regular file!");
    }

    if (Files.isDirectory(classFilePath)) {
      throw new IllegalArgumentException("Needs to point to a regular file - not to a directory.");
    }
  }

  @Override
  @Nonnull
  public Optional<JavaSootClassSource> getClassSource(@Nonnull ClassType type, @Nonnull View view) {

    if (!type.getPackageName().getName().startsWith(omittedPackageName)) {
      return Optional.empty();
    }

    return getSingleClass((JavaClassType) type, path, new AsmJavaClassProvider(view));
  }

  @Nonnull
  @Override
  public Stream<JavaSootClassSource> getClassSources(@Nonnull View view) {
    AsmJavaClassProvider classProvider = new AsmJavaClassProvider(view);
    IdentifierFactory factory = view.getIdentifierFactory();
    Path dirPath = this.path.getParent();

    final String fullyQualifiedName = fromPath(dirPath, path);

    Optional<JavaSootClassSource> javaSootClassSource =
        classProvider
            .createClassSource(this, path, factory.getClassType(fullyQualifiedName))
            .map(src -> (JavaSootClassSource) src);

    return Stream.of(javaSootClassSource.get());
  }

  @Nonnull
  protected String fromPath(@Nonnull Path baseDirPath, Path packageNamePathAndClass) {
    String str =
        FilenameUtils.removeExtension(
            packageNamePathAndClass
                .subpath(baseDirPath.getNameCount(), packageNamePathAndClass.getNameCount())
                .toString()
                .replace(packageNamePathAndClass.getFileSystem().getSeparator(), "."));

    return omittedPackageName.isEmpty() ? str : omittedPackageName + "." + str;
  }
}
