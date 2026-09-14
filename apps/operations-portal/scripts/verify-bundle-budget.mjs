import { gzipSync } from 'node:zlib'
import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const assetsDirectory = fileURLToPath(new URL('../dist/assets/', import.meta.url))
const maximumJavaScriptGzipBytes = 100 * 1024

const files = (await readdir(assetsDirectory)).filter((file) => file.endsWith('.js'))
const violations = []

for (const file of files) {
  const content = await readFile(join(assetsDirectory, file))
  const gzipBytes = gzipSync(content).byteLength
  if (gzipBytes > maximumJavaScriptGzipBytes) {
    violations.push(`${file}: ${(gzipBytes / 1024).toFixed(1)} KiB gzip`)
  }
}

if (violations.length > 0) {
  throw new Error(`JavaScript bundle budget exceeded (100 KiB gzip per chunk):\n${violations.join('\n')}`)
}

console.log(`Bundle budget passed: ${files.length} JavaScript chunks, each <= 100 KiB gzip.`)
