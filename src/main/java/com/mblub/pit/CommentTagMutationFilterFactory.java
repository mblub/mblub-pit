package com.mblub.pit;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.pitest.classinfo.ClassInfo;
import org.pitest.classinfo.ClassName;
import org.pitest.classpath.CodeSource;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.filter.MutationFilter;
import org.pitest.mutationtest.filter.MutationFilterFactory;

import com.mblub.util.io.stream.StreamingLineNumberReader;
import com.mblub.util.io.stream.StreamingLineNumberReader.NumberedLine;

public class CommentTagMutationFilterFactory implements MutationFilterFactory {

  private static final String SUPPRESS_ALL = "ALL";
  private static final Pattern SUPPRESSION_COMMENT_PATTERN = Pattern.compile("//\\s*@suppressMutation\\((.*)\\)");
  private static final String GREGOR_MUTATOR_PREFIX = "org.pitest.mutationtest.engine.gregor.mutators.";

  @Override
  public String description() {
    return "CommentTagMutationFilterFactory (v26)";
  }

  @Override
  public MutationFilter createFilter(Properties factoryProperties, CodeSource source, int maxMutationsPerClass) {
    Map<String, List<NumberedLine>> suppressionTargetsByFile = new TreeMap<>();
    source.getCode().stream().filter(ClassInfo::isTopLevelClass).forEach(ci -> {
      String[] sourceFilePathElements = ci.getName().asJavaName().split("\\.");
      sourceFilePathElements[sourceFilePathElements.length - 1] = ci.getSourceFileName();
      List<NumberedLine> suppressionTargets = StreamingLineNumberReader
              .newReader(Paths.get(factoryProperties.getProperty("sourceDirectory"), sourceFilePathElements))
              .numberedLines().map(this::getSuppressionTarget).filter(Optional::isPresent).map(Optional::get)
              .collect(Collectors.toList());
      if (!suppressionTargets.isEmpty()) {
        suppressionTargetsByFile.put(ci.getSourceFileName(), suppressionTargets);
      }
    });
    System.out.println("CommentTagMutationFilterFactory; suppression targets: " + suppressionTargetsByFile);
    return new PropertiesMutationFilter(
            source.getCode().stream().filter(ci -> suppressionTargetsByFile.containsKey(ci.getSourceFileName()))
                    .collect(toMap(ClassInfo::getName, ci -> suppressionTargetsByFile.get(ci.getSourceFileName())
                            .stream().filter(nl -> ci.isCodeLine(nl.getNumber())).collect(toList()))));
  }

  private Optional<NumberedLine> getSuppressionTarget(NumberedLine numberedLine) {
    Matcher matcher = SUPPRESSION_COMMENT_PATTERN.matcher(numberedLine.getLine().trim());
    return (matcher.matches() ? Optional.of(new NumberedLine(numberedLine.getNumber() + 1, matcher.group(1)))
            : Optional.empty());
  }

  public static class PropertiesMutationFilter implements MutationFilter {

    private Map<ClassName, List<NumberedLine>> suppressionTargetsByClass;

    public PropertiesMutationFilter(Map<ClassName, List<NumberedLine>> suppressionTargetsByClass) {
      this.suppressionTargetsByClass = suppressionTargetsByClass;
      System.out.println("Generated filter with suppressionTargetsByClass: " + suppressionTargetsByClass);
    }

    @Override
    public Collection<MutationDetails> filter(Collection<MutationDetails> detailsCollection) {
      return detailsCollection.stream().filter(this::isNotSuppressed).collect(toList());
    }

    private boolean isNotSuppressed(MutationDetails details) {
      List<NumberedLine> suppressionTargets = suppressionTargetsByClass.get(details.getClassName());
      if (suppressionTargets == null || suppressionTargets.isEmpty()) {
        return true;
      }
      boolean isSuppressed = suppressionTargets.stream().anyMatch(t -> t.getNumber() == details.getLineNumber()
              && (SUPPRESS_ALL.equals(t.getLine()) || details.getMutator().endsWith(t.getLine())));
      if (isSuppressed) {
        reportSuppression(System.out, details);
      }
      return !isSuppressed;
    }

    private void reportSuppression(PrintStream ps, MutationDetails details) {
      ps.print("Suppressing mutation in ");
      ps.print(details.getClassName());
      ps.print(", line ");
      ps.print(details.getLineNumber());
      ps.print(": ");
      ps.print(details.getMutator().startsWith(GREGOR_MUTATOR_PREFIX)
              ? details.getMutator().substring(GREGOR_MUTATOR_PREFIX.length()) : details.getMutator());
      ps.print(" (would have ");
      ps.print(details.getDescription());
      ps.print(").");
      ps.println();
      ps.flush();
    }
  }
}
