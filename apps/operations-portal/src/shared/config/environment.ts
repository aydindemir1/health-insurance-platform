export const environment = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081/api/v1',
  keycloak: {
    url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8080',
    realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'health-insurance',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'health-insurance-web',
  },
} as const
