# Project: ping-aic-connector

## What this is
OpenICF Java connector targeting PingOne Advanced Identity Cloud CREST API.
Provisions managed/alpha_user, managed/alpha_organization, managed/alpha_role.

## Framework
- openicf-java-framework 1.5.20.32 (groupId: org.forgerock.openicf.framework)
- Java 11
- Maven with maven-bundle-plugin (packaging: bundle)

## Object classes
- `__ACCOUNT__`  → /openidm/managed/{realm}_user
- `GROUP`        → /openidm/managed/{realm}_organization
- `ROLE`         → /openidm/managed/{realm}_role

## Operations per object class
- Create  (POST to ?_action=create)
- Update  (PATCH /{id}) — NOT PUT, never PUT
- Delete  (DELETE /{id})
- Search  (GET ?_queryFilter=...) — see executeQuery rules below
- SyncOp  → NOT SUPPORTED. PingAICConnector implements SyncOp and both sync()
  and getLatestSyncToken() throw UnsupportedOperationException with
  message: "LiveSync is not supported. Use reconciliation with timestampAttribute config."

NOTE: There is no GetOp. Read-by-ID is handled inside executeQuery.

## executeQuery rules
- If filter is EqualsFilter on __UID__ or __NAME__ → GET /{id} directly, call
  handler.handle() once with the result. Do not execute a paginated query.
- Otherwise → translate filter via CrestFilterTranslator, compose with
  buildQueryFilter(), execute paginated _queryFilter search.
- If buildQueryFilter() returns null → use _queryFilter=true (return all).
- Page using _pagedResultsCookie loop with _pageSize=100 until cookie is null.

## Authentication
OAuth2 client credentials grant.
Token endpoint: /am/oauth2/{realm}/access_token
Config properties: baseUrl, clientId, clientSecret, realm

## CREST query translation rules
- ICF EqualsFilter on `__UID__` or `__NAME__` → GET /{id} (handled in executeQuery, not translator)
- ICF EqualsFilter on other attr → `_queryFilter=attrName+eq+"value"`
- ICF StartsWithFilter           → `_queryFilter=attrName+sw+"value"`
- ICF ContainsFilter             → `_queryFilter=attrName+co+"value"`
- ICF AndFilter / OrFilter / NotFilter → compose with `and` / `or` / `!`
- All others (EndsWithFilter, GreaterThan, etc.) → throw UnsupportedOperationException
- Always URL-encode the _queryFilter value

## PATCH body format
CREST PATCH is an array of operation objects — not JSON Merge Patch (RFC 7396).
Always use:
[{"operation":"replace","field":"/attrName","value":"..."}]
Never generate a plain JSON diff object for updates.

## Paging
Search responses include `_pagedResultsCookie`. Loop until cookie is null.
Use `_pageSize=100` per request.

## Relationship attributes
Role and org membership arrays (e.g. `roles`, `memberOfOrg`) are relationship
fields in CREST. Do NOT map these as normal attributes. Exclude them from all
schema definitions. They are deferred to a later phase.

---

## Configuration properties

| Property           | Type          | Default | Required | Notes |
|--------------------|---------------|---------|----------|-------|
| baseUrl            | String        | —       | yes      | e.g. https://tenant.forgeblocks.com |
| clientId           | String        | —       | yes      | OAuth2 client ID |
| clientSecret       | GuardedString | —       | yes      | OAuth2 client secret |
| realm              | String        | alpha   | no       | AIC realm |
| connectTimeoutMs   | int           | 10000   | no       | |
| readTimeoutMs      | int           | 30000   | no       | |
| timestampAttribute | String        | null    | no       | CREST attribute name used for delta detection, e.g. frIndexedString20 |
| timestampValue     | String        | null    | no       | ISO timestamp value; used as ge filter when timestampAttribute is set |
| userBaseFilter     | String        | null    | no       | Raw CREST _queryFilter applied to all __ACCOUNT__ searches |
| orgBaseFilter      | String        | null    | no       | Raw CREST _queryFilter applied to all GROUP searches |
| roleBaseFilter     | String        | null    | no       | Raw CREST _queryFilter applied to all ROLE searches |

---

## Query filter composition

All three components are optional. Only non-null, non-blank components are included.
Final filter = baseFilter AND timestampFilter AND translatedFilter

