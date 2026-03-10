// src/main/java/org/forgerock/openicf/connectors/aic/handler/OrgHandler.java
package org.forgerock.openicf.connectors.aic.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.forgerock.openicf.connectors.aic.PingAICConfiguration;
import org.forgerock.openicf.connectors.aic.util.CrestFilterTranslator;
import org.forgerock.openicf.connectors.aic.util.CrestHttpClient;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrgHandler extends AbstractHandler {

    private static final CrestFilterTranslator FILTER_TRANSLATOR = new CrestFilterTranslator();
    private static final String NAME_ATTRIBUTE = "name";

    private final Map<String, String> singularRelationships;

    public OrgHandler(CrestHttpClient http, String resourceUrl, PingAICConfiguration cfg,
                      Map<String, String> relationshipCollections,
                      Map<String, String> singularRelationships,
                      Set<String> readOnlyRelationships) {
        super(http, resourceUrl, cfg, relationshipCollections, readOnlyRelationships);
        this.singularRelationships = singularRelationships;
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> attributes, OperationOptions options) {
        Set<Attribute> scalarAttrs = new LinkedHashSet<>();
        Set<Attribute> arrayRelAttrs = new LinkedHashSet<>();
        Set<Attribute> singularRelAttrs = new LinkedHashSet<>();
        for (Attribute attr : attributes) {
            String name = attr.getName();
            if (readOnlyRelationships.contains(name)) {
                continue;
            }
            if (singularRelationships.containsKey(name)) {
                singularRelAttrs.add(attr);
            } else if (relationshipCollections.containsKey(name)) {
                arrayRelAttrs.add(attr);
            } else {
                scalarAttrs.add(attr);
            }
        }

        ObjectNode body = attributesToJson(scalarAttrs, NAME_ATTRIBUTE);
        JsonNode response = http.post(resourceUrl + "?_action=create", body);
        String objectId = response.get("_id").asText();

        for (Attribute attr : arrayRelAttrs) {
            syncRelationship(objectId, attr.getName(), attr.getValue());
        }
        for (Attribute attr : singularRelAttrs) {
            syncSingularRelationship(objectId, attr.getName(), AttributeUtil.getStringValue(attr));
        }

        return new Uid(objectId);
    }

    @Override
    public ConnectorObject getObject(ObjectClass objectClass, Uid uid, OperationOptions options) {
        JsonNode response = http.get(resourceUrl + "/" + uid.getUidValue(), null);
        ConnectorObject base = connectorObjectFromJson(response, objectClass, NAME_ATTRIBUTE);
        return enrichWithRelationships(base, options);
    }

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions options) {
        String orgId = uid.getUidValue();

        Set<Attribute> scalarAttrs = new LinkedHashSet<>();
        Set<Attribute> arrayRelAttrs = new LinkedHashSet<>();
        Set<Attribute> singularRelAttrs = new LinkedHashSet<>();
        for (Attribute attr : attributes) {
            String name = attr.getName();
            if (readOnlyRelationships.contains(name)) {
                continue;
            }
            if (singularRelationships.containsKey(name)) {
                singularRelAttrs.add(attr);
            } else if (relationshipCollections.containsKey(name)) {
                arrayRelAttrs.add(attr);
            } else {
                scalarAttrs.add(attr);
            }
        }

        if (!scalarAttrs.isEmpty()) {
            ArrayNode patchOps = attributesToPatchOps(scalarAttrs);
            http.patch(resourceUrl + "/" + orgId, patchOps);
        }

        for (Attribute attr : arrayRelAttrs) {
            syncRelationship(orgId, attr.getName(), attr.getValue());
        }
        for (Attribute attr : singularRelAttrs) {
            syncSingularRelationship(orgId, attr.getName(), AttributeUtil.getStringValue(attr));
        }

        return uid;
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        http.delete(resourceUrl + "/" + uid.getUidValue());
    }

    @Override
    protected ConnectorObject enrichWithRelationships(ConnectorObject base, OperationOptions options) {
        if (options.getAttributesToGet() == null) {
            return base;
        }
        return super.enrichWithRelationships(base, options);
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter filter, ResultsHandler handler, OperationOptions options) {
        List<String> translated = FILTER_TRANSLATOR.translate(filter);
        String translatedFilter = (translated != null && !translated.isEmpty()) ? translated.get(0) : null;

        if (translatedFilter == null && filter instanceof EqualsFilter) {
            EqualsFilter eq = (EqualsFilter) filter;
            if (eq.getAttribute().is(Uid.NAME) || eq.getAttribute().is(Name.NAME)) {
                String uidValue = AttributeUtil.getStringValue(eq.getAttribute());
                ConnectorObject obj = getObject(objectClass, new Uid(uidValue), options);
                if (obj != null) {
                    handler.handle(obj);
                }
                return;
            }
        }

        String queryFilter = buildQueryFilter(translatedFilter, cfg.getOrgBaseFilter());
        if (queryFilter == null) {
            queryFilter = "true";
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("_queryFilter", queryFilter);
        if (options.getPageSize() != null) {
            params.put("_pageSize", options.getPageSize().toString());
            if (options.getPagedResultsCookie() != null) {
                params.put("_pagedResultsCookie", options.getPagedResultsCookie());
            } else if (options.getPagedResultsOffset() != null) {
                params.put("_pagedResultsOffset", options.getPagedResultsOffset().toString());
            }
        }

        JsonNode response = http.get(resourceUrl, params);
        JsonNode results = response.get("result");
        if (results != null) {
            for (JsonNode node : results) {
                ConnectorObject obj = connectorObjectFromJson(node, objectClass, NAME_ATTRIBUTE);
                handler.handle(enrichWithRelationships(obj, options));
            }
        }

        JsonNode cookieNode = response.get("pagedResultsCookie");
        String nextCookie = (cookieNode == null || cookieNode.isNull()) ? null : cookieNode.asText();
        JsonNode remainingNode = response.get("remainingPagedResults");
        int remaining = (remainingNode == null || remainingNode.isNull()) ? -1 : remainingNode.asInt();
        if (handler instanceof SearchResultsHandler) {
            ((SearchResultsHandler) handler).handleResult(new SearchResult(nextCookie, remaining));
        }
    }
}
