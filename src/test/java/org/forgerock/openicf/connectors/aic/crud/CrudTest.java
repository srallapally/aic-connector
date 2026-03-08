// src/test/java/org/forgerock/openicf/connectors/aic/crud/CrudTest.java
package org.forgerock.openicf.connectors.aic.crud;

import org.forgerock.openicf.connectors.ConnectorHarnessTest;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CrudTest extends ConnectorHarnessTest {

    private static final String TEST_USERNAME_PREFIX = "icf_test_";

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void createUpdateDelete_lifecycle() {
        String username = TEST_USERNAME_PREFIX + System.currentTimeMillis();
        Uid uid = null;
        try {
            // Create
            Set<Attribute> createAttrs = new HashSet<>();
            createAttrs.add(new Name(username));
            createAttrs.add(AttributeBuilder.build("givenName", "ICF"));
            createAttrs.add(AttributeBuilder.build("sn", "Test"));
            createAttrs.add(AttributeBuilder.build("mail", username + "@test.invalid"));
            createAttrs.add(AttributeBuilder.build("accountStatus", "active"));

            uid = facade.create(ObjectClass.ACCOUNT, createAttrs, new OperationOptionsBuilder().build());
            assertNotNull(uid, "create() returned null uid");
            assertFalse(uid.getUidValue().isBlank(), "create() returned blank uid");

            // Verify exists
            ConnectorObject created = getByUid(uid);
            assertNotNull(created, "User not found after create");
            assertEquals(username, created.getName().getNameValue());

            // Update scalar attribute
            Set<Attribute> updateAttrs = new HashSet<>();
            updateAttrs.add(AttributeBuilder.build("displayName", "ICF Test User"));
            Uid updatedUid = facade.update(ObjectClass.ACCOUNT, uid, updateAttrs,
                    new OperationOptionsBuilder().build());
            assertNotNull(updatedUid);

            // Verify update
            ConnectorObject updated = getByUid(uid);
            assertNotNull(updated);
            Attribute displayName = updated.getAttributeByName("displayName");
            assertNotNull(displayName);
            assertEquals("ICF Test User", AttributeUtil.getStringValue(displayName));
        } finally {
            // Delete — always runs even if assertions fail
            if (uid != null) {
                facade.delete(ObjectClass.ACCOUNT, uid, new OperationOptionsBuilder().build());

                // Verify deleted
                ConnectorObject deleted = getByUid(uid);
                assertNull(deleted, "User still exists after delete");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ConnectorObject getByUid(Uid uid) {
        List<ConnectorObject> results = new ArrayList<>();
        facade.search(
                ObjectClass.ACCOUNT,
                FilterBuilder.equalTo(uid),
                results::add,
                new OperationOptionsBuilder().build()
        );
        return results.isEmpty() ? null : results.get(0);
    }
}
