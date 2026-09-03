# Operations Portal Architecture

```mermaid
flowchart TB
    App["app<br/>providers, router, global styles"]
    Pages["pages<br/>dashboard, login,<br/>pre-authorization list/detail/create"]
    Widgets["widgets<br/>application shell and navigation"]
    Features["features<br/>authentication, submit,<br/>review pre-authorization"]
    Entities["entities<br/>pre-authorization API,<br/>types and status UI"]
    Shared["shared<br/>HTTP client, environment,<br/>async states and common UI"]

    App --> Pages
    App --> Widgets
    Pages --> Widgets
    Pages --> Features
    Pages --> Entities
    Pages --> Shared
    Widgets --> Features
    Widgets --> Shared
    Features --> Entities
    Features --> Shared
    Entities --> Shared
```

Imports may only point downward. `src/app/architecture.test.ts` scans source
imports and rejects reverse dependencies. TanStack Query owns server state;
authentication uses a narrow React context; API data is not copied into a
global client store. React Hook Form and Zod validate submission input, while
route and feature components apply role-aware UI behavior.
