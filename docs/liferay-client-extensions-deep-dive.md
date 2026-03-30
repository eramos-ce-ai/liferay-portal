# Liferay Client Extensions: Deep Dive

> Analysis of `modules/apps/client-extension/` and `modules/apps/portal/portal-catapult-*`

---

## Table of Contents

1. [What Are Client Extensions?](#what-are-client-extensions)
2. [Framework Architecture](#framework-architecture)
3. [The 13 Extension Types](#the-13-extension-types)
   - [Custom Element](#1-custom-element-customelement)
   - [IFrame](#2-iframe-iframe)
   - [Global JS](#3-global-js-globaljs)
   - [Global CSS](#4-global-css-globalcss)
   - [Theme CSS](#5-theme-css-themecss)
   - [Theme Favicon](#6-theme-favicon-themefavicon)
   - [Theme Spritemap](#7-theme-spritemap-themespritemap)
   - [Editor Config Contributor](#8-editor-config-contributor-editorconfigcontributor)
   - [FDS Cell Renderer](#9-fds-cell-renderer-fdscellrenderer)
   - [FDS Filter](#10-fds-filter-fdsfilter)
   - [JS Import Maps Entry](#11-js-import-maps-entry-jsimportmapsentry)
   - [Static Content](#12-static-content-staticcontent)
   - [Commerce Checkout Step](#13-commerce-checkout-step-commercecheckoutstep)
4. [Deployment Pipeline](#deployment-pipeline)
5. [PortalCatapult: Calling External Services](#portalcatapult-calling-external-services)
6. [OAuth2 Integration & Identity](#oauth2-integration--identity)
7. [Permissions & Security Model](#permissions--security-model)
8. [Execution Identity: Which User Runs What](#execution-identity-which-user-runs-what)
9. [Cluster & Multi-Tenant Behavior](#cluster--multi-tenant-behavior)
10. [Key File Reference](#key-file-reference)

---

## What Are Client Extensions?

Client Extensions (CX) are Liferay's mechanism for extending the platform from **outside the OSGi container** — without writing Java bundles or modifying core. They range from frontend concerns (injecting JS/CSS, registering Web Components) to backend integration (calling external microservices from Object Actions). Every extension is represented internally as a **CET** (Client Extension Type) instance stored in the database and deployed dynamically into the OSGi service registry.

The system has 13 types defined in `ClientExtensionEntryConstants`:

| Constant | Type Key | Category |
|---|---|---|
| `TYPE_CUSTOM_ELEMENT` | `customElement` | Frontend / UI |
| `TYPE_IFRAME` | `iframe` | Frontend / UI |
| `TYPE_GLOBAL_JS` | `globalJS` | Frontend / UI |
| `TYPE_GLOBAL_CSS` | `globalCSS` | Frontend / UI |
| `TYPE_THEME_CSS` | `themeCSS` | Frontend / Theming |
| `TYPE_THEME_FAVICON` | `themeFavicon` | Frontend / Theming |
| `TYPE_THEME_SPRITEMAP` | `themeSpritemap` | Frontend / Theming |
| `TYPE_EDITOR_CONFIG_CONTRIBUTOR` | `editorConfigContributor` | Frontend / Editor |
| `TYPE_FDS_CELL_RENDERER` | `fdsCellRenderer` | Frontend / Data |
| `TYPE_FDS_FILTER` | `fdsFilter` | Frontend / Data |
| `TYPE_JS_IMPORT_MAPS_ENTRY` | `jsImportMapsEntry` | Frontend / Modules |
| `TYPE_STATIC_CONTENT` | `staticContent` | Assets |
| `TYPE_COMMERCE_CHECKOUT_STEP` | `commerceCheckoutStep` | Commerce / Backend |

Source: `client-extension-api/.../constants/ClientExtensionEntryConstants.java`

---

## Framework Architecture

### Core Abstractions

```
CET (interface)
 └─ [Type]CET (interface per type, e.g. CustomElementCET)
     └─ [Type]CETImpl (concrete implementation)

CETImplFactory<T>          — creates and validates CET instances
CETFactory                 — routes to the right CETImplFactory by type
CETDeployer                — routes to type-specific OSGi deployment logic
CETManager                 — in-memory cache keyed by (companyId, ERC)
ClientExtensionEntry       — JPA entity persisting CET state in the DB
ClientExtensionEntryLocalService  — internal CRUD (no permission checks)
ClientExtensionEntryService       — public API (permission-checked)
```

### Module Layout

```
modules/apps/client-extension/
├── client-extension-api/          # Model, service interfaces, constants
├── client-extension-service/      # DB persistence, local & public services
├── client-extension-type-api/     # CET interfaces, CETFactory, CETDeployer interfaces
├── client-extension-type-impl/    # CETFactoryImpl, per-type factories & impls, CETManagerImpl
└── client-extension-web/          # OSGi deployer, portlet classes, admin UI

modules/apps/portal/
├── portal-catapult-api/           # PortalCatapult interface
└── portal-catapult-impl/          # OAuth2-authenticated HTTP launcher
```

### Lifecycle

```
1. Admin creates CET via UI or REST API
        │
        ▼
2. ClientExtensionEntryServiceImpl.addClientExtensionEntry()
   — permission check (ADD_ENTRY)
   — type-specific validation via CETImplFactory.validate()
   — persists ClientExtensionEntry to DB
        │
        ▼
3. ClientExtensionEntryLocalServiceImpl.deployClientExtensionEntry()
   @Clusterable — runs on every cluster node
        │
        ▼
4. CETFactoryImpl.create()
   — resolves baseURL, replaces ${modifiedTime} variables
   — builds typed CETImpl instance
        │
        ▼
5. CETDeployerImpl.deploy(cet)
   — registers OSGi services (Portlet, EditorConfigContributor, etc.)
   — returns List<ServiceRegistration<?>> stored in CETManagerImpl
        │
        ▼
6. Extension is live — OSGi runtime picks up registered services
```

---

## The 13 Extension Types

### 1. Custom Element (`customElement`)

**Use case:** Render a Web Component (Custom Element) as a Liferay portlet. This is the primary way to ship a self-contained React, Vue, Angular, or vanilla JS widget and drop it onto any page via the portlet palette.

**How integration works:**

The CET is registered as an OSGi `Portlet` service. When the portlet renders, `CustomElementCETPortlet.render()` outputs a bare custom element tag with portlet preferences and context as HTML attributes:

```java
// CustomElementCETPortlet.java
printWriter.print("<" + cet.getHTMLElementName());

// Injects webdav URL as an attribute automatically
properties.put("liferaywebdavurl",
    themeDisplay.getPortalURL() + "/webdav" +
    group.getFriendlyURL() + "/document_library");

for (Map.Entry<Object, Object> entry : properties.entrySet()) {
    printWriter.print(" " + entry.getKey() + "=\"" +
        StringUtil.replace((String)entry.getValue(), CharPool.QUOTE, "&quot;") +
        "\"");
}
printWriter.print("></" + cet.getHTMLElementName() + ">");
```

JavaScript and CSS files are injected into the page `<head>` via portlet dictionary properties. ESM modules are prefixed with `module:` so Liferay's resource combiner treats them correctly; all URLs get a `?t=<lastModified>` cache-busting parameter unless feature flag `LPS-202104` is on.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `htmlElementName` | Yes | Must start with a lowercase letter and contain a dash (Web Components spec) |
| `urls` | Yes | Newline-separated JS file URLs |
| `cssURLs` | No | Newline-separated CSS file URLs |
| `useESM` | No (default `false`) | Adds `module:` prefix; scripts load as ES modules |
| `instanceable` | No (default `false`) | Allow multiple portlet instances per page |
| `portletCategoryName` | No | Display category in the Add Widget panel |
| `friendlyURLMapping` | No | Friendly URL route for standalone portlet pages |
| `panelCategoryKey` | No | Adds portlet to a control panel category |

**Execution identity:** The portlet runs in the context of the requesting HTTP user (same as any portlet). No privileged identity is assumed.

**Portlet ID format:**
```
com_liferay_client_extension_web_internal_portlet_
ClientExtensionEntryPortlet_<companyId>_<normalizedERC>
```

Source: `client-extension-web/.../portlet/CustomElementCETPortlet.java`

---

### 2. IFrame (`iframe`)

**Use case:** Embed an external web page inside a Liferay portlet using an HTML `<iframe>`. Useful for integrating legacy or third-party UIs without any JavaScript coupling.

**How integration works:**

Deployed as an OSGi `Portlet`. The portlet renders an `<iframe src="...">` pointing to the configured URL. The URL can be absolute or relative to `baseURL`.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `url` | Yes | The iframe `src` URL |
| `friendlyURLMapping` | No | Standalone page mapping |
| `instanceable` | No | Allow multiple instances |
| `portletCategoryName` | No | Widget palette category |

**Execution identity:** The iframe content runs in the browser under the external site's origin — completely isolated from Liferay's security context. No token is passed automatically.

Source: `client-extension-web/.../portlet/IFrameCETPortlet.java`

---

### 3. Global JS (`globalJS`)

**Use case:** Inject a JavaScript file into every portal page (or a scoped subset). Use this for analytics scripts, global utilities, chat widgets, or any JS that must run across the entire site.

**How integration works:**

Registered as a dynamic include contributor (`TopHeadDynamicInclude` or `BottomBodyDynamicInclude`). On every page render, Liferay's tag library evaluates registered contributors and emits `<script>` tags into the `<head>` or bottom of `<body>`.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `url` | Yes | URL to the JS file |
| `scriptLocation` | No (default `head`) | `head` or `body` |
| `scope` | No (default `layout`) | Controls which pages inject the script |
| `scriptElementAttributesJSON` | No | JSON object of extra `<script>` tag attributes (e.g. `{"defer": true}`) |

**Execution identity:** Runs in the browser as the visiting user. No Liferay security context is injected.

---

### 4. Global CSS (`globalCSS`)

**Use case:** Inject a stylesheet into every portal page. Use for global design overrides, brand tokens, or site-wide styling that isn't tied to a theme.

**How integration works:** Same dynamic include mechanism as Global JS but emits `<link rel="stylesheet">` tags.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `url` | Yes | URL to the CSS file |
| `scope` | No (default `layout`) | Scope of application |

---

### 5. Theme CSS (`themeCSS`)

**Use case:** Replace or extend a theme's CSS entirely. Allows shipping a full design system as a client extension — including Clay Design System overrides, RTL variants, and CSS custom property (token) definitions — without deploying a traditional Liferay theme module.

**How integration works:**

Registered as a `ThemeCSSCET` OSGi service. The theme rendering pipeline checks for an applied `ThemeCSSCET` and loads its stylesheets instead of (or layered over) the base theme's CSS.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `mainURL` | Yes | Main CSS file |
| `mainRTLURL` | No | RTL variant |
| `clayURL` | No | Clay Design System CSS override |
| `clayRTLURL` | No | Clay RTL override |
| `frontendTokenDefinitionJSON` | No | JSON defining CSS custom properties exposed in the Style Book editor |

---

### 6. Theme Favicon (`themeFavicon`)

**Use case:** Override the favicon for a site without touching theme files.

**How integration works:** Registered as a service; the theme rendering pipeline resolves the configured favicon URL and emits `<link rel="icon">` tags pointing to it.

**Key properties:** `url` (required) — path to the favicon file.

---

### 7. Theme Spritemap (`themeSpritemap`)

**Use case:** Replace Liferay's SVG icon spritemap with a custom one. All Clay/Lexicon icons reference the spritemap, so this allows full icon customization.

**How integration works:** Registered as a service; the theme layer substitutes the configured spritemap URL wherever `clay:icon` or `liferay-ui:icon` tags render SVG `<use>` references.

**Key properties:** `url` (required) — path to the SVG spritemap file.

---

### 8. Editor Config Contributor (`editorConfigContributor`)

**Use case:** Extend or override the configuration of a rich-text editor (CKEditor 4, CKEditor 5) deployed in specific portlets. Examples: adding custom toolbar buttons, registering plugins, restricting allowed HTML tags.

**How integration works:**

Registered as an OSGi `EditorConfigContributor` service. When an editor initializes, Liferay collects all matching contributors (filtered by `editorNames` and `portletNames`) and calls them to mutate the editor config JSON before it is passed to the editor.

The client extension's `url` points to a JavaScript transformer script that receives the config object and returns a modified version.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `url` | Yes | Transformer script URL |
| `editorNames` | Yes | List: `ckeditor5`, `ckeditor4`, etc. |
| `portletNames` | Yes | List of portlet names to apply to |
| `editorConfigKeys` | Yes | Config keys to pass to the transformer |

**Execution identity:** Config transformation runs server-side in the Java `EditorConfigContributor` OSGi service — the script URL is fetched at config-build time, not at request time.

---

### 9. FDS Cell Renderer (`fdsCellRenderer`)

**Use case:** Provide a custom rendering component for a column in Liferay's **Frontend Data Set** (FDS) — the standard table/list component used across the admin UI and commerce. Examples: rendering a status badge, a clickable thumbnail, or a formatted currency value.

**How integration works:**

The `url` points to a JavaScript module that exports a Web Component or render function. FDS loads the renderer at runtime and uses it to paint cells in the configured column.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `url` | Yes | JS module exporting the cell renderer |

**Execution identity:** Runs in the browser as the visiting user.

---

### 10. FDS Filter (`fdsFilter`)

**Use case:** Provide a custom filter UI component for a FDS table. Examples: a date-range picker, a multi-select taxonomy filter, or a geographic proximity filter.

**How integration works:**

Same registration pattern as FDS Cell Renderer. The `url` points to a module exporting a filter component; FDS mounts it in the filter bar and passes filter values into the data query.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `url` | Yes | JS module exporting the filter component |

**Execution identity:** Runs in the browser as the visiting user.

---

### 11. JS Import Maps Entry (`jsImportMapsEntry`)

**Use case:** Register a bare module specifier mapping in the page's [Import Map](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/script/type/importmap). This allows client-side code to `import 'lodash'` and have it resolved to a CDN URL or a locally hosted file — without bundling it.

**How integration works:**

Registered as an OSGi `JSImportMapsContributor` service. On every page render, Liferay collects all contributors and emits a `<script type="importmap">` block. Each `JSImportMapsEntryCET` contributes one `{ "bareSpecifier": "url" }` entry.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `bareSpecifier` | Yes | The import specifier (e.g. `react`, `lodash`) |
| `url` | Yes | The resolved module URL |

**Example output:**
```html
<script type="importmap">
{
  "imports": {
    "react": "https://cdn.example.com/react.esm.js",
    "lodash": "/o/frontend-js-lodash-web/lodash.js"
  }
}
</script>
```

---

### 12. Static Content (`staticContent`)

**Use case:** Serve static files (images, fonts, JS, CSS, JSON) from a client extension's deployment. Other extension types reference these files via their `url` properties pointing to the base URL.

**How integration works:**

Registers a `baseURL` from which static assets are served. In practice this is often the base URL of an external hosting server or a portal-served static bundle. Does not register any OSGi behavior itself — it is a descriptor that other CETs reference.

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `url` | Yes | Base URL for the static content |

---

### 13. Commerce Checkout Step (`commerceCheckoutStep`)

**Use case:** Insert a custom step into the Commerce checkout flow. Examples: a custom payment gateway UI, a gift-wrapping selection screen, or a loyalty points redemption step.

**How integration works:**

Routed to `CommerceCETDeployer` (a separate OSGi service in the Commerce module). This deployer registers a `CommerceCheckoutStep` OSGi service with the configured order, label, and visibility flags. When a buyer proceeds through checkout, Commerce iterates registered steps in order and renders each one.

The `oAuth2ApplicationExternalReferenceCode` property enables the step to call back to an external service (e.g. a payment processor) via **PortalCatapult** (see [PortalCatapult section](#portalcatapult-calling-external-services)).

**Key properties:**

| Property | Required | Notes |
|---|---|---|
| `checkoutStepName` | Yes | Internal step identifier |
| `checkoutStepLabel` | Yes | Display label shown to buyer |
| `checkoutStepOrder` | Yes | Integer — lower = earlier in flow |
| `oAuth2ApplicationExternalReferenceCode` | No | OAuth2 app for server-side calls from this step |
| `active` | No | Whether step participates in checkout |
| `visible` | No | Whether step is shown in the step indicator |
| `showControls` | No | Whether Next/Back buttons are shown |

**Execution identity:** The checkout step UI runs in the browser as the shopping user. Server-side calls via PortalCatapult use an OAuth2 token acquired for that user (see below).

Source: `client-extension-web/.../type/deployer/CETDeployerImpl.java`

---

## Deployment Pipeline

### Configuration Sources

A CET can originate from three sources:

1. **Database** (`ClientExtensionEntry` entity) — created via the Admin UI or REST API. This is the primary production mechanism.
2. **OSGi Configuration Factory** — `.config` files with PID `com.liferay.client.extension.type.configuration.CETConfiguration`. Used for dev/test deployments shipped inside OSGi bundles.
3. **Programmatic** — calling `ClientExtensionEntryLocalService.addClientExtensionEntry()` directly from Java code.

### URL Resolution and Cache Busting

When a CET is instantiated, `CETFactoryImpl` resolves all URL properties:

```java
// CETFactoryImpl.java
String baseURL = cetConfiguration.baseURL();
baseURL = baseURL.replaceAll(Pattern.quote("${portalURL}"), _portal.getPathContext());

// Variable replacement: ${modifiedTime} → epoch millis of build timestamp
UnicodeProperties typeSettings = _replaceVariables(
    cetImplFactory, modifiedDate, typeSettingsUnicodeProperties);
```

Relative URLs are prefixed with `baseURL`. The `${modifiedTime}` variable (from `buildTimestamp` in the config) is substituted for cache-busting purposes. At portlet render time, a `?t=<lastModified>` query parameter is also appended unless feature flag `LPS-202104` is active.

### OSGi Service Registration

`CETDeployerImpl.deploy(CET)` switches on type and registers the appropriate OSGi services:

```java
// CETDeployerImpl.java
public List<ServiceRegistration<?>> deploy(CET cet) {
    if (TYPE_CUSTOM_ELEMENT.equals(cet.getType()))   return _deploy((CustomElementCET) cet);
    if (TYPE_IFRAME.equals(cet.getType()))            return _deploy((IFrameCET) cet);
    if (TYPE_EDITOR_CONFIG_CONTRIBUTOR...)            return _deploy((EditorConfigContributorCET) cet);
    if (TYPE_JS_IMPORT_MAPS_ENTRY...)                 return _deploy((JSImportMapsEntryCET) cet);
    if (TYPE_THEME_CSS...)                            return _deploy((ThemeCSSCET) cet);
    if (TYPE_COMMERCE_CHECKOUT_STEP...)               return _commerceCETDeployer.deploy(cet);
    // Global JS/CSS, Theme Favicon/Spritemap, FDS, Static Content:
    // no OSGi registration needed — consumed via in-memory CETManager
    return Collections.emptyList();
}
```

For `customElement` and `iframe`, deployment registers up to four services:
- `ConfigurationAction` — portlet preferences UI
- `FriendlyURLMapper` — if `friendlyURLMapping` is set
- `PanelApp` — if `panelCategoryKey` is set
- `Portlet` — always registered

The returned `List<ServiceRegistration<?>>` is stored in `CETManagerImpl` and unregistered when the CET is updated or deleted.

### Cluster Synchronization

Both deploy and undeploy operations are annotated `@Clusterable`:

```java
// ClientExtensionEntryLocalServiceImpl.java
@Clusterable
public void deployClientExtensionEntry(ClientExtensionEntry entry) { ... }

@Clusterable
public void undeployClientExtensionEntry(ClientExtensionEntry entry) { ... }
```

This ensures that when an admin creates or modifies a CET on one node, all cluster nodes re-deploy it automatically via Liferay's cluster invocation mechanism.

---

## PortalCatapult: Calling External Services

PortalCatapult is the mechanism for making **OAuth2-authenticated HTTP calls from Liferay to external services**. It is the bridge between server-side Liferay logic (Object Actions, Commerce steps) and externally hosted client extension backends.

### Interface

```java
// portal-catapult-api/.../PortalCatapult.java
public interface PortalCatapult {
    Future<byte[]> launch(
        long companyId,
        Http.Method method,
        String oAuth2ApplicationExternalReferenceCode,
        JSONObject payloadJSONObject,
        String resourcePath,
        long userId) throws PortalException;
}
```

### Full Execution Flow

```java
// PortalCatapultImpl.java
public Future<byte[]> launch(...) throws PortalException {

    // 1. Build HTTP request
    Http.Options options = new Http.Options();
    options.addHeader(HttpHeaders.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
    if (payloadJSONObject != null) {
        options.setBody(payloadJSONObject.toString(), APPLICATION_JSON, UTF8);
    }

    // 2. Fetch the OAuth2 application by ERC + companyId (tenant-scoped)
    OAuth2Application oAuth2App =
        _oAuth2ApplicationLocalService
            .getOAuth2ApplicationByExternalReferenceCode(oAuth2AppERC, companyId);

    // 3. Resolve target URL
    //    - If resourcePath contains "://" → use as-is (absolute URL)
    //    - Otherwise → homePageURL + "/" + resourcePath
    options.setLocation(_getLocation(oAuth2App, resourcePath));
    options.setMethod(method);

    // 4. Acquire OAuth2 access token in a NEW transaction
    TransactionInvokerUtil.invoke(
        TransactionConfig.Factory.create(Propagation.REQUIRES_NEW, Exception.class),
        () -> {
            _localOAuthClient.consumeAccessToken(
                accessToken -> options.addHeader("Authorization", "Bearer " + accessToken),
                oAuth2App,
                userId);   // ← token is scoped to this user
            return null;
        });

    // 5. Execute asynchronously on a named executor pool
    ExecutorService executor =
        _portalExecutorManager.getPortalExecutor(PortalCatapultImpl.class.getName());

    return executor.submit(() -> {
        try {
            return _http.URLtoByteArray(options);
        } catch (IOException e) {
            _log.error(e);
            return ReflectionUtil.throwException(e);
        }
    });
}
```

### URL Resolution

```java
private String _getLocation(OAuth2Application app, String resourcePath) {
    if (resourcePath.contains(Http.PROTOCOL_DELIMITER)) {
        return resourcePath; // absolute — use directly
    }
    String base = app.getHomePageURL();
    if (base.endsWith("/"))       base = base.substring(0, base.length() - 1);
    if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);
    return base + "/" + resourcePath;
}
```

### Important Behavioral Notes

- **Asynchronous:** Returns `Future<byte[]>`. The caller is responsible for blocking on it if the result is needed synchronously.
- **No retry:** An `IOException` is logged and re-thrown. There is no backoff or retry at this layer.
- **No timeout configuration** at the PortalCatapult level — timeout is governed by the underlying `Http` implementation.
- **Token transaction isolation:** Token acquisition runs in `Propagation.REQUIRES_NEW` to avoid polluting the outer transaction.
- **Error swallowed on IOException:** `_log.error(e)` then `ReflectionUtil.throwException(e)` — the error is logged but the exception still propagates to the caller.

### Where PortalCatapult Is Used

| Caller | Purpose |
|---|---|
| `FunctionObjectActionExecutorImpl` | Object Action of type "Function" calling an external endpoint |
| Commerce Checkout Step CET | Calling a payment/fulfillment microservice during checkout |

---

## OAuth2 Integration & Identity

### How Tokens Are Acquired

PortalCatapult calls `LocalOAuthClient.consumeAccessToken()` with:
- The `OAuth2Application` (looked up by external reference code + companyId)
- The `userId` of the user triggering the action

The `LocalOAuthClient` generates (or retrieves a cached) OAuth2 access token representing that user's identity within the scope of the specified OAuth2 application. The token is then set as a `Bearer` header on the outbound HTTP request.

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

### What the Token Represents

| Token Attribute | Value |
|---|---|
| Subject (`sub`) | The Liferay `userId` of the triggering user |
| Issuer | Liferay portal (company-specific) |
| Audience | The registered OAuth2 application |
| Scope | Scopes granted to the OAuth2 application |
| Company context | Embedded; enforces tenant isolation |

The external service receiving the call can decode the JWT to determine which Liferay user triggered the action and what permissions that user has.

### OAuth2 Application Requirements

To use PortalCatapult, an `OAuth2Application` must be registered in Liferay with:
- A unique **External Reference Code** (the `oAuth2ApplicationExternalReferenceCode` referenced in configs)
- A **Homepage URL** (used as the base URL for relative resource paths)
- Appropriate **scopes** granted

### Token Transaction Isolation

Token acquisition runs inside `Propagation.REQUIRES_NEW`:

```java
TransactionInvokerUtil.invoke(
    TransactionConfig.Factory.create(Propagation.REQUIRES_NEW, Exception.class),
    () -> {
        _localOAuthClient.consumeAccessToken(...);
        return null;
    });
```

This ensures token persistence side effects (if any) don't participate in — or roll back — the outer transaction.

---

## Permissions & Security Model

### Service-Layer Permission Checks

All public mutations go through `ClientExtensionEntryServiceImpl`, which enforces permissions before delegating to the local service:

```java
// ClientExtensionEntryServiceImpl.java

// CREATE — portlet-level permission
_portletResourcePermission.check(
    getPermissionChecker(), null, ActionKeys.ADD_ENTRY);

// READ — model-level permission
_clientExtensionEntryModelResourcePermission.check(
    getPermissionChecker(), clientExtensionEntryId, ActionKeys.VIEW);

// UPDATE — model-level permission
_clientExtensionEntryModelResourcePermission.check(
    getPermissionChecker(), clientExtensionEntryId, ActionKeys.UPDATE);

// DELETE — model-level permission
_clientExtensionEntryModelResourcePermission.check(
    getPermissionChecker(), clientExtensionEntryId, ActionKeys.DELETE);
```

`ClientExtensionEntryLocalService` has **no permission checks** — it is for internal use only. External callers must always use `ClientExtensionEntryService`.

### Permission Resources

| Resource | Permission | Scope |
|---|---|---|
| `ClientExtensionConstants.RESOURCE_NAME` | `ADD_ENTRY` | Portlet/company level |
| `com.liferay.client.extension.model.ClientExtensionEntry` | `VIEW` | Per-entity |
| `com.liferay.client.extension.model.ClientExtensionEntry` | `UPDATE` | Per-entity |
| `com.liferay.client.extension.model.ClientExtensionEntry` | `DELETE` | Per-entity |

### Type-Specific Validation

Before any CET is persisted, `CETImplFactory.validate()` is called. For `CustomElementCET`:
- `htmlElementName` must match `^[a-z][a-z0-9]*(-[a-z0-9]+)+$` (Web Components spec)
- CSS URLs must be valid URLs
- `friendlyURLMapping` must be a valid URL path segment

Invalid settings throw a typed `PortalException` that surfaces to the caller before any database write occurs.

### Scope Validation for Executors

For `FunctionObjectActionExecutorImpl` (which uses PortalCatapult), scope is validated at execution time by the Object Action Engine before the executor is invoked:

```java
// ObjectActionEngineImpl.java
if (objectActionExecutor instanceof CompanyScoped) {
    if (!((CompanyScoped) executor).isAllowedCompany(companyId)) {
        throw new ObjectActionExecutorKeyException(...);
    }
}
if (objectActionExecutor instanceof ObjectDefinitionScoped) {
    if (!((ObjectDefinitionScoped) executor).isAllowedObjectDefinition(name)) {
        throw new ObjectActionExecutorKeyException(...);
    }
}
```

---

## Execution Identity: Which User Runs What

This is one of the most important questions for security and auditability. Here is the complete picture:

| Extension Type | Where it Executes | Identity |
|---|---|---|
| `customElement` | Browser (client-side JS) | The visiting Liferay user (browser session) |
| `iframe` | Browser (separate origin) | The visiting user's browser session; the iframe's origin is isolated |
| `globalJS` | Browser (client-side JS) | The visiting Liferay user (browser session) |
| `globalCSS` | Browser (CSS) | N/A — no identity concept |
| `themeCSS` / `themeFavicon` / `themeSpritemap` | Browser (CSS/HTML) | N/A |
| `editorConfigContributor` | Server (Java, config build time) | The portal's system context — no user-level identity |
| `fdsCellRenderer` | Browser (client-side JS) | The visiting Liferay user (browser session) |
| `fdsFilter` | Browser (client-side JS) | The visiting Liferay user (browser session) |
| `jsImportMapsEntry` | Browser (import resolution) | N/A — resolved at module load time |
| `staticContent` | N/A (asset serving) | N/A |
| `commerceCheckoutStep` (UI) | Browser (client-side) | The shopping user (browser session) |
| `commerceCheckoutStep` → PortalCatapult | Server → External HTTP | OAuth2 token scoped to the shopping user's `userId` |
| Object Action `function` → PortalCatapult | Server → External HTTP | OAuth2 token scoped to the `userId` that triggered the action |

### Key Principle for Server-Side Calls

When Liferay calls an external service via PortalCatapult, the **calling identity is always the user who triggered the event**, not a service account. The external service receives a signed JWT identifying that user. This means:

1. **Privilege is not escalated.** If user A triggers an Object Action, the external service sees a token for user A.
2. **The external service is responsible** for deciding what user A is allowed to do with that call.
3. **There is no anonymous or system-level call path** through PortalCatapult — a valid `userId` is always required.

### Security Implications

- Frontend extensions (JS/CSS) run in the browser with the user's ambient session cookies. They can call Liferay APIs using the user's existing session — no additional token needed, but also no additional privilege.
- Server-side calls via PortalCatapult carry a short-lived OAuth2 Bearer token. The token's scope is limited to what the OAuth2 application is authorized for.
- Custom Element portlets receive `liferaywebdavurl` as an attribute — this exposes the WebDAV root for the current site to the web component. Be careful with this if the component is untrusted.

---

## Cluster & Multi-Tenant Behavior

### Multi-Tenancy (Virtual Instances)

Every `ClientExtensionEntry` is scoped by `companyId`:

- All service queries are filtered: `findByCompanyId()`, `findByC_T(companyId, type)`, `findByERC_C(erc, companyId)`
- External Reference Codes are unique **within** a company but can be reused across companies
- Portlet IDs embed `companyId` to prevent cross-tenant portlet registration conflicts
- OAuth2 application lookup is scoped to `companyId`, preventing one tenant from using another's OAuth2 app

### In-Memory Cache (`CETManagerImpl`)

`CETManagerImpl` maintains:
```java
Map<Long, Map<String, CET>> _cetsMap;
// companyId → (ERC → CET instance)

Map<Long, Map<String, List<ServiceRegistration<?>>>> _serviceRegistrationsMap;
// companyId → (ERC → registered OSGi services)
```

On deploy: CET added to map, OSGi services registered.
On undeploy: OSGi services unregistered, entry removed from map.

### Cluster Propagation

`@Clusterable` on `deployClientExtensionEntry` / `undeployClientExtensionEntry` means Liferay's `ClusterableInvokerUtil` broadcasts the call to all cluster nodes. Each node independently:
1. Re-instantiates the CET from the database
2. Re-registers the OSGi services in its own bundle context
3. Updates its local `CETManagerImpl` cache

This ensures all nodes have a consistent view of deployed extensions without a shared distributed cache.

---

## Key File Reference

| File | Role |
|---|---|
| `client-extension-api/.../constants/ClientExtensionEntryConstants.java` | 13 type key constants |
| `client-extension-api/.../model/ClientExtensionEntry.java` | JPA entity (generated) |
| `client-extension-service/.../impl/ClientExtensionEntryLocalServiceImpl.java` | CRUD, deploy/undeploy (`@Clusterable`) |
| `client-extension-service/.../impl/ClientExtensionEntryServiceImpl.java` | Permission-checked public API |
| `client-extension-type-api/.../type/CET.java` | Base interface for all CET types |
| `client-extension-type-api/.../type/[Type]CET.java` | Per-type interface (13 files) |
| `client-extension-type-api/.../type/factory/CETFactory.java` | Factory interface |
| `client-extension-type-api/.../type/factory/CETImplFactory.java` | Per-type factory interface |
| `client-extension-type-api/.../type/deployer/CETDeployer.java` | Deployer interface |
| `client-extension-type-api/.../type/configuration/CETConfiguration.java` | OSGi metatype config |
| `client-extension-type-impl/.../factory/CETFactoryImpl.java` | Routes to per-type factories; handles URL resolution |
| `client-extension-type-impl/.../factory/[Type]CETImplFactoryImpl.java` | Per-type creation + validation (13 files) |
| `client-extension-type-impl/.../manager/CETManagerImpl.java` | In-memory cache of live CET instances |
| `client-extension-web/.../type/deployer/CETDeployerImpl.java` | OSGi service registration per type |
| `client-extension-web/.../portlet/BaseCETPortlet.java` | Base portlet class for CX portlets |
| `client-extension-web/.../portlet/CustomElementCETPortlet.java` | Renders `<custom-element>` tags; injects JS/CSS |
| `client-extension-web/.../portlet/IFrameCETPortlet.java` | Renders `<iframe>` portlet |
| `portal-catapult-api/.../PortalCatapult.java` | Interface for external service calls |
| `portal-catapult-impl/.../PortalCatapultImpl.java` | OAuth2 token acquisition + async HTTP dispatch |
| `object-service/.../executor/FunctionObjectActionExecutorImpl.java` | Object Action executor that calls PortalCatapult |
