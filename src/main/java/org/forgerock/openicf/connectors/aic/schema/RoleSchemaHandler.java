// src/main/java/org/forgerock/openicf/connectors/aic/schema/RoleSchemaHandler.java
package org.forgerock.openicf.connectors.aic.schema;

import com.fasterxml.jackson.databind.JsonNode;
import org.forgerock.openicf.connectors.aic.util.CrestHttpClient;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class RoleSchemaHandler {

    private static final Set<String> READ_ONLY_RELATIONSHIP_NAMES = Set.of("assignments");

    private final CrestHttpClient http;
    private final String realm;
    private Map<String, String> relationshipCollections = Collections.emptyMap();
    private Set<String> readOnlyRelationships = Collections.emptySet();

    public RoleSchemaHandler(CrestHttpClient http, String realm) {
        this.http = http;
        this.realm = realm;
    }

    public ObjectClassInfo getObjectClassInfo() {
        JsonNode schema = http.get("/openidm/schema/managed/" + realm + "_role", null);
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            throw new ConnectorException("Invalid schema response: missing 'properties' field");
        }

        ObjectClassInfoBuilder ocib = new ObjectClassInfoBuilder();
        ocib.setType("ROLE");

        Map<String, String> relMap = new LinkedHashMap<>();
        Set<String> roRels = new HashSet<>();

        for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String propName = entry.getKey();
            JsonNode propDef = entry.getValue();

            if ("_id".equals(propName)) {
                continue;
            }

            String type = propDef.path("type").asText("string");

            if ("array".equals(type)
                    && "relationship".equals(propDef.path("items").path("type").asText(""))) {
                JsonNode rcArray = propDef.path("items").path("resourceCollection");
                if (!rcArray.isArray() || rcArray.isEmpty()) {
                    continue;
                }
                String collectionPath = rcArray.path(0).path("path").asText(null);
                if (collectionPath == null || collectionPath.isBlank()) {
                    continue;
                }
                relMap.put(propName, collectionPath);

                boolean updateable = !READ_ONLY_RELATIONSHIP_NAMES.contains(propName);
                if (!updateable) {
                    roRels.add(propName);
                }

                AttributeInfoBuilder rab = new AttributeInfoBuilder();
                rab.setName(propName);
                rab.setType(String.class);
                rab.setMultiValued(true);
                rab.setReturnedByDefault(true);
                rab.setCreateable(true);
                rab.setUpdateable(updateable);
                ocib.addAttributeInfo(rab.build());
                continue;
            }

            AttributeInfoBuilder aib = new AttributeInfoBuilder();
            aib.setName("name".equals(propName) ? Name.NAME : propName);
            applyType(aib, type);

            if ("name".equals(propName)) {
                ocib.addAttributeInfo(aib.build());
                continue;
            }

            boolean userEditable = propDef.path("userEditable").asBoolean(true);
            boolean returnByDefault = propDef.path("returnByDefault").asBoolean(true);
            if (!userEditable) {
                aib.setCreateable(false);
                aib.setUpdateable(false);
            }
            if (!returnByDefault) {
                aib.setReturnedByDefault(false);
            }

            ocib.addAttributeInfo(aib.build());
        }

        this.relationshipCollections = Collections.unmodifiableMap(relMap);
        this.readOnlyRelationships = Collections.unmodifiableSet(roRels);
        return ocib.build();
    }

    public Map<String, String> getRelationshipCollections() {
        return relationshipCollections;
    }

    public Set<String> getReadOnlyRelationships() {
        return readOnlyRelationships;
    }

    private static void applyType(AttributeInfoBuilder aib, String type) {
        switch (type) {
            case "boolean":
                aib.setType(Boolean.class);
                break;
            case "integer":
                aib.setType(Integer.class);
                break;
            case "number":
                aib.setType(Double.class);
                break;
            case "array":
                aib.setType(String.class);
                aib.setMultiValued(true);
                break;
            default:
                aib.setType(String.class);
        }
    }
}
