export function convertPlainTextToHtml(text: string): string {
  if (!text) return '';
  return text
    .split('\n\n')
    .map(paragraph => `<p>${paragraph.replace(/\n/g, '<br>')}</p>`)
    .join('');
}

export function convertCitationsToLinks(
  html: string,
  evidences?: Array<{ documentId: string; fileName: string }>,
): string {
  if (!evidences?.length) return html;
  const citationRegex = /\(([^,\u3131-\uD79D\n]+\.pdf),\s*p\.(\d+)(?:-(\d+))?\)/gi;
  return html.replace(citationRegex, (match, fileName, pageStart, pageEnd) => {
    const evidence = evidences.find(e => e.fileName === fileName);
    if (!evidence) return match;
    const label = pageEnd
      ? `${fileName}, p.${pageStart}-${pageEnd}`
      : `${fileName}, p.${pageStart}`;
    return `<a href="#" data-doc-id="${evidence.documentId}" data-page="${pageStart}" style="color: #1a73e8; text-decoration: underline; font-size: 12px;">[${label}]</a>`;
  });
}
