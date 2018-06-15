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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FeatureModelConverter {
    static final String BUILD_DIR = "provisioning/converted";

    public void convert(MavenSession session, Environment env) throws MavenExecutionException {
        Map<String, ProjectInfo> projs = env.modelProjects;
        for (ProjectInfo pi : projs.values()) {
            convert(session, pi.project);
        }
    }

    private void convert(MavenSession session, MavenProject project) throws MavenExecutionException {
        File featuresDir = new File(project.getBasedir(), "src/main/features");

        File[] files = featuresDir.listFiles();
        List<File> featureFiles;
        if (files != null) {
            featureFiles = Arrays.asList(files);
        } else {
            featureFiles = Collections.emptyList();
        }

        if (featureFiles.size() == 0)
            return;

        File targetDir = new File(project.getBuild().getDirectory(), BUILD_DIR);
        targetDir.mkdirs();

        try {
            ArtifactManager am = getArtifactManager(project, session);
            for (File f : files) {
                if (!f.getName().endsWith(".json")) {
                    continue;
                }
                File genFile = new File(targetDir, f.getName() + ".txt");
                FeatureToProvisioning.convert(f, genFile, am, files);
            }
        } catch (Exception e) {
            throw new MavenExecutionException("Cannot convert feature files to provisioning model", e);
        }
    }

    private ArtifactManager getArtifactManager(MavenProject project, MavenSession session)
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
