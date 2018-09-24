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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        File f = new File(System.getProperty("user.home") + "/.m2");
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

        ProjectInfo pi = new ProjectInfo();
        pi.project = proj;

        Environment env = new Environment();
        env.modelProjects.put("xyz", pi);

        FeatureModelConverter.convert(session, env);

        File expectedFile = new File(tempDir, "/provisioning/converted/boot_gav.json.txt");
        assertTrue(expectedFile.exists());
        assertTrue(expectedFile.length() > 0);
    }

    @Test
    public void testConvertWithIncludes() throws Exception {
        File f = new File(System.getProperty("user.home") + "/.m2");
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

        ProjectInfo pi = new ProjectInfo();
        pi.project = proj;

        Environment env = new Environment();
        env.modelProjects.put("xyz", pi);

        FeatureModelConverter.convert(session, env);

        File simpleProvFile = new File(tempDir, "/provisioning/converted/simple.json.txt");
        String simpleProv = new String(Files.readAllBytes(simpleProvFile.toPath()));
        assertTrue(simpleProv.contains("org.apache.aries/org.apache.aries.util/1.1.3"));
        assertFalse(simpleProv.contains("org.apache.sling/org.apache.sling.commons.log/5.1.0"));

        File inheritsProvFile = new File(tempDir, "/provisioning/converted/simple_inherits.json.txt");
        String inheritsProv = new String(Files.readAllBytes(inheritsProvFile.toPath()));
        assertTrue(inheritsProv.contains("org.apache.aries/org.apache.aries.util/1.1.3"));
        assertTrue(inheritsProv.contains("org.apache.sling/org.apache.sling.commons.log/5.1.0"));
    }

    @Test
    public void testReplaceVars() {
        MavenProject mp = Mockito.mock(MavenProject.class);

        Properties props = new Properties();
        props.put("foo", "bar");

        Mockito.when(mp.getGroupId()).thenReturn("abc");
        Mockito.when(mp.getArtifactId()).thenReturn("a.b.c");
        Mockito.when(mp.getVersion()).thenReturn("1.2.3-SNAPSHOT");
        Mockito.when(mp.getProperties()).thenReturn(props);

        assertEquals("xxxabcyyy", FeatureModelConverter.replaceVars(mp,
                "xxx${project.groupId}yyy"));
        assertEquals("xxxabcyyya.b.c1.2.3-SNAPSHOT", FeatureModelConverter.replaceVars(mp,
                "xxx${project.groupId}yyy${project.artifactId}${project.version}"));
        assertEquals("xxxbaryyy", FeatureModelConverter.replaceVars(mp, "xxx${foo}yyy"));
    }
}
