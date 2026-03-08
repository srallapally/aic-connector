// src/main/java/org/forgerock/openicf/connectors/aic/PingAICConnector.java
package org.forgerock.openicf.connectors.aic;

import org.forgerock.openicf.connectors.aic.handler.CrudqHandler;
// import org.forgerock.openicf.connectors.aic.handler.OrganizationHandler;
import org.forgerock.openicf.connectors.aic.handler.RoleHandler;
import org.forgerock.openicf.connectors.aic.handler.UserHandler;
// import org.forgerock.openicf.connectors.aic.schema.OrganizationSchemaHandler;
import org.forgerock.openicf.connectors.aic.schema.RoleSchemaHandler;
import org.forgerock.openicf.connectors.aic.schema.UserSchemaHandler;
import org.forgerock.openicf.connectors.aic.util.CrestHttpClient;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@ConnectorClass(
        displayNameKey = "connector.display",
        configurationClass = PingAICConfiguration.class)
public class PingAICConnector implements
        org.identityconnectors.framework.spi.Connector,
        CreateOp, UpdateOp, DeleteOp,
        SearchOp<Filter>, SchemaOp, TestOp, SyncOp {

    private PingAICConfiguration cfg;
    private CrestHttpClient http;

    private UserHandler userHandler;
//  private OrganizationHandler orgHandler;
    private RoleHandler roleHandler;

    private UserSchemaHandler userSchemaHandler;
//  private OrganizationSchemaHandler orgSchemaHandler;
    private RoleSchemaHandler roleSchemaHandler;

    private ObjectClassInfo userObjectClassInfo;
    private ObjectClassInfo roleObjectClassInfo;

    @Override
    public PingAICConfiguration getConfiguration() {
        return cfg;
    }

    @Override
    public void init(Configuration configuration) {
        this.cfg = (PingAICConfiguration) configuration;
        this.http = new CrestHttpClient(cfg);

        String userUrl = "/openidm/managed/" + cfg.getRealm() + "_user";
//      String orgUrl  = "/openidm/managed/" + cfg.getRealm() + "_organization";
//      String roleUrl = "/openidm/managed/" + cfg.getRealm() + "_role";

        this.userSchemaHandler = new UserSchemaHandler(http, cfg.getRealm());
        this.userObjectClassInfo = userSchemaHandler.getObjectClassInfo();
        Map<String, String> relCollections = userSchemaHandler.getRelationshipCollections();

        this.userHandler = new UserHandler(http, userUrl, cfg, relCollections,
                userSchemaHandler.getReadOnlyAttributes());
//      this.orgHandler  = new OrganizationHandler(http, orgUrl, cfg);

        String roleUrl = "/openidm/managed/" + cfg.getRealm() + "_role";
        this.roleSchemaHandler = new RoleSchemaHandler(http, cfg.getRealm());
        this.roleObjectClassInfo = roleSchemaHandler.getObjectClassInfo();
        this.roleHandler = new RoleHandler(http, roleUrl, cfg,
                roleSchemaHandler.getRelationshipCollections(),
                roleSchemaHandler.getReadOnlyRelationships());

//      this.orgSchemaHandler  = new OrganizationSchemaHandler(http);
    }

    @Override
    public void dispose() {
        try {
            http.close();
        } catch (IOException e) {
            throw new ConnectorException("Failed to close HTTP client: " + e.getMessage(), e);
        }
    }

    // ── SchemaOp ──────────────────────────────────────────────────────────────

    @Override
    public Schema schema() {
        SchemaBuilder builder = new SchemaBuilder(PingAICConnector.class);
        builder.defineObjectClass(userObjectClassInfo);
        builder.defineObjectClass(roleObjectClassInfo);
//      builder.defineObjectClass(orgSchemaHandler.getObjectClassInfo());
        return builder.build();
    }

    // ── TestOp ────────────────────────────────────────────────────────────────

    @Override
    public void test() {
        http.get("/openidm/info/ping", null);
    }

    // ── CreateOp ──────────────────────────────────────────────────────────────

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> attributes, OperationOptions options) {
        return handlerFor(objectClass).create(objectClass, attributes, options);
    }

    // ── UpdateOp ──────────────────────────────────────────────────────────────

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions options) {
        return handlerFor(objectClass).update(objectClass, uid, attributes, options);
    }

    // ── DeleteOp ──────────────────────────────────────────────────────────────

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        handlerFor(objectClass).delete(objectClass, uid, options);
    }

    // ── SearchOp<Filter> ──────────────────────────────────────────────────────

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return filter -> filter == null ? Collections.emptyList() : Collections.singletonList(filter);
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter filter, ResultsHandler handler, OperationOptions options) {
        handlerFor(objectClass).executeQuery(objectClass, filter, handler, options);
    }

    // ── SyncOp ────────────────────────────────────────────────────────────────

    @Override
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler, OperationOptions options) {
        throw new UnsupportedOperationException("LiveSync is not supported. Use reconciliation with timestampAttribute config.");
    }

    @Override
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        throw new UnsupportedOperationException("LiveSync is not supported. Use reconciliation with timestampAttribute config.");
    }

    // ── dispatch ──────────────────────────────────────────────────────────────

    private CrudqHandler handlerFor(ObjectClass objectClass) {
        if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            return userHandler;
        }
//      if (objectClass.is("GROUP")) {
//          return orgHandler;
//      }
        if (objectClass.is("ROLE")) {
            return roleHandler;
        }
        throw new ConnectorException("Unknown object class: " + objectClass.getObjectClassValue());
    }
}
