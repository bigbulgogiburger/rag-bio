export const EMAIL_STYLES = {
  wrapper: 'font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #202124;',
  heading2: 'font-size: 20px; font-weight: bold; margin: 16px 0 8px 0; color: #202124;',
  heading3: 'font-size: 16px; font-weight: bold; margin: 12px 0 6px 0; color: #202124;',
  paragraph: 'margin: 0 0 12px 0; line-height: 1.6;',
  image: 'max-width: 100%; height: auto; display: block; margin: 8px 0;',
  link: 'color: #1a73e8; text-decoration: underline;',
  citation: 'color: #1a73e8; text-decoration: underline; font-size: 12px;',
  blockquote: 'border-left: 3px solid #dadce0; padding-left: 12px; margin: 8px 0; color: #5f6368;',
  bulletList: 'margin: 0 0 12px 0; padding-left: 24px;',
  orderedList: 'margin: 0 0 12px 0; padding-left: 24px;',
  listItem: 'margin: 0 0 4px 0;',
  table: 'border-collapse: collapse; width: 100%;',
  tableCell: 'border: 1px solid #dadce0; padding: 8px;',
  tableHeaderCell: 'border: 1px solid #dadce0; padding: 8px; background-color: #f8f9fa; font-weight: bold;',
  bold: 'font-weight: bold;',
  italic: 'font-style: italic;',
} as const;

export type EmailStyleKey = keyof typeof EMAIL_STYLES;
