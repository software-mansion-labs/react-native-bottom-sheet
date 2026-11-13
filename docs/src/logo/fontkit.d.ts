// fontkit ships no types; this covers what `lettering.ts` uses.
declare module 'fontkit' {
  interface Glyph {
    path: { commands: { command: string; args: number[] }[] };
  }

  interface GlyphRun {
    glyphs: Glyph[];
    positions: { xAdvance: number; xOffset: number }[];
  }

  interface Font {
    capHeight: number;
    unitsPerEm: number;
    layout(text: string): GlyphRun;
  }

  export function create(buffer: Uint8Array): Font;
}
