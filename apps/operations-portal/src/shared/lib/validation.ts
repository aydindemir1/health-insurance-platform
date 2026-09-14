import { z } from 'zod'

/** Matches canonical text accepted by java.util.UUID without requiring RFC version bits. */
export function javaUuid(message?: string) {
  return z.guid(message)
}
