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
  handler.handle() once with the result. Return new SearchResult(). Do not execute
  a paginated query.
- Otherwise → translate filter via CrestFilterTranslator, compose with
  buildQueryFilter(), execute a single paginated _queryFilter search per call.
- If buildQueryFilter() returns null → use _queryFilter=true (return all).
- IDM controls pagination. executeQuery issues ONE HTTP request per invocation.
  Do not loop internally. IDM calls executeQuery again with the next cookie.
- Read paging inputs from OperationOptions:
  - options.getPageSize() — if non-null, add _pageSize to the request
  - options.getPagedResultsCookie() — if non-null, add _pagedResultsCookie
  - options.getPagedResultsOffset() — if non-null, add _pagedResultsOffset
  - Cookie takes precedence over offset; include only one per request.
- CREST response fields (no underscore prefix): "pagedResultsCookie" and
  "remainingPagedResults". Read these from the response JsonNode.
- Return new SearchResult(cookie, remaining):
  - cookie: null if "pagedResultsCookie" node is missing or isNull()
  - remaining: -1 if "remainingPagedResults" node is missing

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
IDM drives paging. The connector issues one HTTP request per executeQuery() call.
OperationOptions provides pageSize, pagedResultsCookie, and pagedResultsOffset.
CREST response fields are "pagedResultsCookie" and "remainingPagedResults" (no
underscore prefix — do not read "_pagedResultsCookie").
Return new SearchResult(cookie, remaining) — remaining defaults to -1 if absent.

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

## Bundle structure and MANIFEST.MF

The connector JAR is an OSGi bundle built by maven-bundle-plugin. The required
layout inside the JAR is:

```
lib/
  httpclient-4.5.14.jar
  httpcore-4.4.16.jar
  jackson-databind-2.16.2.jar
  jackson-core-2.16.2.jar
  jackson-annotations-2.16.2.jar
META-INF/
  MANIFEST.MF
org/...  (connector classes)
```

Dependency JARs MUST be under lib/. The Bundle-ClassPath MUST reference them
with the lib/ prefix. The correct manifest entries are:

```
Bundle-ClassPath: .,lib/httpclient-4.5.14.jar,lib/httpcore-4.4.16.jar,
 lib/jackson-databind-2.16.2.jar,lib/jackson-core-2.16.2.jar,
 lib/jackson-annotations-2.16.2.jar
ConnectorBundle-FrameworkVersion: 1.5
ConnectorBundle-Name: org.forgerock.openicf.connectors.ping-aic-connector
ConnectorBundle-Version: 1.5.20.32
```

The maven-bundle-plugin instructions that produce this:

```xml
<instructions>
  <Bundle-SymbolicName>${project.artifactId}</Bundle-SymbolicName>
  <Bundle-Version>${project.version}</Bundle-Version>
  <ConnectorBundle-FrameworkVersion>1.5</ConnectorBundle-FrameworkVersion>
  <ConnectorBundle-Name>${project.artifactId}</ConnectorBundle-Name>
  <ConnectorBundle-Version>${project.version}</ConnectorBundle-Version>
  <Embed-Dependency>httpclient,httpcore,jackson-databind,jackson-core,jackson-annotations,commons-logging,commons-codec</Embed-Dependency>
  <Embed-Transitive>false</Embed-Transitive>
  <Embed-Directory>lib</Embed-Directory>
  <Export-Package>org.forgerock.openicf.connectors.aic.*</Export-Package>
  <Import-Package>
    org.identityconnectors.*;version="1.5",
    *
  </Import-Package>
</instructions>
```

To verify the bundle is correct after `mvn clean package`:

```bash
# Confirm lib/ layout
unzip -l target/ping-aic-connector-1.5.20.32.jar | grep "lib/"

# Confirm Bundle-ClassPath uses lib/ prefix
unzip -p target/ping-aic-connector-1.5.20.32.jar META-INF/MANIFEST.MF | grep Bundle-ClassPath

# Confirm framework version
unzip -p target/ping-aic-connector-1.5.20.32.jar META-INF/MANIFEST.MF | grep ConnectorBundle-FrameworkVersion
```

Expected output:
- `lib/httpclient-4.5.14.jar` (and other deps) in the JAR listing
- `Bundle-ClassPath: .,lib/httpclient-4.5.14.jar,...`
- `ConnectorBundle-FrameworkVersion: 1.5`

Do NOT modify the manifest by hand. It is generated by maven-bundle-plugin.
If the Bundle-ClassPath shows JARs at the root (without lib/ prefix), the
cause is a missing `<Embed-Directory>lib</Embed-Directory>` in pom.xml.