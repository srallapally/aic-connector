// src/test/java/org/forgerock/openicf/connectors/aic/crud/OrgCrudTest.java
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
public class OrgCrudTest extends ConnectorHarnessTest {

    private static final ObjectClass ORG = new ObjectClass("organization");
    private static final String TEST_ORG_PREFIX = "icf_test_org_";

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void createUpdateDelete_lifecycle() {
        String orgName = TEST_ORG_PREFIX + System.currentTimeMillis();
        Uid targetUserUid = fetchAnyUserUid();
        assertNotNull(targetUserUid, "No user found in tenant to use as relationship target");

        Uid uid = null;
        try {
            // Create
            Set<Attribute> createAttrs = new HashSet<>();
            createAttrs.add(new Name(orgName));
            createAttrs.add(AttributeBuilder.build("description", "test org"));

            uid = facade.create(ORG, createAttrs, new OperationOptionsBuilder().build());
            assertNotNull(uid, "create() returned null uid");
            assertFalse(uid.getUidValue().isBlank(), "create() returned blank uid");

            // Verify exists
            ConnectorObject created = getByUid(ORG, uid);
            assertNotNull(created, "Org not found after create");
            assertEquals(orgName, created.getName().getNameValue());

            // Update scalar attribute
            Set<Attribute> updateAttrs = new HashSet<>();
            updateAttrs.add(AttributeBuilder.build("description", "updated org"));
            Uid updatedUid = facade.update(ORG, uid, updateAttrs,
                    new OperationOptionsBuilder().build());
            assertNotNull(updatedUid);

            // Verify update
            ConnectorObject updated = getByUid(ORG, uid);
            assertNotNull(updated);
            Attribute description = updated.getAttributeByName("description");
            assertNotNull(description);
            assertEquals("updated org", AttributeUtil.getStringValue(description));

            // Relationship grant/revoke for admins, members, owners
            String targetId = targetUserUid.getUidValue();
            for (String rel : new String[]{"admins", "members", "owners"}) {
                // Grant
                Set<Attribute> grantAttrs = new HashSet<>();
                grantAttrs.add(AttributeBuilder.build(rel, targetId));
                facade.update(ORG, uid, grantAttrs, new OperationOptionsBuilder().build());

                ConnectorObject withRel = getByUidWithAttrs(uid, rel);
                assertNotNull(withRel, "Org not found after " + rel + " grant");
                Attribute relAttr = withRel.getAttributeByName(rel);
                assertNotNull(relAttr, rel + " attribute missing after grant");
                assertTrue(relAttr.getValue().contains(targetId),
                        rel + " does not contain target user after grant");

                // Revoke
                Set<Attribute> revokeAttrs = new HashSet<>();
                revokeAttrs.add(AttributeBuilder.build(rel));
                facade.update(ORG, uid, revokeAttrs, new OperationOptionsBuilder().build());

                ConnectorObject withoutRel = getByUidWithAttrs(uid, rel);
                assertNotNull(withoutRel, "Org not found after " + rel + " revoke");
                Attribute clearedAttr = withoutRel.getAttributeByName(rel);
                assertTrue(clearedAttr == null || clearedAttr.getValue() == null
                                || clearedAttr.getValue().isEmpty(),
                        rel + " should be empty after revoke");
            }
        } finally {
            if (uid != null) {
                facade.delete(ORG, uid, new OperationOptionsBuilder().build());

                ConnectorObject deleted = getByUid(ORG, uid);
                assertNull(deleted, "Org still exists after delete");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ConnectorObject getByUid(ObjectClass oc, Uid uid) {
        List<ConnectorObject> results = new ArrayList<>();
        facade.search(
                oc,
                FilterBuilder.equalTo(uid),
                results::add,
                new OperationOptionsBuilder().build()
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private ConnectorObject getByUidWithAttrs(Uid uid, String... attrs) {
        List<ConnectorObject> results = new ArrayList<>();
        facade.search(
                ORG,
                FilterBuilder.equalTo(uid),
                results::add,
                new OperationOptionsBuilder().setAttributesToGet(attrs).build()
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private Uid fetchAnyUserUid() {
        List<ConnectorObject> results = new ArrayList<>();
        facade.search(
                ObjectClass.ACCOUNT,
                null,
                results::add,
                new OperationOptionsBuilder().setPageSize(1).build()
        );
        return results.isEmpty() ? null : results.get(0).getUid();
    }
}
