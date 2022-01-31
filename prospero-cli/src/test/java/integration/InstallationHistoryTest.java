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

import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.cli.actions.Installation;
import com.redhat.prospero.cli.actions.InstallationHistory;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.cli.actions.Update;
import com.redhat.prospero.model.ManifestXmlSupport;
import com.redhat.prospero.model.XmlException;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class InstallationHistoryTest {

    private static final String OUTPUT_DIR = "target/server";
    private static final Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();
   private final Installation installation = new Installation(OUTPUT_PATH);
   private URL channelFile;

   @Before
    public void setUp() throws Exception {
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
        if (Files.exists(Paths.get(channelFile.toURI()))) {
           Files.delete(Paths.get(channelFile.toURI()));
        }
    }

    @Test
    public void listUpdates() throws Exception {
        // installCore
       channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");
       Path installDir = Paths.get(OUTPUT_PATH.toString());
       if (Files.exists(installDir)) {
           throw new ProvisioningException("Installation dir " + installDir + " already exists");
       }
       final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);

       installation.provision("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", channelRefs);

       // updateCore
        TestUtil.prepareChannelFile(OUTPUT_PATH.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.CHANNELS_FILE_NAME), "local-repo-desc.yaml", "local-updates-repo-desc.yaml");
        new Update(OUTPUT_PATH, true).doUpdateAll();

        // get history
        List<SavedState> states = new InstallationHistory(OUTPUT_PATH).getRevisions();

        // assert two entries
        assertEquals(2, states.size());
    }

    @Test
    public void rollbackChanges() throws Exception {
       channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");
       Path installDir = Paths.get(OUTPUT_PATH.toString());
       if (Files.exists(installDir)) {
           throw new ProvisioningException("Installation dir " + installDir + " already exists");
       }
       final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);

       installation.provision("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", channelRefs);

       TestUtil.prepareChannelFile(OUTPUT_PATH.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.CHANNELS_FILE_NAME), "local-repo-desc.yaml", "local-updates-repo-desc.yaml");
        new Update(OUTPUT_PATH, true).doUpdateAll();

        final InstallationHistory installationHistory = new InstallationHistory(OUTPUT_PATH);
        final List<SavedState> revisions = installationHistory.getRevisions();

        final SavedState savedState = revisions.get(1);
        installationHistory.rollback(savedState);

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void displayChanges() throws Exception {
       channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");
       Path installDir = Paths.get(OUTPUT_PATH.toString());
       if (Files.exists(installDir)) {
           throw new ProvisioningException("Installation dir " + installDir + " already exists");
       }
       final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);

       installation.provision("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", channelRefs);

       TestUtil.prepareChannelFile(OUTPUT_PATH.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.CHANNELS_FILE_NAME), "local-repo-desc.yaml", "local-updates-repo-desc.yaml");
        new Update(OUTPUT_PATH, true).doUpdateAll();

        final InstallationHistory installationHistory = new InstallationHistory(OUTPUT_PATH);
        final List<SavedState> revisions = installationHistory.getRevisions();

        final SavedState savedState = revisions.get(1);
        final List<ArtifactChange> changes = installationHistory.compare(savedState);

        assertEquals(2, changes.size());
        Map<Artifact, Artifact> expected = new HashMap<>();
        expected.put(new DefaultArtifact("org.wildfly.core:wildfly-cli:17.0.0.Final"),
                new DefaultArtifact("org.wildfly.core:wildfly-cli:17.0.1.Final"));
        expected.put(new DefaultArtifact("org.wildfly.core:wildfly-cli:jar:client:17.0.0.Final"),
                new DefaultArtifact("org.wildfly.core:wildfly-cli:jar:client:17.0.1.Final"));

        for (ArtifactChange change : changes) {
            if (expected.containsKey(change.getOldVersion())) {
                assertEquals(expected.get(change.getOldVersion()), change.getNewVersion());
                expected.remove(change.getOldVersion());
            } else {
                Assert.fail("Unexpected artifact in updates " + change);
            }
        }
        assertEquals("Not all expected changes were listed", 0, expected.size());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws XmlException {
        final File manifestFile = OUTPUT_PATH.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME).toFile();
        return ManifestXmlSupport.parse(manifestFile).getArtifacts().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst();
    }
}
