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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Properties;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.io.Files;

public class GenerateResourcesMojoTest {
    private File tempDir;

    @Before
    public void setup() throws Exception {
        tempDir = Files.createTempDir();
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
    public void testExecute() throws Exception {
        URL url = getClass().getResource("/features1/simple.json");
        File featureDir = new File(url.toURI()).getParentFile();

        Build build = Mockito.mock(Build.class);
        Mockito.when(build.getDirectory()).thenReturn(tempDir.getAbsolutePath());

        MavenProject proj = Mockito.mock(MavenProject.class);
        Mockito.when(proj.getBuild()).thenReturn(build);
        Mockito.when(proj.getGroupId()).thenReturn("g");
        Mockito.when(proj.getArtifactId()).thenReturn("a");
        Mockito.when(proj.getVersion()).thenReturn("1");
        Mockito.when(proj.getBasedir()).thenReturn(featureDir.getParentFile());
        final Properties projProps = new Properties();
        Mockito.when(proj.getProperties()).thenReturn(projProps);
        File f = new File(System.getProperty("user.home") + File.separatorChar + ".m2");
        ArtifactRepository localRepo = Mockito.mock(ArtifactRepository.class);
        Mockito.when(localRepo.getUrl()).thenReturn(f.toURI().toURL().toString());

        MavenSession session = Mockito.mock(MavenSession.class);
        Mockito.when(session.getLocalRepository()).thenReturn(localRepo);

        GenerateResourcesMojo grm = new GenerateResourcesMojo();
        setPrivateField(grm, "featuresDirectory", featureDir.getName());
        setPrivateField(AbstractSlingStartMojo.class, grm, "project", proj);
        setPrivateField(AbstractSlingStartMojo.class, grm, "mavenSession", session);

        grm.execute();

        File expectedFile = new File(tempDir, FeatureModelConverter.BUILD_DIR + File.separatorChar + "simple.json.txt");
        assertTrue(expectedFile.exists());
        assertTrue(expectedFile.length() > 0);
    }

    private void setPrivateField(Object obj, String name, Object val) throws Exception {
        setPrivateField(obj.getClass(), obj, name, val);
    }

    private void setPrivateField(Class<?> cls, Object obj, String name, Object val) throws Exception {
        Field field = cls.getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, val);
    }
}
