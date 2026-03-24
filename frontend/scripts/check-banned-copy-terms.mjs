import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const frontendRoot = process.cwd()
const repoRoot = path.resolve(frontendRoot, '..')

const bannedPatterns = [
  { term: '炫技', regex: /炫技/g },
  { term: 'PLACEHOLDER', regex: /\bPLACEHOLDER\b/g },
  { term: 'Quantum', regex: /\bQuantum\b/gi },
  { term: 'Operator', regex: /\bOperator\b/gi },
]

const sourceExtensions = new Set(['.vue', '.ts'])

async function collectSourceFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true })
  const files = await Promise.all(
    entries.map(async (entry) => {
      const fullPath = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        return collectSourceFiles(fullPath)
      }
      if (sourceExtensions.has(path.extname(entry.name))) {
        return [fullPath]
      }
      return []
    }),
  )
  return files.flat()
}

function locateLineAndColumn(content, index) {
  const prefix = content.slice(0, index)
  const line = prefix.split('\n').length
  const lastLineStart = prefix.lastIndexOf('\n')
  const column = index - lastLineStart
  return { line, column }
}

function toRelativePath(filePath) {
  return path.relative(repoRoot, filePath).replaceAll('\\', '/')
}

async function scanFile(filePath) {
  const content = await readFile(filePath, 'utf8')
  const violations = []
  for (const pattern of bannedPatterns) {
    pattern.regex.lastIndex = 0
    let match = pattern.regex.exec(content)
    while (match) {
      const { line, column } = locateLineAndColumn(content, match.index)
      violations.push({
        file: toRelativePath(filePath),
        line,
        column,
        term: pattern.term,
      })
      match = pattern.regex.exec(content)
    }
  }
  return violations
}

async function main() {
  const sourceFiles = await collectSourceFiles(path.join(frontendRoot, 'src'))
  const filesToCheck = [...sourceFiles, path.join(repoRoot, 'README.md')]

  const allViolations = []
  for (const filePath of filesToCheck) {
    const violations = await scanFile(filePath)
    allViolations.push(...violations)
  }

  if (allViolations.length === 0) {
    console.log('[copy-guard] 禁用词检查通过')
    return
  }

  console.error('[copy-guard] 检测到禁用词:')
  for (const item of allViolations) {
    console.error(`- ${item.file}:${item.line}:${item.column} -> ${item.term}`)
  }
  process.exit(1)
}

void main()
