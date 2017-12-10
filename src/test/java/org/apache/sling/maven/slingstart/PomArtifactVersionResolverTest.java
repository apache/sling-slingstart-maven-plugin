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

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.sling.provisioning.model.Artifact;
import org.junit.Assert;
import org.junit.Test;

public class PomArtifactVersionResolverTest {

    @Test
    public void testNormalizeType() {
        Assert.assertEquals("jar", PomArtifactVersionResolver.normalizeType("jar"));
        Assert.assertEquals("jar", PomArtifactVersionResolver.normalizeType("bundle"));
        Assert.assertEquals("bla", PomArtifactVersionResolver.normalizeType("bla"));
    }

    @Test
    public void testArtifactEqualsForMavenArtifact() {
        Assert.assertTrue(PomArtifactVersionResolver.artifactEquals(
                new DefaultArtifact("somegroup", "someartifact", "someversion", "somescope", "sometype", "someclassifier", new DefaultArtifactHandler()),
                new Artifact("somegroup", "someartifact", "someversion", "someclassifier", "sometype")));
        // test maven artifact with type "bundle", other with type "jar"
        Assert.assertTrue(PomArtifactVersionResolver.artifactEquals(
                new DefaultArtifact("somegroup", "someartifact", "someversion", "somescope", "bundle", "someclassifier", new DefaultArtifactHandler()), 
                new Artifact("somegroup", "someartifact", "someversion", "someclassifier", "jar")));
        
        // test without classifier
        Assert.assertTrue(PomArtifactVersionResolver.artifactEquals(
                new DefaultArtifact("somegroup", "someartifact", "someversion", "somescope", "bundle", null, new DefaultArtifactHandler()), 
                new Artifact("somegroup", "someartifact", "LATEST", null, "jar")));
    }
    
    @Test
    public void testArtifactEqualsForMavenDependencies() {
        Dependency dependency = new Dependency();
        dependency.setGroupId("somegroup");
        dependency.setArtifactId("someartifact");
        dependency.setVersion("someversion");
        dependency.setClassifier("someclassifier");
        dependency.setType("sometype");
        Assert.assertTrue(PomArtifactVersionResolver.artifactEquals(
                dependency,
                new Artifact("somegroup", "someartifact", "someversion", "someclassifier", "sometype")));
        // test dependency with type "bundle", other with type "jar"
        dependency.setType("bundle");
        Assert.assertTrue(PomArtifactVersionResolver.artifactEquals(
                dependency, 
                new Artifact("somegroup", "someartifact", "someversion", "someclassifier", "jar")));
        
        // test without classifier
        dependency.setClassifier(null);
        Assert.assertTrue(PomArtifactVersionResolver.artifactEquals(
                dependency, 
                new Artifact("somegroup", "someartifact", "LATEST", null, "jar")));
    }
}
