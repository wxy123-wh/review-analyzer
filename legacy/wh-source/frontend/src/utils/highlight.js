function escapeHtml(input) {
  return String(input || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function escapeRegExp(input) {
  return String(input).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function highlightHtml(text, terms) {
  const escapedText = escapeHtml(text)
  const list = Array.from(
    new Set(
      (terms || [])
        .map((t) => (t == null ? '' : String(t).trim()))
        .filter((t) => t.length > 0),
    ),
  ).sort((a, b) => b.length - a.length)

  if (list.length === 0) return escapedText

  const pattern = list.map((t) => escapeRegExp(escapeHtml(t))).join('|')
  if (!pattern) return escapedText

  const re = new RegExp(pattern, 'g')
  return escapedText.replace(re, (m) => `<mark class="kw">${m}</mark>`)
}

