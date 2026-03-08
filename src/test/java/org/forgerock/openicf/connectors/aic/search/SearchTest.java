// src/test/java/org/forgerock/openicf/connectors/aic/search/SearchTest.java
package org.forgerock.openicf.connectors.aic.search;

import org.forgerock.openicf.connectors.ConnectorHarnessTest;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SearchTest extends ConnectorHarnessTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<ConnectorObject> searchAll(OperationOptions options) {
        List<ConnectorObject> results = new ArrayList<>();
        facade.search(ObjectClass.ACCOUNT, null, results::add, options);
        return results;
    }

    private OperationOptions defaultOptions() {
        return new OperationOptionsBuilder().setPageSize(10).build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void searchAll_returnsResults() {
        List<ConnectorObject> results = searchAll(defaultOptions());
        assertFalse(results.isEmpty(), "Expected at least one user from searchAll");
    }

    @Test
    void searchAll_populatesRelationshipAttributes() {
        List<ConnectorObject> results = searchAll(defaultOptions());
        assertFalse(results.isEmpty());

        ConnectorObject first = results.get(0);
        // Attribute must be present — value may be an empty list if user has no memberships
        assertNotNull(first.getAttributeByName("roles"),
                "roles attribute missing from search result");
        assertNotNull(first.getAttributeByName("applications"),
                "applications attribute missing from search result");
        assertNotNull(first.getAttributeByName("assignments"),
                "assignments attribute missing from search result");
    }

    @Test
    void searchByUid_returnsSingleResult() {
        // Get any uid from a broad search first
        List<ConnectorObject> all = searchAll(defaultOptions());
        assertFalse(all.isEmpty());
        String uid = all.get(0).getUid().getUidValue();

        List<ConnectorObject> results = new ArrayList<>();
        facade.search(
                ObjectClass.ACCOUNT,
                FilterBuilder.equalTo(new Uid(uid)),
                results::add,
                defaultOptions()
        );

        assertEquals(1, results.size(), "Expected exactly one result for uid=" + uid);
        assertEquals(uid, results.get(0).getUid().getUidValue());
    }

    @Test
    void searchAll_effectiveAssignments_containsJsonStrings() {
        // Find a user that has effectiveAssignments populated
        OperationOptions opts = new OperationOptionsBuilder()
                .setPageSize(50)
                .setAttributesToGet("effectiveAssignments", "__UID__", "__NAME__")
                .build();

        List<ConnectorObject> results = new ArrayList<>();
        facade.search(ObjectClass.ACCOUNT, null, results::add, opts);

        ConnectorObject userWithAssignments = results.stream()
                .filter(obj -> {
                    Attribute ea = obj.getAttributeByName("effectiveAssignments");
                    return ea != null && ea.getValue() != null && !ea.getValue().isEmpty();
                })
                .findFirst()
                .orElse(null);

        assertNotNull(userWithAssignments,
                "No user found with non-empty effectiveAssignments in first 50 results");

        Attribute ea = userWithAssignments.getAttributeByName("effectiveAssignments");
        Object first = ea.getValue().get(0);
        assertInstanceOf(String.class, first);
        String json = (String) first;
        assertTrue(json.contains("_refResourceId"),
                "effectiveAssignments value does not look like a serialized ref-object: " + json);
    }
}
