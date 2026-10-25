# Asset price write configuration

All POST, PUT and DELETE operations under `/api/v1/asset-prices` require a current signed access token. Configure these Spring properties for the deployment:

| Property | Required value |
| --- | --- |
| `assets.security.issuer` | Exact trusted token issuer |
| `assets.security.audience` | Intended audience for asset price writes |
| `assets.security.jwk-set-uri` | Trusted issuer signing-key endpoint |

Tokens require the exact `accounts:asset-prices:write` scope and a current finite expiration. Missing trust configuration denies mutations. Public GET operations retain their existing access behavior.

The verified subject is the audit actor. It must be nonblank, contain no control characters or unpaired surrogates, and fit within 50 Unicode code points. The configured issuer defines the stored subject namespace; changing the issuer requires a reviewed identity migration because audit rows store the subject alone.

Create requests must omit `assetPriceId`; a non-null client ID returns HTTP 400. The server assigns the ID and creation audit fields. Submitted `dateCreated` and `dateUpdated` values are ignored. Updates load the path-selected existing row, preserve its creator and creation time, and record the current verified modifier through the typed asset callback and standard date auditing. Every existing bulk-delete route uses the same mutation admission.

This audit identity records asset-price data changes. It does not establish custody, chain settlement, tenant isolation, dynamic policy approval or a durable deletion-history record.

Price requests must be positive and fit `NUMERIC(36,18)`: at most 18 integer and 18 fractional digits, with no silent rounding. The maximum is `999999999999999999.999999999999999999`; the smallest positive increment is `0.000000000000000001`. Asset symbols use 1–20 uppercase letters or digits. Quote instruments use 1–10 uppercase letters or digits, including pairs such as BTC/USDC. Both create and update validate required fields, source length and nonfuture observation time before persistence.

The generated SDK packages the actual producer specification at both `openapi.yml` and the compatibility path `api-spec/openapi.yml`. Maven creates the compatibility resource from the root producer output during `process-resources`; there is no separately maintained source schema at that path.
