import { readdirSync, readFileSync } from 'node:fs'
import { dirname, extname, join, relative, resolve, sep } from 'node:path'
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
      for (const match of readFileSync(file, 'utf8').matchAll(/(?:from\s+|import\()\s*['"]([^'"]+)['"]/g)) {
        const specifier = match[1]
        if (!specifier) continue
        const targetLayer = specifier.startsWith('@/')
          ? specifier.split('/')[1]
          : specifier.startsWith('.')
            ? relative(sourceRoot, resolve(dirname(file), specifier)).split(sep)[0]
            : undefined
        if (!targetLayer || !layers.includes(targetLayer as typeof layers[number])) continue
        if (layers.indexOf(targetLayer as typeof layers[number]) < sourceRank) {
          violations.push(`${relative(sourceRoot, file)} imports ${targetLayer}`)
        }
      }
    }
    expect(violations).toEqual([])
  })

  it('uses slice public APIs across feature and entity boundaries', () => {
    const violations: string[] = []
    for (const file of sourceFiles(sourceRoot)) {
      const sourceParts = relative(sourceRoot, file).split(sep)
      const sourceSlice = sourceParts.length > 1 ? `${sourceParts[0]}/${sourceParts[1]}` : undefined
      for (const match of readFileSync(file, 'utf8').matchAll(/(?:from\s+|import\()\s*['"]@\/(features|entities)\/([^/'"]+)\/[^'"]+['"]/g)) {
        const targetSlice = `${match[1]}/${match[2]}`
        if (sourceSlice !== targetSlice) violations.push(`${relative(sourceRoot, file)} bypasses ${targetSlice} public API`)
      }
    }
    expect(violations).toEqual([])
  })
})
