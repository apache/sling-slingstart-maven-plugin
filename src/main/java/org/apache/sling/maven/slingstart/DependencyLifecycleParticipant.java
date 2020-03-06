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
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.apache.sling.maven.slingstart.ModelPreprocessor.Environment;
import org.apache.sling.maven.slingstart.ModelPreprocessor.ProjectInfo;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

/**
 * Maven lifecycle participant which adds the artifacts of the model to the dependencies.
 * This cannot happen as part of a regular Mojo (as there the dependencies have already been calculated)
 * therefore a build extension is necessary to achieve that.
 * This build extension is loaded once per version of the slingstart-maven-plugin being referenced in any of the modules.
 * @see <a href="https://issues.apache.org/jira/browse/MNG-4224">MNG-4224 - Maven Lifecycle Participant</a>
 * @see <a href="http://takari.io/book/91-maven-classloading.html#plugin-classloaders">Maven Classloading</a>
 */
@Component(role = AbstractMavenLifecycleParticipant.class)
public class DependencyLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private static final String GROUP_ID = "org.apache.sling";
    private static final String ARTIFACT_ID = "slingstart-maven-plugin";

    /**
     *  the plugin ID consists of <code>groupId:artifactId</code>, see {@link Plugin#constructKey(String, String)}
     */
    private static final String PLUGIN_ID = GROUP_ID + ":" + ARTIFACT_ID;

    @Requirement
    private Logger logger;

    @Requirement
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Used to look up Artifacts in the remote repository.
     *
     */
    @Requirement
    private ArtifactResolver resolver;

    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException {
        final Environment env = new Environment();
        env.artifactHandlerManager = artifactHandlerManager;
        env.resolver = resolver;
        env.logger = logger;
        env.session = session;

        final String version;
        try {
            version = getCurrentPluginVersion();
        } catch (IOException e) {
            throw new MavenExecutionException("Could not retrieve extension's version", e);
        }
        logger.debug("Searching for projects leveraging plugin '" + PLUGIN_ID + "' in version "+ version + "...");

        for (final MavenProject project : session.getProjects()) {
            // consider all projects where this plugin is configured
            Plugin plugin = project.getPlugin(PLUGIN_ID);
            if (plugin != null) {
                if (version.equals(plugin.getVersion())) {
                    logger.debug("Found project " + project + " leveraging " + PLUGIN_ID +" in version "+ version + ".");
                    final ProjectInfo info = new ProjectInfo();
                    info.plugin = plugin;
                    info.project = project;
                    info.defaultProvisioningModelName = ModelPreprocessor.nodeValue(plugin,
                            "defaultProvisioningModelName", null);
                    env.modelProjects.put(project.getGroupId() + ":" + project.getArtifactId(), info);
                    File processed = new File(project.getBuild().getDirectory(), "features/processed");
                    try {
                        if ( processed.exists() ) {
                            FileUtils.forceDelete(processed);
                        }
                    } catch (IOException e) {
                        throw new MavenExecutionException("Failed to delete: " + processed.getPath(), e);
                    }
                } else {
                    logger.debug("Skipping project " + project + " leveraging " + PLUGIN_ID +" in another version "+ project.getVersion() + ".");
                }
            }
        }
        FeatureModelConverter.convert(env);
        new ModelPreprocessor().addDependencies(env);
    }

    /**
     * Retrieves the version of the encapsulating Mojo by evaluating the {@code pom.properties} loaded via the extension classloader
     * @throws IOException
     * @see <a href="https://maven.apache.org/shared/maven-archiver/#pom-properties-content">Maven Archiver - pom.properties</a>
     */
    static final String getCurrentPluginVersion() throws IOException {
        final String pomPropertiesFile = String.format("/META-INF/maven/%s/%s/pom.properties", GROUP_ID, ARTIFACT_ID);
        try (InputStream inputStream = DependencyLifecycleParticipant.class.getResourceAsStream(pomPropertiesFile)) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not find '" + pomPropertiesFile + "' via classloader '" + DependencyLifecycleParticipant.class.getClassLoader() + "'");
            }
            final Properties properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty("version");
        }
    }
}
