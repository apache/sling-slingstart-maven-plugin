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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;

import org.apache.felix.configurator.impl.json.JSMin;
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
import org.apache.sling.feature.io.json.FeatureJSONWriter;
import org.apache.sling.feature.modelconverter.FeatureToProvisioning;
import org.apache.sling.maven.slingstart.ModelPreprocessor.Environment;
import org.apache.sling.maven.slingstart.ModelPreprocessor.ProjectInfo;

import aQute.bnd.version.MavenVersion;

public class FeatureModelConverter {
    static final String BUILD_DIR = "provisioning/converted";

    static final String PROVISIONING_MODEL_NAME_VARIABLE = "provisioning.model.name";
    static final String PROVISIONING_RUNMODES = "provisioning.runmodes";

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
            convert(env, pi, pi.defaultProvisioningModelName);
        }
    }

    public static class FeatureFileEntry {
        public File file;
        public String runModes;
    }

    public static void convertDirectories(String featuresDirectory, MavenProject project, String defaultProvName,
            FeatureProvider fp) throws MavenExecutionException {
        final List<FeatureFileEntry> featureFiles = FeatureModelConverter.getFeatureFiles(project.getBasedir(),
                featuresDirectory);
        if (!featureFiles.isEmpty()) {
            convert(featureFiles, project, defaultProvName, fp);
        }
    }

    private static List<FeatureFileEntry> getFeatureFiles(final File baseDir, final String config) {
        final List<FeatureFileEntry> files = new ArrayList<>();
        for (final String cfg : config.split(",")) {
            final String[] directoryCfg = cfg.split("\\|");
            final String directory = directoryCfg[0].trim().replace('/', File.separatorChar);

            String runmodes = null;
            if (directoryCfg.length > 1) {
                runmodes = String.join(",", Arrays.copyOfRange(directoryCfg, 1, directoryCfg.length));
            }
            final File featuresDir = new File(baseDir, directory);
            final File[] children = featuresDir.listFiles();
            if (children != null) {
                for (final File f : children) {
                    if (f.isFile() && f.getName().endsWith(".json")) {
                        final FeatureFileEntry ff = new FeatureFileEntry();
                        ff.file = f;
                        ff.runModes = runmodes;
                        files.add(ff);
                    }
                }
            }
        }

        return files;
    }

    public static void convert(Environment env, ProjectInfo info, String defaultProvName)
            throws MavenExecutionException {
        final String config = ModelPreprocessor.nodeValue(info.plugin, "featuresDirectory", "src/main/features");
        final List<FeatureFileEntry> files = getFeatureFiles(info.project.getBasedir(), config);
        if (!files.isEmpty()) {
            try {
                convert(files, info.project, defaultProvName,
                        id -> getFeature(id, env.session, info.project, env.artifactHandlerManager, env.resolver));
            } catch (RuntimeException ex) {
                throw new MavenExecutionException(ex.getMessage(), ex);
            }
        }
    }

    static void convert(List<FeatureFileEntry> files, MavenProject project, String defaultProvName, FeatureProvider fp)
            throws MavenExecutionException {
        File processedFeaturesDir = new File(project.getBuild().getDirectory(), "features/processed");
        processedFeaturesDir.mkdirs();

        List<File> substedFiles = new ArrayList<>();
        for (FeatureFileEntry featureFile : files) {
            final File f = featureFile.file;
            File outFile = new File(processedFeaturesDir, f.getName());
            if (!outFile.exists() || outFile.lastModified() <= f.lastModified()) {
                try {
                    final String suggestedClassifier;
                    if (!f.getName().equals("feature.json")) {
                        final int lastDot = f.getName().lastIndexOf('.');
                        suggestedClassifier = f.getName().substring(0, lastDot);
                    } else {
                        suggestedClassifier = null;
                    }
                    String json = readFeatureFile(project, f, suggestedClassifier);

                    // check for prov model name
                    if (defaultProvName != null || featureFile.runModes != null) {
                        try (final Reader reader = new StringReader(json)) {
                            final Feature feature = FeatureJSONReader.read(reader, f.getAbsolutePath());
                            boolean update = false;
                            if (featureFile.runModes != null) {
                                String oldValue = feature.getVariables().get(PROVISIONING_RUNMODES);
                                String newValue;
                                if (oldValue == null) {
                                    newValue = featureFile.runModes;
                                } else {
                                    newValue = oldValue.concat(",").concat(featureFile.runModes);
                                }
                                feature.getVariables().put(PROVISIONING_RUNMODES, newValue);
                                update = true;
                            }

                            if (defaultProvName != null
                                    && feature.getVariables().get(PROVISIONING_MODEL_NAME_VARIABLE) == null) {
                                feature.getVariables().put(PROVISIONING_MODEL_NAME_VARIABLE, defaultProvName);
                                update = true;
                            }
                            if (update) {
                                try (final Writer writer = new StringWriter()) {
                                    FeatureJSONWriter.write(writer, feature);
                                    writer.flush();
                                    json = writer.toString();
                                }
                            }
                        }
                    }
                    try (final Writer fileWriter = new FileWriter(outFile)) {
                        fileWriter.write(json);
                    }
                } catch (IOException e) {
                    throw new MavenExecutionException("Problem processing feature file " + f.getAbsolutePath(), e);
                }
            }
            substedFiles.add(outFile);
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

    /**
     * Read the json file, minify it, add id if missing and replace variables
     *
     * @param file The json file
     * @return The read and minified JSON
     */
    public static String readFeatureFile(final MavenProject project, final File file,
            final String suggestedClassifier) {
        final StringBuilder sb = new StringBuilder();
        try (final Reader reader = new FileReader(file)) {
            final char[] buf = new char[4096];
            int l = 0;

            while ((l = reader.read(buf)) > 0) {
                sb.append(buf, 0, l);
            }
        } catch (final IOException io) {
            throw new RuntimeException("Unable to read feature " + file.getAbsolutePath(), io);
        }
        final String readJson = sb.toString();

        // minify JSON (remove comments)
        String json;
        try (final Writer out = new StringWriter(); final Reader in = new StringReader(readJson)) {
            final JSMin min = new JSMin(in, out);
            min.jsmin();
            json = out.toString();
        } catch (final IOException e) {
            throw new RuntimeException("Unable to read feature file " + file.getAbsolutePath(), e);
        }

        // check if "id" is set
        try (final JsonReader reader = Json.createReader(new StringReader(json))) {
            final JsonObject obj = reader.readObject();
            if (!obj.containsKey("id")) {
                final StringBuilder isb = new StringBuilder();
                isb.append(project.getGroupId());
                isb.append(':');
                isb.append(project.getArtifactId());
                isb.append(':');
                isb.append("slingosgifeature");

                if (suggestedClassifier != null) {
                    isb.append(':');
                    isb.append(suggestedClassifier);
                }
                isb.append(':');
                isb.append(project.getVersion());

                final StringWriter writer = new StringWriter();

                try (final JsonGenerator generator = Json.createGenerator(writer)) {
                    generator.writeStartObject();

                    generator.write("id", isb.toString());

                    for (final Map.Entry<String, JsonValue> entry : obj.entrySet()) {
                        generator.write(entry.getKey(), entry.getValue());
                    }
                    generator.writeEnd();
                }

                json = writer.toString();
            }
        }

        // replace variables
        return Substitution.replaceMavenVars(project, json);
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
