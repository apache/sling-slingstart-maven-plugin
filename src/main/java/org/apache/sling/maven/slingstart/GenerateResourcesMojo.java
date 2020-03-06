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

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.manager.ArchiverManager;

@Mojo(
        name = "generate-resources",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class GenerateResourcesMojo extends AbstractSlingStartMojo {
    /**
     * Comma separated list of directories. Each directory entry can be followed by
     * the "|" character and a run mode. More run modes can be added using the same
     * mechanism after the first run mode. These run modes are then used as
     * additional run modes for the provisioning models.
     */
    @Parameter(defaultValue = "src/main/features")
    private String featuresDirectory;

    @Parameter
    private String defaultProvisioningModelName;

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
    public void execute() throws MojoExecutionException {
        try {
            FeatureModelConverter.convertDirectories(this.featuresDirectory, project, defaultProvisioningModelName,
                    id -> FeatureModelConverter.getFeature(id,
                    mavenSession,
                    project, artifactHandlerManager, resolver));
        } catch (MavenExecutionException e) {
            throw new MojoExecutionException("Cannot convert feature files to provisioning model.", e);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Problem obtaining artifact manager.", e);
        }
    }
}
