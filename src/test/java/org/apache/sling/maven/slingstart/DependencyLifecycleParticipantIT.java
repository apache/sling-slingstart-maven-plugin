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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;


public class DependencyLifecycleParticipantIT {

    private static final String PROPERTY_PROJECT_VERSION = "project.version";

    @Test
    public void testGetCurrentPluginVersion() throws IOException {
        String version = System.getProperty(PROPERTY_PROJECT_VERSION);
        Assert.assertNotNull("This test must be called with property " + PROPERTY_PROJECT_VERSION + " being set to the project version", version);
        Assert.assertEquals(version, DependencyLifecycleParticipant.getCurrentPluginVersion());
    }
}
