/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.maven.slingstart;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.sling.feature.io.ArtifactManager;
import org.apache.sling.feature.io.ArtifactManagerConfig;
import org.apache.sling.feature.modelconverter.FeatureToProvisioning;
import org.codehaus.plexus.archiver.manager.ArchiverManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mojo(
        name = "generate-resources",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class GenerateResourcesMojo extends AbstractSlingStartMojo {
    @Parameter(defaultValue="${basedir}/src/main/features")
    private File featuresDirectory;

    /**
     * To look up Archiver/UnArchiver implementations
     */
    @Component
    private ArchiverManager archiverManager;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Used to look up Artifacts in the remote repository.
     *
     */
    @Component
    private ArtifactResolver resolver;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    protected MojoExecution mojoExecution;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File[] featureFiles = featuresDirectory.listFiles();
        if (featureFiles == null)
            return;

        File targetDir = new File(project.getBuild().getDirectory(), FeatureModelConverter.BUILD_DIR);
        targetDir.mkdirs();

        try {
            ArtifactManager am = getArtifactManager();
            List<File> files = Arrays.asList(featureFiles);
            for (File f : files) {
                if (!f.getName().endsWith(".json")) {
                    continue;
                }

                File genFile = new File(targetDir, f.getName() + ".txt");
                FeatureToProvisioning.convert(f, genFile, am);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Cannot convert feature files to provisioning model", e);
        }
    }

    private ArtifactManager getArtifactManager() throws IOException {
        List<String> repos = new ArrayList<>();
        repos.add(mavenSession.getLocalRepository().getUrl());
        for (ArtifactRepository ar : project.getRemoteArtifactRepositories()) {
            repos.add(ar.getUrl());
        }

        final ArtifactManagerConfig amConfig = new ArtifactManagerConfig();
        amConfig.setRepositoryUrls(repos.toArray(new String[] {}));
        return ArtifactManager.getArtifactManager(amConfig);
    }
}
