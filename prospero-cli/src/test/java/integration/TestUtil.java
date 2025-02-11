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

import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.prospero.api.InstallationMetadata;

public class TestUtil {

    public static final Path MANIFEST_FILE_PATH = Paths.get(InstallationMetadata.METADATA_DIR, InstallationMetadata.MANIFEST_FILE_NAME);
    public static final Path CHANNELS_FILE_PATH = Paths.get(InstallationMetadata.METADATA_DIR, InstallationMetadata.CHANNELS_FILE_NAME);

    static public URL prepareChannelFileAsUrl(String channelDescriptor) throws IOException {
        final Path channelFile = Files.createTempFile("channels", "yaml");
        channelFile.toFile().deleteOnExit();

        final Path path = prepareChannelFileAsUrl(channelFile, channelDescriptor);
        return path.toUri().toURL();
    }

    static public Path prepareChannelFile(String channelDescriptor) throws IOException {
        final Path channelFile = Files.createTempFile("channels", "yaml");
        channelFile.toFile().deleteOnExit();

        return prepareChannelFileAsUrl(channelFile, channelDescriptor);
    }

    static public Path prepareChannelFileAsUrl(Path channelFile, String... channelDescriptor) throws IOException {
        List<URL> repoUrls = Arrays.stream(channelDescriptor).map(d->TestUtil.class.getClassLoader().getResource(d)).collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("[");
        for (int i=0; i<repoUrls.size(); i++) {
            sb.append(String.format("{\"name\":\"%s\",\"url\":\"%s\"}", "repo-"+i, repoUrls.get(i)));
            if (i < (repoUrls.size()-1)) {
                sb.append(",");
            }
        }
        sb.append("]");

        try (FileWriter fw = new FileWriter(channelFile.toFile())) {
            fw.write(sb.toString());
        }
        return channelFile;
    }
}
