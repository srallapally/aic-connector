// src/main/java/org/forgerock/openicf/connectors/aic/PingAICConfiguration.java
package org.forgerock.openicf.connectors.aic;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class PingAICConfiguration extends AbstractConfiguration {

    private String baseUrl;
    private String clientId;
    private GuardedString clientSecret;
    private String realm = "alpha";
    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 30_000;
    private String timestampAttribute;
    private String timestampValue;
    private String userBaseFilter;
    private String orgBaseFilter;
    private String roleBaseFilter;

    @ConfigurationProperty(order = 1, required = true,
            displayMessageKey = "baseUrl.display",
            helpMessageKey = "baseUrl.help")
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    @ConfigurationProperty(order = 2, required = true,
            displayMessageKey = "clientId.display",
            helpMessageKey = "clientId.help")
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    @ConfigurationProperty(order = 3, required = true, confidential = true,
            displayMessageKey = "clientSecret.display",
            helpMessageKey = "clientSecret.help")
    public GuardedString getClientSecret() { return clientSecret; }
    public void setClientSecret(GuardedString clientSecret) { this.clientSecret = clientSecret; }

    @ConfigurationProperty(order = 4,
            displayMessageKey = "realm.display",
            helpMessageKey = "realm.help")
    public String getRealm() { return realm; }
    public void setRealm(String realm) { this.realm = realm; }

    @ConfigurationProperty(order = 5,
            displayMessageKey = "connectTimeoutMs.display",
            helpMessageKey = "connectTimeoutMs.help")
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    @ConfigurationProperty(order = 6,
            displayMessageKey = "readTimeoutMs.display",
            helpMessageKey = "readTimeoutMs.help")
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    @ConfigurationProperty(order = 7,
            displayMessageKey = "timestampAttribute.display",
            helpMessageKey = "timestampAttribute.help")
    public String getTimestampAttribute() { return timestampAttribute; }
    public void setTimestampAttribute(String timestampAttribute) { this.timestampAttribute = timestampAttribute; }

    @ConfigurationProperty(order = 8,
            displayMessageKey = "timestampValue.display",
            helpMessageKey = "timestampValue.help")
    public String getTimestampValue() { return timestampValue; }
    public void setTimestampValue(String timestampValue) { this.timestampValue = timestampValue; }

    @ConfigurationProperty(order = 9,
            displayMessageKey = "userBaseFilter.display",
            helpMessageKey = "userBaseFilter.help")
    public String getUserBaseFilter() { return userBaseFilter; }
    public void setUserBaseFilter(String userBaseFilter) { this.userBaseFilter = userBaseFilter; }

    @ConfigurationProperty(order = 10,
            displayMessageKey = "orgBaseFilter.display",
            helpMessageKey = "orgBaseFilter.help")
    public String getOrgBaseFilter() { return orgBaseFilter; }
    public void setOrgBaseFilter(String orgBaseFilter) { this.orgBaseFilter = orgBaseFilter; }

    @ConfigurationProperty(order = 11,
            displayMessageKey = "roleBaseFilter.display",
            helpMessageKey = "roleBaseFilter.help")
    public String getRoleBaseFilter() { return roleBaseFilter; }
    public void setRoleBaseFilter(String roleBaseFilter) { this.roleBaseFilter = roleBaseFilter; }

    @Override
    public void validate() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ConfigurationException("baseUrl must not be blank");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new ConfigurationException("clientId must not be blank");
        }
        if (clientSecret == null) {
            throw new ConfigurationException("clientSecret must not be null");
        }
    }
}
