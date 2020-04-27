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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.apache.sling.maven.slingstart.ModelPreprocessor.Environment;
import org.apache.sling.maven.slingstart.ModelPreprocessor.ProjectInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class FeatureModelConverterTest {
    private File tempDir;

    @Before
    public void setup() throws Exception {
        tempDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
    }

    @After
    public void tearDown() throws Exception {
        // Delete the temp dir again
        delTree(tempDir);
        tempDir = null;
    }

    private void delTree(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delTree(c);
        }
        if (!f.delete()) {
            throw new FileNotFoundException("Cannot delete: " + f);
        }
    }

    @Test
    public void testConvert() throws Exception {
        File f = new File(System.getProperty("user.home") + File.separatorChar + ".m2");
        ArtifactRepository localRepo = Mockito.mock(ArtifactRepository.class);
        Mockito.when(localRepo.getUrl()).thenReturn(f.toURI().toURL().toString());

        MavenSession session = Mockito.mock(MavenSession.class);
        Mockito.when(session.getLocalRepository()).thenReturn(localRepo);

        URL url = getClass().getResource("/features2/src/main/features/boot_gav.json");
        File projBaseDir = new File(url.toURI())
                .getParentFile().getParentFile().getParentFile().getParentFile();

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory()).thenReturn(tempDir.getAbsolutePath());

        MavenProject proj = Mockito.mock(MavenProject.class);
        Mockito.when(proj.getBasedir()).thenReturn(projBaseDir);
        Mockito.when(proj.getBuild()).thenReturn(build);
        Mockito.when(proj.getGroupId()).thenReturn("g");
        Mockito.when(proj.getArtifactId()).thenReturn("a");
        Mockito.when(proj.getVersion()).thenReturn("1.0");
        final Properties projProps = new Properties();
        Mockito.when(proj.getProperties()).thenReturn(projProps);

        ProjectInfo pi = new ProjectInfo();
        pi.project = proj;

        Environment env = new Environment();
        env.modelProjects.put("xyz", pi);
        env.session = session;

        FeatureModelConverter.convert(env);

        File expectedFile = new File(tempDir,
                "provisioning" + File.separatorChar + "converted" + File.separatorChar + "boot_gav.json.txt");
        assertTrue(expectedFile.exists());
        assertTrue(expectedFile.length() > 0);
    }

    @Test
    public void testConvertWithIncludes() throws Exception {
        File f = new File(System.getProperty("user.home") + File.separatorChar + ".m2");
        ArtifactRepository localRepo = Mockito.mock(ArtifactRepository.class);
        Mockito.when(localRepo.getUrl()).thenReturn(f.toURI().toURL().toString());

        MavenSession session = Mockito.mock(MavenSession.class);
        Mockito.when(session.getLocalRepository()).thenReturn(localRepo);

        URL url = getClass().getResource("/features3");
        File projBaseDir = new File(url.toURI());

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory()).thenReturn(tempDir.getAbsolutePath());

        MavenProject proj = Mockito.mock(MavenProject.class);
        Mockito.when(proj.getBasedir()).thenReturn(projBaseDir);
        Mockito.when(proj.getBuild()).thenReturn(build);
        Mockito.when(proj.getGroupId()).thenReturn("generated");
        Mockito.when(proj.getArtifactId()).thenReturn("a");
        Mockito.when(proj.getVersion()).thenReturn("0.0.1-SNAPSHOT");
        final Properties projProps = new Properties();
        Mockito.when(proj.getProperties()).thenReturn(projProps);

        ProjectInfo pi = new ProjectInfo();
        pi.project = proj;

        Environment env = new Environment();
        env.modelProjects.put("xyz", pi);
        env.session = session;

        FeatureModelConverter.convert(env);

        File simpleProvFile = new File(tempDir,
                "provisioning" + File.separatorChar + "converted" + File.separatorChar + "simple.json.txt");
        String simpleProv = new String(Files.readAllBytes(simpleProvFile.toPath()));
        assertTrue(simpleProv.contains("org.apache.aries/org.apache.aries.util/1.1.3"));
        assertFalse(simpleProv.contains("org.apache.sling/org.apache.sling.commons.log/5.1.0"));

        File inheritsProvFile = new File(tempDir,
                "provisioning" + File.separatorChar + "converted" + File.separatorChar + "simple_inherits.json.txt");
        String inheritsProv = new String(Files.readAllBytes(inheritsProvFile.toPath()));
        assertTrue(inheritsProv.contains("org.apache.aries/org.apache.aries.util/1.1.3"));
        assertTrue(inheritsProv.contains("org.apache.sling/org.apache.sling.commons.log/5.1.0"));
    }

    @Test
    public void testFeatureDirectoryDirectives() throws Exception {
        List<FeatureModelConverter.FeatureFileEntry> featureFiles =
            FeatureModelConverter.getFeatureFiles(new File(getClass().getResource("/features1").toURI()).getParentFile(),
            "features1"
                + "," + "features1"
                + ";" + FeatureModelConverter.PROVISIONING_MODEL_NAME_VARIABLE + "=" + "quickstart"
                + "," + "features1"
                + ";" + FeatureModelConverter.PROVISIONING_MODEL_NAME_VARIABLE + "=" + "quickstart"
                + ";" + FeatureModelConverter.PROVISIONING_RUNMODES + "=" + "author"
                + "," + "features1"
                + ";" + FeatureModelConverter.PROVISIONING_MODEL_NAME_VARIABLE + "=" + "samplecontent"
                + "," + "features1"
                + ";" + FeatureModelConverter.PROVISIONING_MODEL_NAME_VARIABLE + "=" + ":boot"
                + ";" + FeatureModelConverter.PROVISIONING_RUNMODES + ":List<Integer>=" + "\"1,2,3\""
        );

        assertNull(featureFiles.get(0).model);
        assertNull(featureFiles.get(0).runModes);

        assertEquals("quickstart", featureFiles.get(1).model);
        assertNull(featureFiles.get(1).runModes);

        assertEquals("quickstart", featureFiles.get(2).model);
        assertEquals("author", featureFiles.get(2).runModes);

        assertEquals("samplecontent", featureFiles.get(3).model);
        assertNull(featureFiles.get(3).runModes);

        assertEquals(":boot", featureFiles.get(4).model);
        assertEquals("1,2,3", featureFiles.get(4).runModes);
    }
}
