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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.stream.StreamSupport;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.utils.WriterFactory;
import org.apache.sling.provisioning.model.Artifact;
import org.apache.sling.provisioning.model.Model;
import org.apache.sling.provisioning.model.ModelUtility;

/**
 * Attaches a dependencies pom as project artifact 
 */
@Mojo(
        name = "attach-dependencies-pom",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
    )
public class AttachDependenciesPom extends AbstractSlingStartMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        Model model = ProjectHelper.getRawModel(this.project);
        if (usePomVariables) {
            model = ModelUtility.applyVariables(model, new PomVariableResolver(project));
        }
        if (usePomDependencies) {
            model = ModelUtility.applyArtifactVersions(model, new PomArtifactVersionResolver(project, allowUnresolvedPomDependencies));
        }

        // write the model archive
        final File outputFile = new File(this.project.getBuild().getDirectory(), project.getBuild().getFinalName() + "-" + BuildConstants.CLASSIFIER_DEPENDENCIES + "." + BuildConstants.TYPE_POM);
        outputFile.getParentFile().mkdirs();
        
        try {
            try ( Writer wf = WriterFactory.newXmlWriter(outputFile) ) {
                org.apache.maven.model.Model mavenModel = new org.apache.maven.model.Model();
                mavenModel.setName(project.getName() + " (Dependencies)");
                mavenModel.setGroupId(project.getGroupId());
                mavenModel.setArtifactId(project.getArtifactId());
                mavenModel.setVersion(project.getVersion());
                mavenModel.setDependencyManagement(new DependencyManagement());

                model.getFeatures().stream()
                    .flatMap( d -> d.getRunModes().stream() )
                    .flatMap( r -> r.getArtifactGroups().stream() )
                    .flatMap( ag -> StreamSupport.stream(ag.spliterator(), false))
                    .forEach( a -> addDependency(a, mavenModel) );
                
                new MavenXpp3Writer().write( wf, mavenModel );
            }
                
        } catch (IOException e) {
            throw new MojoExecutionException("Failed writing dependencies POM", e);
        }

        // attach it as an additional artifact
        projectHelper.attachArtifact(project, BuildConstants.TYPE_POM,
                    BuildConstants.CLASSIFIER_DEPENDENCIES, outputFile);
    }

    private void addDependency(Artifact a, org.apache.maven.model.Model model) {
        Dependency dep = new Dependency();
        dep.setScope("provided");
        dep.setGroupId(a.getGroupId());
        dep.setArtifactId(a.getArtifactId());
        dep.setVersion(a.getVersion());

        model.getDependencyManagement().addDependency(dep);
    }

}
