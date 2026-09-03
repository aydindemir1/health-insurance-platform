import { readdirSync, readFileSync } from 'node:fs'
import { extname, join, relative, sep } from 'node:path'
import { describe, expect, it } from 'vitest'

const sourceRoot = join(process.cwd(), 'src')
const layers = ['app', 'pages', 'widgets', 'features', 'entities', 'shared'] as const

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return sourceFiles(path)
    return ['.ts', '.tsx'].includes(extname(entry.name)) ? [path] : []
  })
}

describe('Feature-Sliced Design dependency direction', () => {
  it('only imports the same layer or a lower layer', () => {
    const violations: string[] = []
    for (const file of sourceFiles(sourceRoot)) {
      const sourceLayer = relative(sourceRoot, file).split(sep)[0]
      const sourceRank = layers.indexOf(sourceLayer as typeof layers[number])
      if (sourceRank < 0) continue
      for (const match of readFileSync(file, 'utf8').matchAll(/from ['"]@\/(app|pages|widgets|features|entities|shared)\//g)) {
        const targetLayer = match[1]
        if (layers.indexOf(targetLayer as typeof layers[number]) < sourceRank) {
          violations.push(`${relative(sourceRoot, file)} imports ${targetLayer}`)
        }
      }
    }
    expect(violations).toEqual([])
  })
})
