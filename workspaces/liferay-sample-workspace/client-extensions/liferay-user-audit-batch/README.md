# Liferay User Audit Batch Client Extension

A **batch** client extension that provisions a sample custom object for
exercising the companion `liferay-user-audit-spring-boot` client extension end
to end.

## What it provisions

On deployment the batch engine upserts a custom object definition
`UserAuditDemo` at **company** scope with the following fields:

| Field | Business type | Required |
| --- | --- | --- |
| `title` | Text (`String`) | Yes |
| `priority` | Integer | No |

It also attaches an **`onAfterUpdate`** object action named
`userAuditOnUpdate` that invokes the `liferay-user-audit-spring-boot` client
extension (resource path `/object/action/user-audit`). Updating a
`UserAuditDemo` entry therefore triggers the Spring Boot extension, which logs
the audit report identifying the updating and creating users.

## OAuth scopes

The batch headless-server OAuth application requires:

- `Liferay.Headless.Batch.Engine.everything` - to run the batch import.
- `Liferay.Object.Admin.REST.everything` - to create the object definition,
  its fields, and its object action.

## Caveat: the object action executor key

The object action's `objectActionExecutorKey`
(`function#liferay-user-audit-spring-boot-object-action-1`) assumes the Spring
Boot client extension is **deployed first**, so the executor key resolves to
its registered object action.

If your Liferay version does not auto-resolve the executor key when the batch
runs, create the action manually in the admin UI instead: open the
`UserAuditDemo` object, go to **Actions**, add an action with trigger **On
After Update**, and select the **Liferay User Audit Spring Boot Object Action**
client extension.
