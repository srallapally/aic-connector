// src/test/java/org/forgerock/openicf/connectors/aic/schema/UserSchemaHandlerTest.java
package org.forgerock.openicf.connectors.aic.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSchemaHandlerTest {

    private static ObjectClassInfo oci;
    private static Map<String, String> relationshipCollections;
    private static Set<String> readOnlyAttributes;

    @BeforeAll
    static void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File("docs/user.json"));
        JsonNode schemaNode = root.path("result").path(0);

        UserSchemaHandler handler = new UserSchemaHandler(null, "alpha");
        oci = handler.buildFromSchema(schemaNode);
        relationshipCollections = handler.getRelationshipCollections();
        readOnlyAttributes = handler.getReadOnlyAttributes();
    }

    private static AttributeInfo findAttr(String name) {
        for (AttributeInfo ai : oci.getAttributeInfo()) {
            if (ai.getName().equals(name)) {
                return ai;
            }
        }
        return null;
    }

    // ── 1. Writable relationships ────────────────────────────────────────────

    @Test
    void writableRelationships_inRelationshipCollections_andUpdateable() {
        String[] expected = {
                "roles", "assignments", "applications", "groups",
                "memberOfOrg", "adminOfOrg", "ownerOfOrg"
        };
        for (String name : expected) {
            assertTrue(relationshipCollections.containsKey(name),
                    name + " should be in relationshipCollections");
            assertFalse(readOnlyAttributes.contains(name),
                    name + " should NOT be in readOnlyAttributes");

            AttributeInfo ai = findAttr(name);
            assertNotNull(ai, name + " should be registered as an attribute");
            assertTrue(ai.isMultiValued(), name + " should be multi-valued");
            assertEquals(String.class, ai.getType(), name + " should be String type");
            assertFalse(ai.isCreateable(), name + " should NOT be createable");
            assertTrue(ai.isUpdateable(), name + " should be updateable");
            assertFalse(ai.isReturnedByDefault(), name + " should NOT be returnedByDefault");
        }
    }

    // ── 2. Virtual effective* attributes ─────────────────────────────────────

    @Test
    void effectiveAttributes_inReadOnlyAttributes_andNotUpdateable() {
        String[] expected = {
                "effectiveRoles", "effectiveAssignments",
                "effectiveApplications", "effectiveGroups"
        };
        for (String name : expected) {
            assertTrue(readOnlyAttributes.contains(name),
                    name + " should be in readOnlyAttributes");
            assertFalse(relationshipCollections.containsKey(name),
                    name + " should NOT be in relationshipCollections");

            AttributeInfo ai = findAttr(name);
            assertNotNull(ai, name + " should be registered as an attribute");
            assertTrue(ai.isMultiValued(), name + " should be multi-valued");
            assertEquals(String.class, ai.getType(), name + " should be String type");
            assertFalse(ai.isCreateable(), name + " should NOT be createable");
            assertFalse(ai.isUpdateable(), name + " should NOT be updateable");
        }
    }

    // ── 3. memberOfOrgIDs — virtual string array, read-only ──────────────────

    @Test
    void memberOfOrgIDs_isReadOnlyMultivaluedString_notInRelationshipCollections() {
        assertFalse(relationshipCollections.containsKey("memberOfOrgIDs"),
                "memberOfOrgIDs should NOT be in relationshipCollections");
        assertTrue(readOnlyAttributes.contains("memberOfOrgIDs"),
                "memberOfOrgIDs should be in readOnlyAttributes");

        AttributeInfo ai = findAttr("memberOfOrgIDs");
        assertNotNull(ai, "memberOfOrgIDs should be registered as an attribute");
        assertTrue(ai.isMultiValued(), "memberOfOrgIDs should be multi-valued");
        assertEquals(String.class, ai.getType(), "memberOfOrgIDs should be String type");
        assertFalse(ai.isCreateable(), "memberOfOrgIDs should NOT be createable");
        assertFalse(ai.isUpdateable(), "memberOfOrgIDs should NOT be updateable");
    }

    // ── 4. adminOfOrgIDs, ownerOfOrgIDs — do not exist in schema ─────────────

    @Test
    void nonExistentOrgIDFields_areNotRegistered() {
        assertNull(findAttr("adminOfOrgIDs"),
                "adminOfOrgIDs should not exist as an attribute");
        assertNull(findAttr("ownerOfOrgIDs"),
                "ownerOfOrgIDs should not exist as an attribute");
    }

    // ── 5. Scalar with userEditable=false ────────────────────────────────────

    @Test
    void scalarWithUserEditableFalse_isNotUpdateable() {
        AttributeInfo ai = findAttr("passwordLastChangedTime");
        assertNotNull(ai, "passwordLastChangedTime should be registered");
        assertFalse(ai.isUpdateable(),
                "passwordLastChangedTime (userEditable=false) should NOT be updateable");
        assertFalse(ai.isCreateable(),
                "passwordLastChangedTime (userEditable=false) should NOT be createable");
    }
}
