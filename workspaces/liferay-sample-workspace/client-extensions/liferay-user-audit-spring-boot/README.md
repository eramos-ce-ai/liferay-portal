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
