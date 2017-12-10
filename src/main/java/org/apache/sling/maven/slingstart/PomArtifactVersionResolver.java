/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.maven.slingstart;

import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.sling.provisioning.model.Artifact;
import org.apache.sling.provisioning.model.ModelUtility.ArtifactVersionResolver;

/**
 * Provisioning artifact resolver that tries to resolve artifacts in provisioning file without version (LATEST)
 * against the dependencies defined in the maven project.
 * The following sections in the Maven project are considered during resolving the version:
 * <ol>
 *   <li>The project's artifact itself</li>
 *   <li>The project's dependencies</li>
 *   <li>The project's dependencyManagement</li>
 * </ol>
 */
public class PomArtifactVersionResolver implements ArtifactVersionResolver {

    private final MavenProject project;
    private final boolean allowUnresolvedPomDependencies;
    
    /**
     * @param project Maven project
     * @param allowUnresolvedPomDependencies If true, no exception is thrown when resolving is not possible
     */
    public PomArtifactVersionResolver(MavenProject project, boolean allowUnresolvedPomDependencies) {
        this.project = project;
        this.allowUnresolvedPomDependencies = allowUnresolvedPomDependencies;
    }
    
    @Override
    public String resolve(Artifact artifact) {
        if (artifactEquals(project.getArtifact(), artifact)) {
            return project.getVersion();
        }
        
        String version = findVersion(project.getDependencies(), artifact);
        if (version != null) {
            return version;
        }
        
        if (project.getDependencyManagement() != null) {
            version = findVersion(project.getDependencyManagement().getDependencies(), artifact);
            if (version != null) {
                return version;
            }
        }
        if (allowUnresolvedPomDependencies) {
            return null;
        }
        else {
            throw new IllegalArgumentException("Unable to resolve dependency: " + artifact.toMvnUrl());
        }
    }
    
    private String findVersion(List<Dependency> dependencies, Artifact artifact) {
        if (dependencies != null) {
            for (Dependency dependency : dependencies) {
                if (artifactEquals(dependency, artifact)) {
                    return dependency.getVersion();
                }
            }
        }
        return null;
    }
    
    static boolean artifactEquals(Dependency dependency, Artifact artifact) {
        return StringUtils.equals(dependency.getGroupId(), artifact.getGroupId())
                && StringUtils.equals(dependency.getArtifactId(), artifact.getArtifactId())
                && StringUtils.equals(dependency.getClassifier(), artifact.getClassifier())
                && StringUtils.equals(normalizeType(dependency.getType()), normalizeType(artifact.getType()));
    }
    
    static boolean artifactEquals(org.apache.maven.artifact.Artifact artifact, Artifact artifact2) {
        return StringUtils.equals(artifact.getGroupId(), artifact2.getGroupId())
                && StringUtils.equals(artifact.getArtifactId(), artifact2.getArtifactId())
                && StringUtils.equals(artifact.getClassifier(), artifact2.getClassifier())
                && StringUtils.equals(normalizeType(artifact.getType()), normalizeType(artifact2.getType()));
    }
    
    static String normalizeType(String type) {
        // bundles are often referred to with type "jar" in the provisioning model.
        // especially when leaving out the version you cannot even specify a type different from the default "jar"
        if (type.equals("bundle")) {
            return "jar";
        } else {
            return type;
        }
    }
}
