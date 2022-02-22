package com.redhat.prospero.galleon;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.wfchannel.RepositoryManager;
import com.redhat.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Ignore
public class FeaturePackLocationParserTest {

    private ChannelMavenArtifactRepositoryManager repoManager;
    private FeaturePackLocationParser parser;

//    @Before
    public void setUp() throws Exception {
        final Path channelsFile = Paths.get(FeaturePackLocationParserTest.class.getResource("/channels/eap/channels-eap74.json").toURI());

        final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelsFile);
        final List<Channel> channels = channelRefs.stream().map(ref-> {
            try {
                return ChannelMapper.from(new URL(ref.getUrl()));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        //        try {
        //            provisioningRepo = Files.createTempDirectory("provisioning-repo");
        //            provisioningRepo.toFile().deleteOnExit();
        //        } catch (IOException e) {
        //            throw new ProvisioningException("Unable to create provisioning repository folder.", e);
        //        }
        Path provisioningRepo = Paths.get("/Users/spyrkob/workspaces/set/prospero/debug/provision-repo/");
        final RepositoryManager repositoryManager = new RepositoryManager(provisioningRepo);

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory(repositoryManager);
        repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory);
        parser = new FeaturePackLocationParser(repoManager);
    }

    @Test
    public void findLatestFeaturePackInPartialGav() throws Exception {
        final FeaturePackLocation resolvedFpl = resolveFplVersion("org.jboss.eap:wildfly-ee-galleon-pack");
        assertEquals("7.4.3.GA-redhat-SNAPSHOT", resolvedFpl.getBuild());
        assertEquals("org.jboss.eap:wildfly-ee-galleon-pack::zip", resolvedFpl.getProducerName());
    }

    @Test
    public void useProvidedGavWhenUsedFull() throws Exception {
        final FeaturePackLocation resolvedFpl = resolveFplVersion("org.jboss.eap:wildfly-ee-galleon-pack:7.4.2.GA-redhat-00003");
        assertEquals("7.4.2.GA-redhat-00003", resolvedFpl.getBuild());
        assertEquals("org.jboss.eap:wildfly-ee-galleon-pack::zip", resolvedFpl.getProducerName());
    }

    @Test
    public void useUniverseIfProvided() throws Exception {
        assertEquals(null, resolveFplVersion("wildfly@maven(community-universe):current").getBuild());
        assertEquals("current", resolveFplVersion("wildfly@maven(community-universe):current").getChannelName());
    }

    private FeaturePackLocation resolveFplVersion(String fplText) throws MavenUniverseException {
        return parser.resolveFpl(fplText);
    }
}