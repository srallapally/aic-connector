// src/main/java/org/forgerock/openicf/connectors/aic/util/CrestHttpClient.java
package org.forgerock.openicf.connectors.aic.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.forgerock.openicf.connectors.aic.PingAICConfiguration;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CrestHttpClient implements Closeable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CloseableHttpClient httpClient;
    private final String baseUrl;
    private final String token;

    public CrestHttpClient(PingAICConfiguration cfg) {
        this.baseUrl = cfg.getBaseUrl();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(cfg.getConnectTimeoutMs())
                .setSocketTimeout(cfg.getReadTimeoutMs())
                .build();

        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        this.token = fetchToken(cfg);
    }

    private String fetchToken(PingAICConfiguration cfg) {
        final String[] holder = new String[1];
        cfg.getClientSecret().access(chars -> holder[0] = new String(chars));

        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "client_credentials"));
        params.add(new BasicNameValuePair("client_id", cfg.getClientId()));
        params.add(new BasicNameValuePair("client_secret", holder[0]));
        holder[0] = null;

        HttpPost post = new HttpPost(cfg.getBaseUrl() + "/am/oauth2/" + cfg.getRealm() + "/access_token");
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (status != 200) {
                throw new ConnectorException("Token request failed: " + status + " " + body);
            }
            return MAPPER.readTree(body).get("access_token").asText();
        } catch (IOException e) {
            throw new ConnectorException("Token request failed: " + e.getMessage(), e);
        }
    }

    public JsonNode get(String path, Map<String, String> queryParams) {
        try {
            URIBuilder builder = new URIBuilder(baseUrl + path);
            if (queryParams != null) {
                queryParams.forEach(builder::addParameter);
            }
            URI uri = builder.build();
            return execute(new HttpGet(uri), true);
        } catch (URISyntaxException e) {
            throw new ConnectorException("Invalid URI for path: " + path, e);
        }
    }

    public JsonNode post(String path, JsonNode body) {
        HttpPost request = new HttpPost(baseUrl + path);
        request.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
        return execute(request, true);
    }

    public JsonNode patch(String path, JsonNode patchOps) {
        HttpPatch request = new HttpPatch(baseUrl + path);
        request.setHeader("If-Match", "*");
        request.setEntity(new StringEntity(patchOps.toString(), ContentType.APPLICATION_JSON));
        return execute(request, true);
    }

    public void delete(String path) {
        execute(new HttpDelete(baseUrl + path), false);
    }

    private JsonNode execute(HttpUriRequest request, boolean expectBody) {
        request.setHeader("Authorization", "Bearer " + token);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            if (status == 401) {
                throw new ConnectorException("Unauthorized (401) — token invalid or expired");
            }
            if (status < 200 || status >= 300) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                throw new ConnectorException("Request failed: " + status + " " + body);
            }
            if (!expectBody) {
                return null;
            }
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new ConnectorException("HTTP request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
