// src/main/java/org/forgerock/openicf/connectors/aic/util/CrestFilterTranslator.java
package org.forgerock.openicf.connectors.aic.util;

import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;


public class CrestFilterTranslator extends AbstractFilterTranslator<String> {

    @Override
    protected String createEqualsExpression(EqualsFilter filter, boolean not) {
        if (filter.getAttribute().is(Uid.NAME)) {
            return null;
        }
        return expr(filter.getAttribute().getName(), "eq",
                AttributeUtil.getStringValue(filter.getAttribute()), not);
    }

    @Override
    protected String createStartsWithExpression(StartsWithFilter filter, boolean not) {
        return expr(filter.getAttribute().getName(), "sw",
                AttributeUtil.getStringValue(filter.getAttribute()), not);
    }

    @Override
    protected String createContainsExpression(ContainsFilter filter, boolean not) {
        return expr(filter.getAttribute().getName(), "co",
                AttributeUtil.getStringValue(filter.getAttribute()), not);
    }

    @Override
    protected String createAndExpression(String left, String right) {
        return "(" + left + " and " + right + ")";
    }

    @Override
    protected String createOrExpression(String left, String right) {
        return "(" + left + " or " + right + ")";
    }

    @Override
    protected String createGreaterThanExpression(GreaterThanFilter filter, boolean not) {
        throw new UnsupportedOperationException("GreaterThan filter is not supported");
    }

    @Override
    protected String createGreaterThanOrEqualExpression(GreaterThanOrEqualFilter filter, boolean not) {
        throw new UnsupportedOperationException("GreaterThanOrEqual filter is not supported");
    }

    @Override
    protected String createLessThanExpression(LessThanFilter filter, boolean not) {
        throw new UnsupportedOperationException("LessThan filter is not supported");
    }

    @Override
    protected String createLessThanOrEqualExpression(LessThanOrEqualFilter filter, boolean not) {
        throw new UnsupportedOperationException("LessThanOrEqual filter is not supported");
    }

    @Override
    protected String createEndsWithExpression(EndsWithFilter filter, boolean not) {
        throw new UnsupportedOperationException("EndsWith filter is not supported");
    }

    @Override
    protected String createContainsAllValuesExpression(ContainsAllValuesFilter filter, boolean not) {
        throw new UnsupportedOperationException("ContainsAllValues filter is not supported");
    }

    private static String expr(String name, String op, String value, boolean not) {
        String base = name + " " + op + " \"" + value + "\"";
        return not ? "!(" + base + ")" : base;
    }
}
