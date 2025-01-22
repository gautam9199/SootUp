package sootup.java.bytecode.frontend.inputlocation;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.commons.io.FilenameUtils;
import sootup.core.IdentifierFactory;
import sootup.core.frontend.ClassProvider;
import sootup.core.frontend.SootClassSource;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.inputlocation.FileType;
import sootup.core.model.SourceType;
import sootup.core.transform.BodyInterceptor;
import sootup.core.util.PathUtils;
import sootup.core.util.StreamUtils;
import sootup.java.core.*;
import sootup.java.core.types.JavaClassType;

/*-
 * #%L
 * Soot
 * %%
 * Copyright (C) 2018-2020 Manuel Benz, Christian Brüggemann, Markus Schmidt and others
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

/**
 * Base class for {@link PathBasedAnalysisInputLocation}s that can be located by a {@link Path}
 * object.
 *
 * @author Manuel Benz created on 22.05.18
 * @author Kaustubh Kelkar updated on 30.07.2020
 */
public abstract class PathBasedAnalysisInputLocation implements AnalysisInputLocation {
  @Nonnull protected Path path;
  @Nonnull protected Collection<Path> ignoredPaths;
  @Nonnull protected final SourceType sourceType;
  @Nonnull protected final List<BodyInterceptor> bodyInterceptors;

  protected PathBasedAnalysisInputLocation(@Nonnull Path path, @Nonnull SourceType srcType) {
    this(path, srcType, Collections.emptyList());
  }

  protected PathBasedAnalysisInputLocation(
      @Nonnull Path path,
      @Nonnull SourceType srcType,
      @Nonnull List<BodyInterceptor> bodyInterceptors) {
    this(path, srcType, bodyInterceptors, Collections.emptyList());
  }

  protected PathBasedAnalysisInputLocation(
      @Nonnull Path path,
      @Nonnull SourceType srcType,
      @Nonnull List<BodyInterceptor> bodyInterceptors,
      @Nonnull Collection<Path> ignoredPaths) {
    this.path = path;
    this.ignoredPaths =
        ignoredPaths.stream()
            .map(Path::toAbsolutePath)
            .collect(Collectors.toCollection(HashSet::new));
    this.sourceType = srcType;
    this.bodyInterceptors = bodyInterceptors;

    if (!Files.exists(path)) {
      throw new IllegalArgumentException("The provided path '" + path + "' does not exist.");
    }
  }

  @Override
  @Nonnull
  public SourceType getSourceType() {
    return sourceType;
  }

  @Override
  @Nonnull
  public List<BodyInterceptor> getBodyInterceptors() {
    return bodyInterceptors;
  }

  @Nonnull
  public static PathBasedAnalysisInputLocation create(
      @Nonnull Path path, @Nonnull SourceType sourceType) {
    return create(path, sourceType, Collections.emptyList());
  }

  @Nonnull
  public static PathBasedAnalysisInputLocation create(
      @Nonnull Path path,
      @Nonnull SourceType srcType,
      @Nonnull List<BodyInterceptor> bodyInterceptors) {
    return create(path, srcType, bodyInterceptors, Collections.emptyList());
  }

  @Nonnull
  public static PathBasedAnalysisInputLocation create(
      @Nonnull Path path,
      @Nonnull SourceType srcType,
      @Nonnull List<BodyInterceptor> bodyInterceptors,
      @Nonnull Collection<Path> ignoredPaths) {

    if (ignoredPaths.stream()
        .anyMatch(ignoPath -> path.toString().startsWith(ignoPath.toString()))) {
      throw new IllegalArgumentException(
          "The Path for the AnalysisInputLocation is in the ignored paths.");
    }

    if (Files.isDirectory(path)) {
      return new DirectoryBasedAnalysisInputLocation(path, srcType, bodyInterceptors, ignoredPaths);
    } else if (PathUtils.isArchive(path)) {
      if (PathUtils.hasExtension(path, FileType.JAR)) {
        return new ArchiveBasedAnalysisInputLocation(path, srcType, bodyInterceptors, ignoredPaths);
      } else if (PathUtils.hasExtension(path, FileType.WAR)) {
        try {
          return new WarArchiveAnalysisInputLocation(path, srcType, bodyInterceptors, ignoredPaths);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
    throw new IllegalArgumentException(
        "Path '"
            + path.toAbsolutePath()
            + "' has to be pointing to the root of a class container, e.g. directory, jar, zip, apk, war etc.");
  }

  /** returns a resource that must be closed! */
  @Nonnull
  protected Stream<JavaSootClassSource> walkDirectory(
      @Nonnull Path dirPath,
      @Nonnull IdentifierFactory factory,
      @Nonnull ClassProvider classProvider) {

    final FileType handledFileType = classProvider.getHandledFileType();
    final String moduleInfoFilename = JavaModuleIdentifierFactory.MODULE_INFO_FILE + ".class";
    try {
      return Files.walk(dirPath)
          .filter(
              filePath ->
                  PathUtils.hasExtension(filePath, handledFileType)
                      && !filePath.toString().endsWith(moduleInfoFilename)
                      && ignoredPaths.stream()
                          .noneMatch(p -> filePath.toString().startsWith(p.toString())))
          .flatMap(
              p -> {
                final String fullyQualifiedName = fromPath(dirPath, p);

                return StreamUtils.optionalToStream(
                    classProvider.createClassSource(
                        this, p, factory.getClassType(fullyQualifiedName)));
              })
          .map(src -> (JavaSootClassSource) src);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Nonnull
  protected String fromPath(@Nonnull Path baseDirPath, Path packageNamePathAndClass) {
    return FilenameUtils.removeExtension(
        packageNamePathAndClass
            .subpath(baseDirPath.getNameCount(), packageNamePathAndClass.getNameCount())
            .toString()
            .replace(packageNamePathAndClass.getFileSystem().getSeparator(), "."));
  }

  @Nonnull
  protected Optional<JavaSootClassSource> getClassSourceInternal(
      @Nonnull JavaClassType signature, @Nonnull Path path, @Nonnull ClassProvider classProvider) {

    Path pathToClass =
        path.resolve(
            path.getFileSystem()
                .getPath(
                    signature.getFullyQualifiedName().replace('.', '/')
                        + classProvider.getHandledFileType().getExtensionWithDot()));

    if (!Files.exists(pathToClass)) {
      return Optional.empty();
    }

    Optional<? extends SootClassSource> classSource =
        classProvider.createClassSource(this, pathToClass, signature);

    return classSource.map(src -> (JavaSootClassSource) src);
  }

  protected Optional<JavaSootClassSource> getSingleClass(
      @Nonnull JavaClassType signature, @Nonnull Path path, @Nonnull ClassProvider classProvider) {

    Path pathToClass = Paths.get(path.toString());

    Optional<? extends SootClassSource> classSource =
        classProvider.createClassSource(this, pathToClass, signature);

    return classSource.map(src -> (JavaSootClassSource) src);
  }
}
