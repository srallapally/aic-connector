// src/main/java/org/forgerock/openicf/connectors/aic/schema/UserSchemaHandler.java
package org.forgerock.openicf.connectors.aic.schema;

import com.fasterxml.jackson.databind.JsonNode;
import org.forgerock.openicf.connectors.aic.util.CrestHttpClient;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class UserSchemaHandler {

    private final CrestHttpClient http;
    private final String realm;
    private Map<String, String> relationshipCollections = Collections.emptyMap();
    private Set<String> readOnlyAttributes = Collections.emptySet();

    public UserSchemaHandler(CrestHttpClient http, String realm) {
        this.http = http;
        this.realm = realm;
    }

    public ObjectClassInfo getObjectClassInfo() {
        JsonNode schema = http.get("/openidm/schema/managed/" + realm + "_user", null);
        return buildFromSchema(schema);
    }

    ObjectClassInfo buildFromSchema(JsonNode schema) {
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            throw new ConnectorException("Invalid schema response: missing 'properties' field");
        }

        ObjectClassInfoBuilder ocib = new ObjectClassInfoBuilder();
        ocib.setType(ObjectClass.ACCOUNT_NAME);

        Map<String, String> relMap = new LinkedHashMap<>();
        Set<String> roAttrs = new HashSet<>();

        for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String propName = entry.getKey();
            JsonNode propDef = entry.getValue();

            if ("_id".equals(propName)) {
                continue;
            }

            String type = propDef.path("type").asText("string");
            boolean isVirtual = propDef.path("isVirtual").asBoolean(false);

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

                if (isVirtual) {
                    roAttrs.add(propName);
                    AttributeInfoBuilder rab = new AttributeInfoBuilder();
                    rab.setName(propName);
                    rab.setType(String.class);
                    rab.setMultiValued(true);
                    rab.setReturnedByDefault(false);
                    rab.setCreateable(false);
                    rab.setUpdateable(false);
                    ocib.addAttributeInfo(rab.build());
                } else {
                    relMap.put(propName, collectionPath);
                    AttributeInfoBuilder rab = new AttributeInfoBuilder();
                    rab.setName(propName);
                    rab.setType(String.class);
                    rab.setMultiValued(true);
                    rab.setReturnedByDefault(false);
                    rab.setCreateable(false);
                    rab.setUpdateable(true);
                    ocib.addAttributeInfo(rab.build());
                }
                continue;
            }

            AttributeInfoBuilder aib = new AttributeInfoBuilder();
            aib.setName("userName".equals(propName) ? Name.NAME : propName);
            applyType(aib, type);

            if ("userName".equals(propName)) {
                ocib.addAttributeInfo(aib.build());
                continue;
            }

            boolean userEditable = propDef.path("userEditable").asBoolean(true);
            boolean returnByDefault = propDef.path("returnByDefault").asBoolean(true);
            if (isVirtual || !userEditable) {
                aib.setCreateable(false);
                aib.setUpdateable(false);
            }
            if (isVirtual) {
                roAttrs.add(propName);
            }
            if (!returnByDefault) {
                aib.setReturnedByDefault(false);
            }

            ocib.addAttributeInfo(aib.build());
        }

        this.relationshipCollections = Collections.unmodifiableMap(relMap);
        this.readOnlyAttributes = Collections.unmodifiableSet(roAttrs);
        return ocib.build();
    }

    public Map<String, String> getRelationshipCollections() {
        return relationshipCollections;
    }

    public Set<String> getReadOnlyAttributes() {
        return readOnlyAttributes;
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
