// src/main/java/org/forgerock/openicf/connectors/aic/handler/CrudqHandler.java
package org.forgerock.openicf.connectors.aic.handler;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.Set;

public interface CrudqHandler {

    Uid create(ObjectClass objectClass, Set<Attribute> attributes, OperationOptions options);

    ConnectorObject getObject(ObjectClass objectClass, Uid uid, OperationOptions options);

    Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions options);

    void delete(ObjectClass objectClass, Uid uid, OperationOptions options);

    void executeQuery(ObjectClass objectClass, Filter filter, ResultsHandler handler, OperationOptions options);
}
