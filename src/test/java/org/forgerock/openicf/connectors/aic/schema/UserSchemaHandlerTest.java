// src/test/java/org/forgerock/openicf/connectors/aic/schema/UserSchemaHandlerTest.java
package org.forgerock.openicf.connectors.aic.schema;

import org.forgerock.openicf.connectors.ConnectorHarnessTest;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UserSchemaHandlerTest extends ConnectorHarnessTest {

    private Map<String, AttributeInfo> accountAttrs;

    @BeforeAll
    void fetchSchema() {
        Schema schema = facade.schema();
        ObjectClassInfo accountInfo = schema.findObjectClassInfo(ObjectClass.ACCOUNT_NAME);
        assertNotNull(accountInfo, "__ACCOUNT__ object class not found in schema");
        accountAttrs = accountInfo.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, a -> a));
    }

    // ── Writable relationship attributes ─────────────────────────────────────

    @Test
    void roles_isPresent() {
        assertTrue(accountAttrs.containsKey("roles"), "roles not found in schema");
    }

    @Test
    void roles_isMultiValued() {
        assertTrue(accountAttrs.get("roles").isMultiValued());
    }

    @Test
    void roles_isUpdateable() {
        assertTrue(accountAttrs.get("roles").isUpdateable());
    }

    @Test
    void roles_isReturnedByDefault() {
        assertTrue(accountAttrs.get("roles").isReturnedByDefault());
    }

    @Test
    void applications_isPresent() {
        assertTrue(accountAttrs.containsKey("applications"), "applications not found in schema");
    }

    @Test
    void applications_isMultiValued() {
        assertTrue(accountAttrs.get("applications").isMultiValued());
    }

    @Test
    void applications_isUpdateable() {
        assertTrue(accountAttrs.get("applications").isUpdateable());
    }

    @Test
    void applications_isReturnedByDefault() {
        assertTrue(accountAttrs.get("applications").isReturnedByDefault());
    }

    @Test
    void assignments_isPresent() {
        assertTrue(accountAttrs.containsKey("assignments"), "assignments not found in schema");
    }

    @Test
    void assignments_isMultiValued() {
        assertTrue(accountAttrs.get("assignments").isMultiValued());
    }

    @Test
    void assignments_isUpdateable() {
        assertTrue(accountAttrs.get("assignments").isUpdateable());
    }

    @Test
    void assignments_isReturnedByDefault() {
        assertTrue(accountAttrs.get("assignments").isReturnedByDefault());
    }

    // ── Read-only effective attributes ────────────────────────────────────────

    @Test
    void effectiveRoles_isPresent() {
        assertTrue(accountAttrs.containsKey("effectiveRoles"), "effectiveRoles not found in schema");
    }

    @Test
    void effectiveRoles_isNotUpdateable() {
        assertFalse(accountAttrs.get("effectiveRoles").isUpdateable());
    }

    @Test
    void effectiveRoles_isNotReturnedByDefault() {
        assertFalse(accountAttrs.get("effectiveRoles").isReturnedByDefault());
    }

    @Test
    void effectiveAssignments_isPresent() {
        assertTrue(accountAttrs.containsKey("effectiveAssignments"), "effectiveAssignments not found in schema");
    }

    @Test
    void effectiveAssignments_isNotUpdateable() {
        assertFalse(accountAttrs.get("effectiveAssignments").isUpdateable());
    }

    @Test
    void effectiveAssignments_isNotReturnedByDefault() {
        assertFalse(accountAttrs.get("effectiveAssignments").isReturnedByDefault());
    }

    @Test
    void effectiveApplications_isPresent() {
        assertTrue(accountAttrs.containsKey("effectiveApplications"), "effectiveApplications not found in schema");
    }

    @Test
    void effectiveApplications_isNotUpdateable() {
        assertFalse(accountAttrs.get("effectiveApplications").isUpdateable());
    }

    @Test
    void effectiveApplications_isNotReturnedByDefault() {
        assertFalse(accountAttrs.get("effectiveApplications").isReturnedByDefault());
    }

    @Test
    void effectiveGroups_isPresent() {
        assertTrue(accountAttrs.containsKey("effectiveGroups"), "effectiveGroups not found in schema");
    }

    @Test
    void effectiveGroups_isNotUpdateable() {
        assertFalse(accountAttrs.get("effectiveGroups").isUpdateable());
    }

    @Test
    void effectiveGroups_isNotReturnedByDefault() {
        assertFalse(accountAttrs.get("effectiveGroups").isReturnedByDefault());
    }
}
