import { describe, expect, it } from 'vitest'
import { javaUuid } from './validation'

describe('javaUuid', () => {
  it('accepts canonical identifiers supported by java.util.UUID without RFC version bits', () => {
    expect(javaUuid().safeParse('20000000-0000-0000-0000-000000000001').success).toBe(true)
  })

  it('rejects malformed identifiers', () => {
    expect(javaUuid().safeParse('not-a-uuid').success).toBe(false)
  })
})
