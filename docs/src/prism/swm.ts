import type { PrismTheme } from 'prism-react-renderer';

// Syntax themes built from the Software Mansion palette in custom.css:
// navy ink, purple keywords and props, blue functions and components,
// green strings, rust literals, muted comments and punctuation.
// The background comes from custom.css, so it follows the page tokens.

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

const theme = (p: Palette, background: string): PrismTheme => ({
  plain: { color: p.plain, backgroundColor: background },
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

export const swmLight = theme(
  {
    plain: '#001a72', // navy-light-100
    keyword: '#782aeb', // purple-light-100
    entity: '#126893', // blue-dark-120
    string: '#31775d', // green-dark-120
    literal: '#914f55', // red-dark-120
    muted: '#6676aa', // navy-light-60
    property: '#33488e', // navy-light-80
    inserted: '#31775d',
    deleted: '#914f55',
  },
  '#f8f9ff' // off-white
);

export const swmDark = theme(
  {
    plain: '#c1c6e5', // navy-dark-20
    keyword: '#c49ffe', // purple-dark-80
    entity: '#6fcef5', // blue-dark-80
    string: '#7adead', // green-dark-80
    literal: '#ff8b88', // red-dark-80
    muted: '#7485bd', // navy-dark-60
    property: '#abbcf5', // navy-dark-40
    inserted: '#7adead',
    deleted: '#ff8b88',
  },
  '#232736' // navy
);
