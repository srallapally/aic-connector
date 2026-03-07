# ping-aic-connector

OpenICF Java connector for PingOne Advanced Identity Cloud (AIC). Provisions and governs identity objects via the IDM CREST API.

## Overview

| Property | Value |
|---|---|
| Framework | OpenICF Java Framework 1.5.20.32 |
| Java | 11+ |
| Packaging | OSGi bundle — embedded deps under `lib/` |
| Artifact | `ping-aic-connector-1.5.20.32.jar` |

## Supported Object Classes

| ICF Object Class | AIC Resource | Status |
|---|---|---|
| `__ACCOUNT__` | `managed/{realm}_user` | Complete |
| `ROLE` | `managed/{realm}_role` | Complete |
| `GROUP` | `managed/{realm}_organization` | Planned |

## Supported Operations

| Operation | `__ACCOUNT__` | `ROLE` | `GROUP` |
|---|---|---|---|
| Create | Yes | Yes | Planned |
| Update | Yes | Yes | Planned |
| Delete | Yes | Yes | Planned |
| Search | Yes | Yes | Planned |
| LiveSync | No | No | No |

- **Update** uses CREST PATCH (`[{"operation":"replace","field":"/attr","value":...}]`), never PUT.
- **LiveSync** (`SyncOp`) is not supported. Use reconciliation with `timestampAttribute` / `timestampValue` for incremental sync (see [Delta Reconciliation](#delta-reconciliation)).

## Relationship Attributes

Relationship attributes are discovered dynamically from the AIC schema at connector init time. No relationship field names are hardcoded. Each relationship attribute stores a list of target object UUIDs (`_refResourceId` values).

Relationships are only fetched when explicitly requested via `attributesToGet` in `OperationOptions` (`NOT_RETURNED_BY_DEFAULT`).

Grant/revoke uses replace semantics: IDM passes the full desired list; the connector diffs against current state and issues individual PATCH calls for each addition and removal.

| Object Class | Relationship | Target Collection | Updateable |
|---|---|---|---|
| `__ACCOUNT__` | `roles` | `managed/{realm}_role` | Yes |
| `__ACCOUNT__` | `memberOfOrg` | `managed/{realm}_organization` | Yes |
| `__ACCOUNT__` | `adminOfOrg` | `managed/{realm}_organization` | Yes |
| `__ACCOUNT__` | `ownerOfOrg` | `managed/{realm}_organization` | Yes |
| `ROLE` | `members` | `managed/{realm}_user` | Yes |
| `ROLE` | `assignments` | `managed/{realm}_assignment` | No (read-only) |

Additional relationships defined in the AIC schema are discovered automatically.

**Read:**

```
GET /openidm/managed/{realm}_user/{id}/{fieldName}?_queryFilter=true&_fields=_refResourceId,...
```

**Update (diff-based):**

1. Fetches current relationship entries for the attribute
2. Computes adds (desired - current) and removes (current - desired)
3. Issues one PATCH per add (`operation: "add"`, `field: "/{fieldName}/-"`) and one per remove (`operation: "remove"`, `field: "/{fieldName}"`)

Passing a null or empty value removes all current memberships.

## Authentication

OAuth2 client credentials grant. Token endpoint:

```
{baseUrl}/am/oauth2/{realm}/access_token
```

The connector fetches a token on initialisation and reuses it for the lifetime of the connector instance.

## Configuration Properties

| Property | Type | Default | Required | Description |
|---|---|---|---|---|
| `baseUrl` | String | — | Yes | Tenant base URL, e.g. `https://tenant.forgeblocks.com` |
| `clientId` | String | — | Yes | OAuth2 client ID |
| `clientSecret` | GuardedString | — | Yes | OAuth2 client secret |
| `realm` | String | `alpha` | No | AIC realm |
| `connectTimeoutMs` | int | `10000` | No | HTTP connect timeout (ms) |
| `readTimeoutMs` | int | `30000` | No | HTTP read timeout (ms) |
| `timestampAttribute` | String | — | No | CREST attribute for delta detection (e.g. `frIndexedString20`) |
| `timestampValue` | String | — | No | ISO-8601 timestamp; combined with `timestampAttribute` as a `ge` filter |
| `userBaseFilter` | String | — | No | Raw `_queryFilter` applied to all `__ACCOUNT__` searches |
| `orgBaseFilter` | String | — | No | Raw `_queryFilter` applied to all `GROUP` searches |
| `roleBaseFilter` | String | — | No | Raw `_queryFilter` applied to all `ROLE` searches |

## Query Filter Support

The connector translates ICF filters to CREST `_queryFilter` expressions.

| ICF Filter | CREST Expression | Notes |
|---|---|---|
| `EqualsFilter` on `__UID__` / `__NAME__` | `GET /{id}` | Direct lookup, no `_queryFilter` |
| `EqualsFilter` on attribute | `attr eq "value"` | |
| `StartsWithFilter` | `attr sw "value"` | |
| `ContainsFilter` | `attr co "value"` | |
| `AndFilter` | `(left and right)` | |
| `OrFilter` | `(left or right)` | |
| `NotFilter` | `!(expr)` | |
| Others | — | Throws `UnsupportedOperationException` |

### Filter Composition

All three optional components are combined when present:

```
(baseFilter) and timestampAttr ge "timestampValue" and (translatedFilter)
```

If none are present, no `_queryFilter` parameter is sent (returns all objects).

## Paging

The connector follows the IDM-driven paging contract:

- IDM passes the desired page size and cursor via `OperationOptions`.
- The connector performs **one HTTP request per `executeQuery` call**.
- The connector returns a `SearchResult` containing `pagedResultsCookie` and `remainingPagedResults` from the CREST response.
- IDM decides whether to request the next page.

## Delta Reconciliation

To run incremental reconciliation instead of a full scan, configure `timestampAttribute` and `timestampValue`:

```json
{
  "timestampAttribute": "frIndexedString20",
  "timestampValue": "2024-01-15T08:00:00Z"
}
```

The connector appends `frIndexedString20 ge "2024-01-15T08:00:00Z"` to every search filter, returning only objects modified on or after the given timestamp. Update `timestampValue` between runs to advance the watermark.

## Build

```bash
mvn clean package
```

Output: `target/ping-aic-connector-1.5.20.32.jar`

Compile-scope dependencies (Apache HttpClient, Jackson) are embedded under `lib/` inside the bundle. The OpenICF framework and SLF4J are provided by the runtime.

## Bundle Verification

```bash
unzip -l target/ping-aic-connector-1.5.20.32.jar | grep "lib/"
unzip -p target/ping-aic-connector-1.5.20.32.jar META-INF/MANIFEST.MF | grep Bundle-ClassPath
unzip -p target/ping-aic-connector-1.5.20.32.jar META-INF/MANIFEST.MF | grep ConnectorBundle-FrameworkVersion
```

Expected:
- `lib/httpclient-4.5.14.jar` (and other deps) under `lib/`
- `Bundle-ClassPath: .,lib/httpclient-4.5.14.jar,...`
- `ConnectorBundle-FrameworkVersion: 1.5`

## Deployment

Copy the JAR to the RCS connectors directory. Restart RCS or wait for connector hot-reload.
