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
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;

import org.apache.commons.io.FileUtils;
import org.apache.felix.cm.json.Configurations;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;

public class JSONFeatures {

    /**
     * Read the feature and add the {@code id} attribute if missing
     * @param reader The reader
     * @param optionalId The artifact id to use if the {@code id} attribute is missing
     * @param location The location
     * @return The feature as a string
     * @throws IOException If reading fails
     */
    public static String read(final Reader reader, final ArtifactId optionalId, final String location)
    throws IOException {
        JsonObject featureObj;
        try (JsonReader jsonReader = Json.createReader(Configurations.jsonCommentAwareReader(reader))) {
            featureObj = jsonReader.readObject();
            if ( !featureObj.containsKey("id") ) {
                final JsonObjectBuilder job = Json.createObjectBuilder();
                job.add("id", optionalId.toMvnId());
                for(final Map.Entry<String, JsonValue> prop : featureObj.entrySet() ) {
                    job.add(prop.getKey(), prop.getValue());
                }
                featureObj = job.build();
            }
        } catch ( final JsonException je) {
            throw new IOException(location.concat(" : " ).concat(je.getMessage()), je);
        }

        try ( final StringWriter writer = new StringWriter()) {
            try ( final JsonWriter jsonWriter = Json.createWriter(writer)) {
                jsonWriter.writeObject(featureObj);
            }
            return writer.toString();
        }
    }

    private static final String FILE_PREFIX = "@file";
    
   /**
     * Check for extensions of type text and if they reference a file
     */
	public static void handleExtensions(final Feature feature, final File file) throws IOException {
        for(final Extension ext : feature.getExtensions()) {
            if ( ext.getType() == ExtensionType.TEXT && ext.getText().startsWith(FILE_PREFIX)) {
                final int pos = file.getName().lastIndexOf(".");
                final String baseName = pos == -1 ? file.getName() : file.getName().substring(0, pos);
                final String fileName;
                if ( FILE_PREFIX.equals(ext.getText()) ) {
                    fileName = baseName.concat("-").concat(ext.getName()).concat(".txt");
                } else {
                    if ( !ext.getText().substring(FILE_PREFIX.length()).startsWith(":") ) {
                        throw new IOException("Invalid file reference: " + ext.getText());
                    }
                    fileName = baseName.concat("-").concat(ext.getText().substring(FILE_PREFIX.length() + 1));
                }
                final File txtFile = new File(file.getParentFile(), fileName);
                if ( !txtFile.exists() || !txtFile.isFile()) {
                    throw new IOException("Extension text file " + txtFile.getAbsolutePath() + " not found.");
                }
                final String contents = FileUtils.readFileToString(txtFile, StandardCharsets.UTF_8);
                ext.setText(contents);
            }
        }
    }
}
