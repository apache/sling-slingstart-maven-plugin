/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.maven.slingstart;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.io.ArtifactManager;
import org.apache.sling.feature.io.ArtifactManagerConfig;
import org.apache.sling.feature.modelconverter.FeatureToProvisioning;
import org.apache.sling.maven.slingstart.ModelPreprocessor.Environment;
import org.apache.sling.maven.slingstart.ModelPreprocessor.ProjectInfo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class FeatureModelConverter {
    static final String BUILD_DIR = "provisioning/converted";

    public static void convert(MavenSession session, Environment env) throws MavenExecutionException {
        Map<String, ProjectInfo> projs = env.modelProjects;
        for (ProjectInfo pi : projs.values()) {
            convert(session, pi.project);
        }
    }

    private static void convert(MavenSession session, MavenProject project) throws MavenExecutionException {
        File featuresDir = new File(project.getBasedir(), "src/main/features");

        File[] files = featuresDir.listFiles();
        if (files == null || files.length == 0)
            return;

        ArtifactManager am;
        try {
            am = getArtifactManager(project, session);
        } catch (IOException ex) {
            throw new MavenExecutionException("Unable to obtain artifactManager", ex);
        }

        convert(files, project, am);
    }

    static void convert(File[] files, MavenProject project, ArtifactManager am) throws MavenExecutionException {
        File processedFeaturesDir = new File(project.getBuild().getDirectory(), "features/processed");
        processedFeaturesDir.mkdirs();

        List<File> substedFiles = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().endsWith(".json")) {
                continue;
            }

            try {
                substedFiles.add(substituteVars(project, f, processedFeaturesDir));
            } catch (IOException e) {
                throw new MavenExecutionException("Problem processing feature file " + f.getAbsolutePath(), e);
            }
        }

        File targetDir = new File(project.getBuild().getDirectory(), BUILD_DIR);
        targetDir.mkdirs();

        try {
            for (File f : substedFiles) {
                if (!f.getName().endsWith(".json")) {
                    continue;
                }
                File genFile = new File(targetDir, f.getName() + ".txt");
                FeatureToProvisioning.convert(f, genFile, am, substedFiles.toArray(new File[] {}));
            }
        } catch (Exception e) {
            throw new MavenExecutionException("Cannot convert feature files to provisioning model", e);
        }
    }

    private static File substituteVars(MavenProject project, File f, File processedFeaturesDir) throws IOException {
        File file = new File(processedFeaturesDir, f.getName());

        if (file.exists() && file.lastModified() > f.lastModified()) {
            // The file already exists, so we don't need to write it again
            return file;
        }

        try (FileWriter fw = new FileWriter(file)) {
            for (String s : Files.readAllLines(f.toPath())) {
                fw.write(replaceVars(project, s));
                fw.write(System.getProperty("line.separator"));
            }
        }
        return file;
    }

    static String replaceVars(MavenProject project, String s) {
        // There must be a better way than enumerating all these?
        s = replaceAll(s, "project.groupId", project.getGroupId());
        s = replaceAll(s, "project.artifactId", project.getArtifactId());
        s = replaceAll(s, "project.version", project.getVersion());


        Properties props = project.getProperties();
        if (props != null) {
            for (String key : props.stringPropertyNames()) {
                s = replaceAll(s, key, props.getProperty(key));
            }
        }

        return s;
    }

    private static String replaceAll(String s, String key, String value) {
        return s.replaceAll("\\Q${" + key + "}\\E", value);
    }

    private static ArtifactManager getArtifactManager(MavenProject project, MavenSession session)
            throws IOException {
        List<String> repos = new ArrayList<>();
        repos.add(session.getLocalRepository().getUrl());
        for (ArtifactRepository ar : project.getRemoteArtifactRepositories()) {
            repos.add(ar.getUrl());
        }

        final ArtifactManagerConfig amConfig = new ArtifactManagerConfig();
        amConfig.setRepositoryUrls(repos.toArray(new String[] {}));
        return ArtifactManager.getArtifactManager(amConfig);
    }
}
