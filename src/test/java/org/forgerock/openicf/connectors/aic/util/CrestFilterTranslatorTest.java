// src/test/java/org/forgerock/openicf/connectors/aic/util/CrestFilterTranslatorTest.java
package org.forgerock.openicf.connectors.aic.util;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.OrFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrestFilterTranslatorTest {

    private final CrestFilterTranslator translator = new CrestFilterTranslator();

    private static Attribute attr(String name, String value) {
        return new AttributeBuilder().setName(name).addValue(value).build();
    }

    // ── EqualsFilter on __UID__ ────────────────────────────────────────────────

    @Test
    void equalsFilter_onUid_returnsEmptyList() {
        Filter filter = new EqualsFilter(attr(Uid.NAME, "6b49de5e-a6e4-41c4-a7e8-3bb9e4d61e13"));
        List<String> result = translator.translate(filter);
        assertTrue(result == null || result.isEmpty(),
                "UID EqualsFilter should not produce a _queryFilter expression");
    }

    // ── EqualsFilter on a regular attribute ───────────────────────────────────

    @Test
    void equalsFilter_onUserName_returnsEqExpression() {
        Filter filter = new EqualsFilter(attr("userName", "alice"));
        List<String> result = translator.translate(filter);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("userName eq \"alice\"", result.get(0));
    }

    // ── StartsWithFilter ──────────────────────────────────────────────────────

    @Test
    void startsWithFilter_onUserName_returnsSwExpression() {
        Filter filter = new StartsWithFilter(attr("userName", "ali"));
        List<String> result = translator.translate(filter);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("userName sw \"ali\"", result.get(0));
    }

    // ── ContainsFilter ────────────────────────────────────────────────────────

    @Test
    void containsFilter_onUserName_returnsCoExpression() {
        Filter filter = new ContainsFilter(attr("userName", "lic"));
        List<String> result = translator.translate(filter);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("userName co \"lic\"", result.get(0));
    }

    // ── AndFilter ─────────────────────────────────────────────────────────────

    @Test
    void andFilter_ofTwoEquals_returnsParenthesisedAndExpression() {
        Filter left  = new EqualsFilter(attr("givenName", "Alice"));
        Filter right = new EqualsFilter(attr("sn", "Smith"));
        List<String> result = translator.translate(new AndFilter(left, right));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("(givenName eq \"Alice\" and sn eq \"Smith\")", result.get(0));
    }

    // ── OrFilter ──────────────────────────────────────────────────────────────

    @Test
    void orFilter_ofTwoEquals_returnsParenthesisedOrExpression() {
        Filter left  = new EqualsFilter(attr("givenName", "Alice"));
        Filter right = new EqualsFilter(attr("givenName", "Bob"));
        List<String> result = translator.translate(new OrFilter(left, right));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("(givenName eq \"Alice\" or givenName eq \"Bob\")", result.get(0));
    }

    // ── NotFilter ─────────────────────────────────────────────────────────────

    @Test
    void notFilter_wrappingEquals_returnsBangExpression() {
        Filter inner  = new EqualsFilter(attr("active", "true"));
        List<String> result = translator.translate(new NotFilter(inner));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("!(active eq \"true\")", result.get(0));
    }

    // ── Unsupported filters ───────────────────────────────────────────────────

    @Test
    void endsWithFilter_throwsUnsupportedOperationException() {
        Filter filter = new EndsWithFilter(attr("userName", "suffix"));
        assertThrows(UnsupportedOperationException.class, () -> translator.translate(filter));
    }

    @Test
    void greaterThanFilter_throwsUnsupportedOperationException() {
        Filter filter = new GreaterThanFilter(attr("age", "30"));
        assertThrows(UnsupportedOperationException.class, () -> translator.translate(filter));
    }
}
