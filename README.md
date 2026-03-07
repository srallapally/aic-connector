# PingOne Advanced Identity Cloud Connector

OpenICF Java connector for provisioning identities against the PingOne Advanced Identity Cloud (AIC) CREST API.

## Overview

| Property | Value |
|---|---|
| Framework | OpenICF Java Framework 1.5.20.32 |
| Java | 11+ |
| Packaging | OSGi bundle — embedded deps under `lib/` |
| Artifact | `ping-aic-connector-1.5.20.32.jar` |

The connector provisions against the managed object CREST API:

| Object class | ICF name | CREST resource |
|---|---|---|
| User | `__ACCOUNT__` | `/openidm/managed/{realm}_user` |
| Organization | `GROUP` | `/openidm/managed/{realm}_organization` *(not yet active)* |
| Role | `ROLE` | `/openidm/managed/{realm}_role` *(not yet active)* |

## Supported Operations

| Object class | Create | Update | Delete | Search |
|---|---|---|---|---|
| `__ACCOUNT__` | ✓ | ✓ | ✓ | ✓ |
| `GROUP` | — | — | — | — |
| `ROLE` | — | — | — | — |

- **Update** uses CREST PATCH (`[{"operation":"replace","field":"/attr","value":...}]`), never PUT.
- **LiveSync** (`SyncOp`) is not supported. Use reconciliation with `timestampAttribute` / `timestampValue` for incremental sync (see [Delta Reconciliation](#delta-reconciliation)).

## Authentication

OAuth2 client credentials grant. The token endpoint is derived from configuration:

```
{baseUrl}/am/oauth2/{realm}/access_token
```

The connector fetches a token on initialisation and reuses it for the lifetime of the connector instance.

## Configuration Reference

| Property | Type | Default | Required | Description |
|---|---|---|---|---|
| `baseUrl` | String | — | yes | Tenant base URL, e.g. `https://tenant.forgeblocks.com` |
| `clientId` | String | — | yes | OAuth2 client ID |
| `clientSecret` | GuardedString | — | yes | OAuth2 client secret |
| `realm` | String | `alpha` | no | AIC realm |
| `connectTimeoutMs` | int | `10000` | no | HTTP connect timeout (ms) |
| `readTimeoutMs` | int | `30000` | no | HTTP read timeout (ms) |
| `timestampAttribute` | String | — | no | CREST attribute used for delta detection (e.g. `frIndexedString20`) |
| `timestampValue` | String | — | no | ISO-8601 timestamp; combined with `timestampAttribute` as a `ge` filter |
| `userBaseFilter` | String | — | no | Raw `_queryFilter` appended to all `__ACCOUNT__` searches |
| `orgBaseFilter` | String | — | no | Raw `_queryFilter` appended to all `GROUP` searches |
| `roleBaseFilter` | String | — | no | Raw `_queryFilter` appended to all `ROLE` searches |

## Query Filter Support

The connector translates ICF filters to CREST `_queryFilter` expressions.

| ICF filter | CREST expression | Notes |
|---|---|---|
| `EqualsFilter` on `__UID__` or `__NAME__` | `GET /{id}` | Resolved via direct lookup, no `_queryFilter` |
| `EqualsFilter` on attribute | `attr eq "value"` | |
| `StartsWithFilter` | `attr sw "value"` | |
| `ContainsFilter` | `attr co "value"` | |
| `AndFilter` | `(left and right)` | |
| `OrFilter` | `(left or right)` | |
| `NotFilter` | `!(expr)` | |
| `GreaterThanFilter` | — | Throws `UnsupportedOperationException` |
| `GreaterThanOrEqualFilter` | — | Throws `UnsupportedOperationException` |
| `LessThanFilter` | — | Throws `UnsupportedOperationException` |
| `LessThanOrEqualFilter` | — | Throws `UnsupportedOperationException` |
| `EndsWithFilter` | — | Throws `UnsupportedOperationException` |
| `ContainsAllValuesFilter` | — | Throws `UnsupportedOperationException` |

### Filter composition

All three optional components are combined when present:

```
(baseFilter) and timestampAttr ge "timestampValue" and (translatedFilter)
```

If none are present, no `_queryFilter` parameter is sent (returns all objects).

## Paging

The connector follows the IDM-driven paging contract:

- IDM passes the desired page size and cursor via `OperationOptions` (`getPageSize()`, `getPagedResultsCookie()`, `getPagedResultsOffset()`).
- The connector performs **one HTTP request per `executeQuery` call**.
- After iterating results, the connector returns a `SearchResult` containing the next `pagedResultsCookie` and `remainingPagedResults` from the CREST response back to IDM.
- IDM decides whether to request the next page by calling `executeQuery` again.

## Delta Reconciliation

To run incremental reconciliation instead of a full scan, configure `timestampAttribute` and `timestampValue`:

```json
{
  "timestampAttribute": "frIndexedString20",
  "timestampValue": "2024-01-15T08:00:00Z"
}
```

The connector appends `frIndexedString20 ge "2024-01-15T08:00:00Z"` to every search filter, returning only objects modified on or after the given timestamp. Update `timestampValue` between reconciliation runs to advance the watermark.

## Build

```bash
mvn clean package
```

The connector bundle is written to `target/ping-aic-connector-1.5.20.32.jar`. Compile-scope dependencies (Apache HttpClient, Jackson) are embedded in the `lib/` directory inside the bundle; the OpenICF framework and SLF4J are provided by the runtime.

## Tests

```bash
mvn test
```

## Bundle Manifest Headers

| Header | Value |
|---|---|
| `Bundle-SymbolicName` | `ping-aic-connector` |
| `ConnectorBundle-FrameworkVersion` | `1.5` |
| `ConnectorBundle-Name` | `ping-aic-connector` |
| `ConnectorBundle-Version` | `1.5.20.32` |
| `Export-Package` | `org.forgerock.openicf.connectors.aic` |
| `Embed-Directory` | `lib` |
