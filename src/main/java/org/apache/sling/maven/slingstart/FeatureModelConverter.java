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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.apache.sling.feature.modelconverter.FeatureToProvisioning;
import org.apache.sling.maven.slingstart.ModelPreprocessor.Environment;
import org.apache.sling.maven.slingstart.ModelPreprocessor.ProjectInfo;

import aQute.bnd.version.MavenVersion;

public class FeatureModelConverter {
    static final String BUILD_DIR = "provisioning/converted";

    public static Feature getFeature(ArtifactId id, MavenSession session, MavenProject project, ArtifactHandlerManager manager, ArtifactResolver resolver) {
        try {
            File file = ModelUtils.getArtifact(project, session, manager, resolver, id.getGroupId(), id.getArtifactId(), id.getVersion(), id.getType(), id.getClassifier()).getFile();
            try (Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
                return FeatureJSONReader.read(reader, file.toURI().toURL().toString());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void convert(Environment env) throws MavenExecutionException {
        for (ProjectInfo pi : env.modelProjects.values()) {
            convert(env, pi);
        }
    }

    public static List<File> getFeatureFiles(final File baseDir, final String config) {
        final List<File> files = new ArrayList<>();
        for (final String cfg : config.split(",")) {
            final File featuresDir = new File(baseDir, cfg.trim().replace('/', File.separatorChar));
            final File[] children = featuresDir.listFiles();
            if (children != null) {
                for (final File f : children) {
                    if (f.isFile() && f.getName().endsWith(".json")) {
                        files.add(f);
                    }
                }
            }
        }

        if (files.isEmpty()) {
            return null;
        }
        return files;
    }

    public static void convert(Environment env, ProjectInfo info) throws MavenExecutionException {
        final String config = ModelPreprocessor.nodeValue(info.plugin, "featuresDirectory", "src/main/features");
        final List<File> files = getFeatureFiles(info.project.getBasedir(), config);
        if (files == null) {
            return;
        }

        try {
            convert(files, info.project,
                    id -> getFeature(id, env.session, info.project, env.artifactHandlerManager, env.resolver));
        } catch (RuntimeException ex) {
            throw new MavenExecutionException(ex.getMessage(), ex);
        }

    }

    static void convert(List<File> files, MavenProject project, FeatureProvider fp) throws MavenExecutionException {
        File processedFeaturesDir = new File(project.getBuild().getDirectory(), "features/processed");
        processedFeaturesDir.mkdirs();

        List<File> substedFiles = new ArrayList<>();
        for (File f : files) {
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
                File genFile = new File(targetDir, f.getName() + ".txt");
                FeatureToProvisioning.convert(f, genFile, fp, substedFiles.toArray(new File[] {}));
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
        s = replaceAll(s, "project.osgiVersion", getOSGiVersion(project.getVersion()));

        s = replaceProperties(System.getProperties(), s);
        s = replaceProperties(project.getProperties(), s);

        return s;
    }

    private static String replaceProperties(Properties props, String s) {
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

    /**
     * Remove leading zeros for a version part
     */
    private static String cleanVersionString(final String version) {
        final StringBuilder sb = new StringBuilder();
        boolean afterDot = false;
        for(int i=0;i<version.length(); i++) {
            final char c = version.charAt(i);
            if ( c == '.' ) {
                if (afterDot == true ) {
                    sb.append('0');
                }
                afterDot = true;
                sb.append(c);
            } else if ( afterDot && c == '0' ) {
                // skip
            } else if ( afterDot && c == '-' ) {
                sb.append('0');
                sb.append(c);
                afterDot = false;
            } else {
                afterDot = false;
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String getOSGiVersion(final String version) {
        final DefaultArtifactVersion dav = new DefaultArtifactVersion(cleanVersionString(version));
        final StringBuilder sb = new StringBuilder();
        sb.append(dav.getMajorVersion());
        sb.append('.');
        sb.append(dav.getMinorVersion());
        sb.append('.');
        sb.append(dav.getIncrementalVersion());
        if ( dav.getQualifier() != null ) {
            sb.append('.');
            sb.append(dav.getQualifier());
        }
        final MavenVersion mavenVersion = new MavenVersion(sb.toString());
        return mavenVersion.getOSGiVersion().toString();
    }
}
