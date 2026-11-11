import type { PrismTheme } from 'prism-react-renderer';

type Palette = {
  plain: string;
  keyword: string;
  entity: string;
  string: string;
  literal: string;
  muted: string;
  property: string;
  inserted: string;
  deleted: string;
};

const theme = (p: Palette): PrismTheme => ({
  plain: { color: p.plain, backgroundColor: 'var(--bs-subtle)' },
  styles: [
    {
      types: ['comment', 'prolog', 'doctype', 'cdata'],
      style: { color: p.muted, fontStyle: 'italic' },
    },
    { types: ['punctuation', 'operator'], style: { color: p.muted } },
    {
      types: ['keyword', 'control', 'atrule', 'important', 'selector'],
      style: { color: p.keyword },
    },
    { types: ['attr-name'], style: { color: p.keyword } },
    {
      types: ['function', 'tag', 'class-name', 'maybe-class-name', 'builtin'],
      style: { color: p.entity },
    },
    {
      types: [
        'string',
        'char',
        'attr-value',
        'template-string',
        'regex',
        'url',
      ],
      style: { color: p.string },
    },
    {
      types: ['number', 'boolean', 'constant', 'symbol', 'unit'],
      style: { color: p.literal },
    },
    { types: ['property', 'property-access'], style: { color: p.property } },
    { types: ['variable', 'parameter'], style: { color: p.plain } },
    // JavaScript inside JSX braces is nested in the tag token; reset it so
    // `{index}` reads as code, not as part of the tag name.
    { types: ['script'], style: { color: p.plain } },
    { types: ['inserted'], style: { color: p.inserted } },
    { types: ['deleted'], style: { color: p.deleted } },
    { types: ['bold'], style: { fontWeight: 'bold' } },
    { types: ['italic'], style: { fontStyle: 'italic' } },
  ],
});

export const swmLight = theme({
  plain: 'var(--bs-text)',
  keyword: '#782aeb', // purple-light-100
  entity: 'var(--bs-accent)',
  string: '#31775d', // green-dark-120
  literal: '#914f55', // red-dark-120
  muted: 'var(--bs-muted)',
  property: 'var(--bs-heading)',
  inserted: '#31775d',
  deleted: '#914f55',
});

export const swmDark = theme({
  plain: 'var(--bs-text)',
  keyword: '#c49ffe', // purple-dark-80
  entity: 'var(--bs-accent)',
  string: '#7adead', // green-dark-80
  literal: '#ff8b88', // red-dark-80
  muted: 'var(--bs-muted)',
  property: 'var(--bs-heading)',
  inserted: '#7adead',
  deleted: '#ff8b88',
});
