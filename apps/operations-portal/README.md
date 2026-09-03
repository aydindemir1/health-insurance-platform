# Operations Portal

React and TypeScript frontend for hospital and insurance operations. The first
vertical slice supports Keycloak authentication, role-aware navigation,
pre-authorization creation, lookup, detail review, and specialist decisions.

## Local development

Copy `.env.example` to `.env.local` if the default local URLs do not match your
environment. Never commit `.env.local` or credentials.

```powershell
npm install
npm run dev
```

The development server runs at `http://localhost:5173`. Keycloak is expected at
`http://localhost:8080`, and Authorization Service is expected at
`http://localhost:8081`.

## Quality checks

```powershell
npm run lint
npm test
npm run build
```

## Dependency direction

The source tree follows this import direction:

```text
app -> pages -> widgets -> features -> entities -> shared
```

`src/app/architecture.test.ts` verifies this rule. A lower layer must not import
from a higher layer. Shared code therefore knows nothing about business entities,
while entities remain independent of user workflows and page composition.
