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
import java.net.URL;

import com.google.common.io.Files;

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

        FeatureModelConverter fmc = new FeatureModelConverter();
        fmc.convert(session, env);

        File expectedFile = new File(tempDir, "/provisioning/converted/boot_gav.json.txt");
        assertTrue(expectedFile.exists());
        assertTrue(expectedFile.length() > 0);
    }
}