Rules:
- baseFilter: per-object-class config property (userBaseFilter / orgBaseFilter / roleBaseFilter)
- timestampFilter: `timestampAttribute ge "timestampValue"` — only included when BOTH
  timestampAttribute and timestampValue are non-null and non-blank. If either is absent, skip entirely.
- translatedFilter: output of CrestFilterTranslator. Null if ICF filter was EqualsFilter
  on __UID__ or __NAME__ (those are handled as GET /{id}, not passed to buildQueryFilter).
- If all three are null: omit _queryFilter param entirely (returns all objects).
- Composition: each present component is wrapped in parens and joined with " and ".

This logic lives in AbstractHandler.buildQueryFilter(String translatedFilter, String baseFilter).
BaseFilter is passed in by the concrete handler (UserHandler passes cfg.getUserBaseFilter(), etc.)

---

## Package and class structure

```
org.forgerock.openicf.connectors.aic
├── PingAICConnector.java          # main @ConnectorClass, thin dispatcher only
└── PingAICConfiguration.java      # extends AbstractConfiguration

org.forgerock.openicf.connectors.aic.schema
├── UserSchemaHandler.java         # returns ObjectClassInfo for __ACCOUNT__
├── OrganizationSchemaHandler.java # returns ObjectClassInfo for GROUP
└── RoleSchemaHandler.java         # returns ObjectClassInfo for ROLE

org.forgerock.openicf.connectors.aic.handler
├── CrudqHandler.java              # interface — method signatures mirror OpenICF Connector interface
├── AbstractHandler.java           # abstract class implementing CrudqHandler
│                                  # owns: CrestHttpClient ref, base resource URL,
│                                  #       connectorObjectFromJson(), buildQueryFilter(),
│                                  #       attributesToJson(), attributesToPatchOps(),
│                                  #       shared private methods
├── UserHandler.java               # extends AbstractHandler
├── OrganizationHandler.java       # extends AbstractHandler
└── RoleHandler.java               # extends AbstractHandler

org.forgerock.openicf.connectors.aic.util
├── CrestFilterTranslator.java     # shared, translates ICF Filter → _queryFilter string
└── CrestHttpClient.java           # HTTP client + OAuth2 token management
```

### CrudqHandler interface
Method signatures take the same parameters as the OpenICF Connector interface:
- `Uid create(ObjectClass, Set<Attribute>, OperationOptions)`
- `ConnectorObject getObject(ObjectClass, Uid, OperationOptions)`
- `Uid update(ObjectClass, Uid, Set<Attribute>, OperationOptions)`
- `void delete(ObjectClass, Uid, OperationOptions)`
- `void executeQuery(ObjectClass, Filter, ResultsHandler, OperationOptions)`

### AbstractHandler owns
- `protected final CrestHttpClient http`
- `protected final String resourceUrl`  (constructed from cfg.getRealm() by PingAICConnector.init())
- `protected final PingAICConfiguration cfg`
- `protected ConnectorObject connectorObjectFromJson(JsonNode, ObjectClass, String nameAttribute)`
- `protected String buildQueryFilter(String translatedFilter, String baseFilter)`
- `protected ObjectNode attributesToJson(Set<Attribute>, String nameAttribute)`
- `protected ArrayNode attributesToPatchOps(Set<Attribute>)`

### PingAICConnector
- Implements: Connector, CreateOp, UpdateOp, DeleteOp, SearchOp<Filter>, SchemaOp, TestOp, SyncOp
- No GetOp
- Instantiates one handler per object class in init() with realm-parameterized resourceUrl
- Dispatches all Connector interface methods to the appropriate handler by ObjectClass
- Implements SyncOp — both methods throw UnsupportedOperationException
- Contains no business logic

## Reference material
- docs/user.json — live schema response from GET /openidm/schema/managed/alpha_user
- docs/organization.json — live schema response from GET /openidm/schema/managed/alpha_organization
- docs/role.json — live schema response from GET /openidm/schema/managed/alpha_role

## Coding rules
- Present a plan (files to create/modify and why) before writing any code.
  Wait for explicit approval before proceeding.
- No speculative code. Only implement what was asked.
- No unsolicited refactoring of existing code.
- No comments that restate what the code obviously does.
- No "I've successfully..." or "Here's what I did..." summaries after tasks.
- Source file path as first-line comment in every generated file.
- No features beyond what was asked.
- No token refresh logic until explicitly requested.

## Build
mvn clean package
mvn test
mvn validate