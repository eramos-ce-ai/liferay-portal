# Liferay User Audit Spring Boot Client Extension

A **Spring Boot 4** client extension that runs as a Liferay **Object Action**
on update of a custom object. Whenever an entry is updated it logs an
easy-to-read audit report identifying:

- the user who **executed the update** (the user that triggered the action), and
- the user who **originally created** the entry.

Both user records are retrieved from the Liferay **Headless Admin User API**
(`GET /o/headless-admin-user/v1.0/user-accounts/{id}`) using the client
extension's own headless-server OAuth token, so the lookup does not depend on
the permissions of whoever triggered the update.

## Architecture

Liferay's shared `client-extension-util-spring-boot3` helper targets Spring
Boot 3.4 / Spring Security 6, which is not binary compatible with Spring Boot
4 / Spring Security 7. This extension therefore ships its own small,
Boot-4-native security and OAuth plumbing instead of depending on that util:

- `config/LiferaySecurityConfiguration` - Spring Security 7 resource server
  that validates the inbound Liferay JWT (JWK set from `/o/oauth2/jwks`,
  `client_id` claim check).
- `config/LiferayOAuth2ClientConfiguration` + `oauth/*` - client-credentials
  token acquisition and caching for outbound Headless API calls.
- `service/HeadlessAdminUserClient` - reads user accounts from the Headless
  Admin User API.
- `web/ObjectActionUserAuditController` - the `/object/action/user-audit`
  endpoint invoked by the Object Action.

## OAuth (defined in `client-extension.yaml`)

| External reference code | Type | Purpose |
| --- | --- | --- |
| `liferay-user-audit-spring-boot-oaua` | `oAuthApplicationUserAgent` | Authenticates the inbound Object Action request. |
| `liferay-user-audit-spring-boot-oahs` | `oAuthApplicationHeadlessServer` | Grants the extension its own permission (`Liferay.Headless.Admin.User.everything`) to read the user system object. |

## Wiring the Object Action in Liferay

1. Deploy this client extension to your Liferay DXP / Portal.
2. Open your custom object (Object definition) in the admin UI.
3. Add an **Action** with trigger **Standalone / On After Update**
   (`onAfterUpdate`).
4. Set the action type to **Webhook** and select the
   `Liferay User Audit Spring Boot Object Action` client extension (resource
   path `/object/action/user-audit`).
5. Update an entry of the object and check the client extension logs for the
   formatted audit report.

## Sample log output

```
==========================================================================
 Object Action Audit  |  Trigger: onAfterUpdate  |  Entry ID: 43210
==========================================================================
 Executed By (updated the entry)
   User ID      : 20125
   Name         : Jane Updater
   Screen Name  : jane
   Email        : jane@example.com
--------------------------------------------------------------------------
 Original Creator (created the entry)
   User ID      : 20124
   Name         : John Creator
   Screen Name  : john
   Email        : john@example.com
==========================================================================
```

## JWT issuer validation

The resource server validates the inbound Liferay JWT with a composite
validator that checks the token timestamp, the token `client_id` claim (it
must match one of the configured OAuth2 user-agent application client IDs),
and the token issuer.

Issuer validation is **enforced by default**, so production rejects any token
whose `iss` claim does not match the expected issuer. The expected issuer
defaults to `${com.liferay.lxc.dxp.server.protocol}://${com.liferay.lxc.dxp.mainDomain}`
(derived from the LXC environment). If your portal issues a different value,
override it with:

```properties
liferay.oauth.jwt.issuer=https://my-portal.example.com
```

For local development against an instance whose issuer differs from the
expected value, you may disable issuer validation (the timestamp and
`client_id` checks still apply):

```properties
liferay.oauth.jwt.issuer.validation.enabled=false
```

## Building and testing

**IMPORTANT toolchain constraint:** Spring Boot 4 requires **Gradle 8.14+**,
which is **not** compatible with this `liferay-sample-workspace`'s Gradle
wrapper (8.5) or Liferay's `com.liferay.gradle.plugins.defaults` plugin (which
has therefore been removed from this module's `build.gradle`). This module is
built and tested **standalone** with an external Gradle 8.14+ (not the
workspace wrapper), run from the module directory:

```bash
gradle test
gradle bootJar
```

The JUnit 5 suite covers the audit-payload processing (`UserAuditProcessor`),
the `client_id` validator (`ClientIdOAuth2TokenValidator`), the issuer
validator (the composite built by `LiferaySecurityConfiguration`), and the
controller wiring (`ObjectActionUserAuditController`).

The companion batch client extension `liferay-user-audit-batch` provisions a
sample custom object (`UserAuditDemo`) with an attached `onAfterUpdate` object
action, so the whole flow can be exercised end to end.
