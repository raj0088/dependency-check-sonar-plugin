/*
 * Dependency-Check Plugin for SonarQube
 * Copyright (C) 2015-2019 dependency-check
 * philipp.dallig@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.dependencycheck.reason;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.dependencycheck.base.DependencyCheckUtils;
import org.sonar.dependencycheck.parser.element.Confidence;
import org.sonar.dependencycheck.parser.element.Dependency;
import org.sonar.dependencycheck.parser.element.Identifier;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

public class GradleDependencyReason extends DependencyReason {

    private final InputFile buildGradle;
    private String content;
    private Map<Dependency, TextRangeConfidence> dependencyMap;

    private static final Logger LOGGER = Loggers.get(MavenDependencyReason.class);

    public GradleDependencyReason(@NonNull InputFile buildGradle) {
        super(buildGradle);
        this.buildGradle = buildGradle;
        dependencyMap = new HashMap<>();
        content = "";
        try {
            content = buildGradle.contents();
        } catch (IOException e) {
            LOGGER.warn("Could not read build.gradle file");
        }
    }

    @Override
    @NonNull
    public TextRangeConfidence getBestTextRange(@NonNull Dependency dependency) {
        if (dependencyMap.containsKey(dependency)) {
            return dependencyMap.get(dependency);
        } else {
            Optional<Identifier> gradleIdentifier = DependencyCheckUtils.getMavenIdentifier(dependency);
            if (gradleIdentifier.isPresent()) {
                tryArtifactMatch(gradleIdentifier.get()).ifPresent(textrange -> dependencyMap.put(dependency, textrange));
            } else {
                LOGGER.warn("No artifactId found for Dependency {}", dependency.getFileName());
            }
            if (!dependencyMap.containsKey(dependency) || dependencyMap.get(dependency) == null) {
                LOGGER.debug("We doesn't find a TextRange for {} in {}. We link to first line with {} confidence", dependency.getFileName(), buildGradle, Confidence.LOW);
                dependencyMap.put(dependency, new TextRangeConfidence(buildGradle.selectLine(1), Confidence.LOW));
            }
        }
        return dependencyMap.get(dependency);
    }

    /**
     *
     * This Methods tries to find the best TextRange for an given Artifactid in the build.gradle file
     * If the line parser doesn't find anything we return the TextRange with linenumer 1
     * TODO: It would be nice to have something similar to the command "gradlew app:dependencies"
     * At the moment a simple line parser without transitive dependencies
     *
     * @param artifactid
     * @return TextRange if found in pom, else null
     */
    private Optional<TextRangeConfidence> tryArtifactMatch(Identifier gradleIdentifier) {
        Optional<String> packageArtefact = Identifier.getPackageArtefact(gradleIdentifier);
        if (packageArtefact.isPresent()) {
            // packageArtefact has something like struts/struts@1.2.8
            String[] gradleIdentifierSplit = packageArtefact.get().split("@");
            gradleIdentifierSplit = gradleIdentifierSplit[0].split("/");
            String artefactId = gradleIdentifierSplit[1];
            try (final Scanner scanner = new Scanner(content)) {
                int linenumber = 0;
                while (scanner.hasNextLine()) {
                    final String lineFromFile = scanner.nextLine();
                    linenumber++;
                    if (lineFromFile.contains(artefactId)) {
                        LOGGER.debug("We found {} in {} on line {}", artefactId, buildGradle, linenumber);
                        return Optional.of(new TextRangeConfidence(buildGradle.selectLine(linenumber), Confidence.MEDIUM));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if we have a pom File and this pom file is readable and has content Pom Files contains the in maven builds the
     * dependencies
     */
    @Override
    public boolean isReasonable() {
        return buildGradle != null && content != null && !content.isEmpty();
    }

    /**
     * returns pom file
     */
    @Override
    @CheckForNull
    public InputComponent getInputComponent() {
        return buildGradle;
    }
}
