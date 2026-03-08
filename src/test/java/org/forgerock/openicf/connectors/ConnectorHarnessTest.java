// src/test/java/org/forgerock/openicf/connectors/ConnectorHarnessTest.java
package org.forgerock.openicf.connectors;

import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ConnectorHarnessTest {

    private static final String CONNECTOR_NAME = "org.forgerock.openicf.connectors.aic.PingAICConnector";
    private static final String PROPS_FILE = "src/test/resources/aic-test.properties";

    protected ConnectorFacade facade;

    @BeforeAll
    void setup() throws IOException {
        Properties props = loadProperties();

        String jarPath = props.getProperty("aic.jarPath");
        String version = props.getProperty("aic.version");

        ConnectorInfoManagerFactory factory = ConnectorInfoManagerFactory.getInstance();
        File bundleDir = new File(jarPath).getParentFile();
        URL url = IOUtil.makeURL(bundleDir, new File(jarPath).getName());
        ConnectorInfoManager manager = factory.getLocalManager(url);

        ConnectorInfo info = findConnector(manager, version);
        if (info == null) {
            throw new IllegalStateException("Connector not found: " + CONNECTOR_NAME + " v" + version);
        }

        APIConfiguration apiConfig = info.createDefaultAPIConfiguration();
        ConfigurationProperties configProps = apiConfig.getConfigurationProperties();

        configProps.getProperty("baseUrl").setValue(props.getProperty("aic.baseUrl"));
        configProps.getProperty("realm").setValue(props.getProperty("aic.realm"));
        configProps.getProperty("clientId").setValue(props.getProperty("aic.clientId"));
        configProps.getProperty("clientSecret").setValue(
                new GuardedString(props.getProperty("aic.clientSecret").toCharArray()));

        facade = ConnectorFacadeFactory.getInstance().newInstance(apiConfig);
        facade.validate();
    }

    @AfterAll
    void teardown() {
        ConnectorFacadeFactory.getInstance().dispose();
    }

    private ConnectorInfo findConnector(ConnectorInfoManager manager, String version) {
        for (ConnectorInfo info : manager.getConnectorInfos()) {
            ConnectorKey key = info.getConnectorKey();
            if (version.equals(key.getBundleVersion()) && CONNECTOR_NAME.equals(key.getConnectorName())) {
                return manager.findConnectorInfo(key);
            }
        }
        return null;
    }

    private Properties loadProperties() throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(PROPS_FILE)) {
            props.load(fis);
        }
        return props;
    }
}
