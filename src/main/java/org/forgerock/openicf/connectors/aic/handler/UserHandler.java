// src/main/java/org/forgerock/openicf/connectors/aic/handler/UserHandler.java
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
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserHandler extends AbstractHandler {

    private static final CrestFilterTranslator FILTER_TRANSLATOR = new CrestFilterTranslator();
    private static final String NAME_ATTRIBUTE = "userName";

    private final Map<String, String> relationshipCollections;
    private final Set<String> readOnlyRelationships;

    public UserHandler(CrestHttpClient http, String resourceUrl, PingAICConfiguration cfg,
                       Map<String, String> relationshipCollections,
                       Set<String> readOnlyRelationships) {
        super(http, resourceUrl, cfg);
        this.relationshipCollections = relationshipCollections;
        this.readOnlyRelationships = readOnlyRelationships;
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> attributes, OperationOptions options) {
        ObjectNode body = attributesToJson(attributes, NAME_ATTRIBUTE);
        JsonNode response = http.post(resourceUrl + "?_action=create", body);
        return new Uid(response.get("_id").asText());
    }

    @Override
    public ConnectorObject getObject(ObjectClass objectClass, Uid uid, OperationOptions options) {
        JsonNode response = http.get(resourceUrl + "/" + uid.getUidValue(), null);
        ConnectorObject base = connectorObjectFromJson(response, objectClass, NAME_ATTRIBUTE);
        return enrichWithRelationships(base, options);
    }

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions options) {
        String userId = uid.getUidValue();

        Set<Attribute> scalarAttrs = new LinkedHashSet<>();
        Set<Attribute> relationshipAttrs = new LinkedHashSet<>();
        for (Attribute attr : attributes) {
            String name = attr.getName();
            if (readOnlyRelationships.contains(name)) {
                continue;
            }
            if (relationshipCollections.containsKey(name)) {
                relationshipAttrs.add(attr);
            } else {
                scalarAttrs.add(attr);
            }
        }

        if (!scalarAttrs.isEmpty()) {
            ArrayNode patchOps = attributesToPatchOps(scalarAttrs);
            http.patch(resourceUrl + "/" + userId, patchOps);
        }

        for (Attribute attr : relationshipAttrs) {
            syncRelationship(userId, attr.getName(), attr.getValue());
        }

        return uid;
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        http.delete(resourceUrl + "/" + uid.getUidValue());
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

        String queryFilter = buildQueryFilter(translatedFilter, cfg.getUserBaseFilter());
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

    // ── Relationship read helpers ─────────────────────────────────────────────

    private ConnectorObject enrichWithRelationships(ConnectorObject base, OperationOptions options) {
        String[] attrsToGet = options.getAttributesToGet();
        if (attrsToGet == null) {
            return base;
        }

        Set<String> requestedRelationships = new HashSet<>();
        for (String attr : attrsToGet) {
            if (relationshipCollections.containsKey(attr)) {
                requestedRelationships.add(attr);
            }
        }
        if (requestedRelationships.isEmpty()) {
            return base;
        }

        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(base.getObjectClass());
        builder.setUid(base.getUid());
        builder.setName(base.getName());
        for (Attribute attr : base.getAttributes()) {
            if (!attr.is(Uid.NAME) && !attr.is(Name.NAME)) {
                builder.addAttribute(attr);
            }
        }

        String userId = base.getUid().getUidValue();
        for (String relAttr : requestedRelationships) {
            List<String> refIds = fetchRelationshipIds(userId, relAttr);
            builder.addAttribute(relAttr, refIds);
        }

        return builder.build();
    }

    private List<String> fetchRelationshipIds(String userId, String fieldName) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("_queryFilter", "true");
        params.put("_fields", "_ref,_refResourceCollection,_refResourceId,_refProperties");
        JsonNode response = http.get(resourceUrl + "/" + userId + "/" + fieldName, params);

        List<String> ids = new ArrayList<>();
        JsonNode results = response.get("result");
        if (results != null) {
            for (JsonNode node : results) {
                JsonNode refId = node.get("_refResourceId");
                if (refId != null && !refId.isNull()) {
                    ids.add(refId.asText());
                }
            }
        }
        return ids;
    }

    // ── Relationship write helpers ────────────────────────────────────────────

    private void syncRelationship(String userId, String fieldName, List<Object> desiredValues) {
        Set<String> desired = new LinkedHashSet<>();
        if (desiredValues != null) {
            for (Object v : desiredValues) {
                if (v != null) {
                    desired.add(v.toString());
                }
            }
        }

        Map<String, RelationshipEntry> current = fetchCurrentRelationships(userId, fieldName);

        Set<String> toAdd = new LinkedHashSet<>(desired);
        toAdd.removeAll(current.keySet());
        Set<String> toRemove = new LinkedHashSet<>(current.keySet());
        toRemove.removeAll(desired);

        for (String id : toAdd) {
            applyRelationshipAdd(userId, fieldName, id);
        }
        for (String id : toRemove) {
            applyRelationshipRemove(userId, fieldName, current.get(id));
        }
    }

    private Map<String, RelationshipEntry> fetchCurrentRelationships(String userId, String fieldName) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("_queryFilter", "true");
        params.put("_fields", "_ref,_refResourceCollection,_refResourceId,_refProperties");
        JsonNode response = http.get(resourceUrl + "/" + userId + "/" + fieldName, params);

        Map<String, RelationshipEntry> map = new LinkedHashMap<>();
        JsonNode results = response.get("result");
        if (results != null) {
            for (JsonNode node : results) {
                String refId = node.path("_refResourceId").asText(null);
                if (refId != null) {
                    RelationshipEntry entry = new RelationshipEntry(
                            node.path("_ref").asText(),
                            node.path("_refResourceCollection").asText(),
                            refId,
                            node.path("_refProperties").path("_id").asText(),
                            node.path("_refProperties").path("_rev").asText()
                    );
                    map.put(refId, entry);
                }
            }
        }
        return map;
    }

    private void applyRelationshipAdd(String userId, String fieldName, String targetId) {
        String collectionPath = relationshipCollections.get(fieldName);
        ArrayNode ops = MAPPER.createArrayNode();
        ObjectNode op = ops.addObject();
        op.put("operation", "add");
        op.put("field", "/" + fieldName + "/-");
        ObjectNode value = MAPPER.createObjectNode();
        value.put("_ref", collectionPath + "/" + targetId);
        op.set("value", value);
        http.patch(resourceUrl + "/" + userId, ops);
    }

    private void applyRelationshipRemove(String userId, String fieldName,
                                         RelationshipEntry entry) {
        ArrayNode ops = MAPPER.createArrayNode();
        ObjectNode op = ops.addObject();
        op.put("operation", "remove");
        op.put("field", "/" + fieldName);
        ObjectNode value = MAPPER.createObjectNode();
        value.put("_ref", entry.ref);
        value.put("_refResourceCollection", entry.refResourceCollection);
        value.put("_refResourceId", entry.refResourceId);
        ObjectNode refProps = MAPPER.createObjectNode();
        refProps.put("_id", entry.refPropertiesId);
        refProps.put("_rev", entry.refPropertiesRev);
        value.set("_refProperties", refProps);
        op.set("value", value);
        http.patch(resourceUrl + "/" + userId, ops);
    }

    // ── RelationshipEntry ─────────────────────────────────────────────────────

    private static final class RelationshipEntry {
        final String ref;
        final String refResourceCollection;
        final String refResourceId;
        final String refPropertiesId;
        final String refPropertiesRev;

        RelationshipEntry(String ref, String refResourceCollection, String refResourceId,
                          String refPropertiesId, String refPropertiesRev) {
            this.ref = ref;
            this.refResourceCollection = refResourceCollection;
            this.refResourceId = refResourceId;
            this.refPropertiesId = refPropertiesId;
            this.refPropertiesRev = refPropertiesRev;
        }
    }
}
