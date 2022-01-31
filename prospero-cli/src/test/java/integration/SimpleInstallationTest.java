/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package integration;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.cli.actions.Installation;
import com.redhat.prospero.cli.actions.Update;
import com.redhat.prospero.model.ManifestXmlSupport;
import com.redhat.prospero.model.XmlException;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

/*
 * Currently requires a local repos to run
 * TODO: use channels based on central repo once the metadata issue is fixed (MVNCENTRAL-7012)
 */
public class SimpleInstallationTest {

    private static final String OUTPUT_DIR = "target/server";
    private Path OUTPUT_PATH;
    private Path manifestPath;
    private Installation installation;

    @Before
    public void setUp() throws Exception {
        OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();
        manifestPath = OUTPUT_PATH.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME);
        installation = new Installation(OUTPUT_PATH);
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @Test
//    @Ignore
    public void installWildflyCore() throws Exception {
        final URL channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");
        final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);

        installation.provision("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", channelRefs);

//        new Update(OUTPUT_PATH, true).doUpdateAll();
//        FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
//
//        System.out.println("!! Sleeping " + System.currentTimeMillis());
//        Thread.sleep(60000);
//        System.out.println("!! Awake " + System.currentTimeMillis());
        // verify installation with manifest file is present
//        assertTrue(manifestPath.toFile().exists());
        // verify manifest contains versions 17.0.1
//        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
//        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());


    }

    @Test
    public void updateWildflyCore() throws Exception {
        final URL channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");
        final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);

        installation.provision("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", channelRefs);

        TestUtil.prepareChannelFile(OUTPUT_PATH.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.CHANNELS_FILE_NAME), "local-repo-desc.yaml", "local-updates-repo-desc.yaml");
        new Update(OUTPUT_PATH, true).doUpdateAll();

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.1.Final", wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void installWildflyCoreFromInstallationFile() throws Exception {
        final URL channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");
        final File installationFile = new File(this.getClass().getClassLoader().getResource("provisioning.xml").toURI());
        final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);

        installation.provision(installationFile.toPath(), channelRefs);

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws XmlException {
        final File manifestFile = manifestPath.toFile();
        return ManifestXmlSupport.parse(manifestFile).getArtifacts().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst();
    }

}
