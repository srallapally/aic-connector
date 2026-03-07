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

import java.util.Iterator;
import java.util.Map;

public class UserSchemaHandler {

    private final CrestHttpClient http;

    public UserSchemaHandler(CrestHttpClient http) {
        this.http = http;
    }

    public ObjectClassInfo getObjectClassInfo() {
        JsonNode schema = http.get("/openidm/schema/managed/alpha_user", null);
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            throw new ConnectorException("Invalid schema response: missing 'properties' field");
        }

        ObjectClassInfoBuilder ocib = new ObjectClassInfoBuilder();
        ocib.setType(ObjectClass.ACCOUNT_NAME);

        for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String propName = entry.getKey();
            JsonNode propDef = entry.getValue();

            if ("_id".equals(propName)) {
                continue;
            }

            String type = propDef.path("type").asText("string");
            if ("array".equals(type) && propDef.path("items").has("$ref")) {
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
            if (!userEditable) {
                aib.setCreateable(false);
                aib.setUpdateable(false);
            }
            if (!returnByDefault) {
                aib.setReturnedByDefault(false);
            }

            ocib.addAttributeInfo(aib.build());
        }

        for (String relAttr : new String[]{"roles", "memberOfOrg", "adminOfOrg", "ownerOfOrg"}) {
            AttributeInfoBuilder rab = new AttributeInfoBuilder();
            rab.setName(relAttr);
            rab.setType(String.class);
            rab.setMultiValued(true);
            rab.setReturnedByDefault(false);
            rab.setCreateable(false);
            rab.setUpdateable(true);
            ocib.addAttributeInfo(rab.build());
        }

        return ocib.build();
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
