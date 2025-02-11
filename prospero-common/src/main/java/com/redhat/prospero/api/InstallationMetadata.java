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

package com.redhat.prospero.api;

import com.redhat.prospero.installation.git.GitStorage;
import com.redhat.prospero.model.ManifestYamlSupport;
import org.eclipse.aether.artifact.Artifact;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.xml.ProvisioningXmlParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class InstallationMetadata {

    public static final String METADATA_DIR = ".installation";
    public static final String MANIFEST_FILE_NAME = "manifest.yaml";
    public static final String CHANNELS_FILE_NAME = "channels.yaml";
    public static final String PROVISIONING_FILE_NAME = "provisioning.xml";
    public static final String GALLEON_INSTALLATION_DIR = ".galleon";
    private final Path manifestFile;
    private final Path channelsFile;
    private final Path provisioningFile;
    private final Manifest manifest;
    private final ProvisioningConfig provisioningConfig;
    private final List<ChannelRef> channelRefs;
    private final GitStorage gitStorage;
    private Path base;

    private InstallationMetadata(Path manifestFile, Path channelsFile, Path provisioningFile) throws MetadataException {
        this.base = manifestFile.getParent();
        this.gitStorage = null;
        this.manifestFile = manifestFile;
        this.channelsFile = channelsFile;
        this.provisioningFile = provisioningFile;

        try {
            this.manifest = Manifest.parseManifest(manifestFile);
            this.channelRefs = ChannelRef.readChannels(channelsFile);
            this.provisioningConfig = ProvisioningXmlParser.parse(provisioningFile);
        } catch (IOException | ProvisioningException e) {
            throw new MetadataException("Error when parsing installation metadata", e);
        }
    }

    public InstallationMetadata(Path base) throws MetadataException {
        this.base = base;
        this.gitStorage = new GitStorage(base);
        this.manifestFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME);
        this.channelsFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.CHANNELS_FILE_NAME);
        this.provisioningFile = base.resolve(GALLEON_INSTALLATION_DIR).resolve(InstallationMetadata.PROVISIONING_FILE_NAME);

        try {
            this.manifest = Manifest.parseManifest(manifestFile);
            this.channelRefs = ChannelRef.readChannels(channelsFile);
            this.provisioningConfig = ProvisioningXmlParser.parse(provisioningFile);
        } catch (IOException | ProvisioningException e) {
            throw new MetadataException("Error when parsing installation metadata", e);
        }
    }

    public InstallationMetadata(Path base, List<Artifact> artifacts, List<ChannelRef> channelRefs) throws MetadataException {
        this.base = base;
        this.gitStorage = new GitStorage(base);
        this.manifestFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME);
        this.channelsFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.CHANNELS_FILE_NAME);
        this.provisioningFile = base.resolve(GALLEON_INSTALLATION_DIR).resolve(InstallationMetadata.PROVISIONING_FILE_NAME);

        this.manifest = new Manifest(artifacts, manifestFile);
        this.channelRefs = channelRefs;
        try {
            this.provisioningConfig = ProvisioningXmlParser.parse(provisioningFile);
        } catch (ProvisioningException e) {
            throw new MetadataException("Error when parsing installation metadata", e);
        }
    }

    public static InstallationMetadata importMetadata(Path location) throws IOException, MetadataException {
        Path manifestFile = null;
        Path channelsFile = null;
        Path provisioningFile = null;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(location.toFile()))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {

                if (entry.getName().equals(MANIFEST_FILE_NAME)) {
                    manifestFile = Files.createTempFile("manifest", "xml");
                    Files.copy(zis, manifestFile, StandardCopyOption.REPLACE_EXISTING);
                    manifestFile.toFile().deleteOnExit();
                }

                if (entry.getName().equals(CHANNELS_FILE_NAME)) {
                    channelsFile = Files.createTempFile("channels", "yaml");
                    Files.copy(zis, channelsFile, StandardCopyOption.REPLACE_EXISTING);
                    channelsFile.toFile().deleteOnExit();
                }

                if (entry.getName().equals(PROVISIONING_FILE_NAME)) {
                    provisioningFile = Files.createTempFile("provisioning", "xml");
                    Files.copy(zis, provisioningFile, StandardCopyOption.REPLACE_EXISTING);
                    provisioningFile.toFile().deleteOnExit();
                }

                entry = zis.getNextEntry();
            }
        }

        if (manifestFile == null || channelsFile == null || provisioningFile == null) {
            throw new IllegalArgumentException("Provided metadata bundle is missing one or more entries");
        }

        return new InstallationMetadata(manifestFile, channelsFile, provisioningFile);
    }

    public Path exportMetadataBundle(Path location) throws IOException {
        final File file = location.toFile();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(MANIFEST_FILE_NAME));
            try(FileInputStream fis = new FileInputStream(manifestFile.toFile())) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(CHANNELS_FILE_NAME));
            try(FileInputStream fis = new FileInputStream(channelsFile.toFile())) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(PROVISIONING_FILE_NAME));
            try(FileInputStream fis = new FileInputStream(provisioningFile.toFile())) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
        return file.toPath();
    }

    public void registerUpdates(Set<Artifact> artifacts) {
        for (Artifact artifact : artifacts) {
            manifest.updateVersion(artifact);
        }
    }

    public Manifest getManifest() {
        return manifest;
    }

    public List<ChannelRef> getChannels() {
        return channelRefs;
    }

    public ProvisioningConfig getProvisioningConfig() {
        return provisioningConfig;
    }

    public void writeFiles() throws MetadataException {
        try {
            ManifestYamlSupport.write(this.manifest, this.channelRefs);
        } catch (IOException e) {
            throw new MetadataException("Unable to save manifest in installation", e);
        }

        // write channels into installation
        try {
            ChannelRef.writeChannels(this.channelRefs, this.channelsFile.toFile());
        } catch (IOException e) {
            throw new MetadataException("Unable to save channel list in installation", e);
        }

        gitStorage.record();
    }

    public List<SavedState> getRevisions() throws MetadataException {
        return gitStorage.getRevisions();
    }

    public InstallationMetadata rollback(SavedState savedState) throws MetadataException {
        // checkout previous version
        // record as rollback operation
        gitStorage.revert(savedState);

        // re-parse metadata
        return new InstallationMetadata(base);
    }

    public List<ArtifactChange> getChangesSince(SavedState savedState) throws MetadataException {
        return gitStorage.getChanges(savedState);
    }
}
