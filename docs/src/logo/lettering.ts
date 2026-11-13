import type { Lettering, LetteringLine } from './logo';

// Aeonik is licensed, so it is not in the repository: `bun run fonts:decrypt`
// unpacks it into `static/fonts/aeonik`, which the site serves. The logo's two
// lines are shaped from those files, so the lettering is outlines of the real
// font with its own kerning and ligatures.
const LINES = {
  top: { file: 'Aeonik-Regular.woff2', text: 'REACT NATIVE' },
  bottom: { file: 'Aeonik-Bold.woff2', text: 'Bottom Sheet' },
};

const OPS: Record<string, string> = {
  moveTo: 'M',
  lineTo: 'L',
  quadraticCurveTo: 'Q',
  bezierCurveTo: 'C',
  closePath: 'Z',
};

/** Shapes the logo's lettering from the fonts under `fontsUrl`. */
export async function loadLettering(fontsUrl: string): Promise<Lettering> {
  const { create } = await import('fontkit');

  const shape = async ({ file, text }: (typeof LINES)['top']) => {
    const response = await fetch(`${fontsUrl}/${file}`);
    // The dev server answers unknown paths with the app's HTML.
    const isFont = !response.headers.get('content-type')?.includes('html');
    if (!response.ok || !isFont) {
      throw new Error(
        `${file} is missing. Unpack Aeonik with "bun run fonts:decrypt".`
      );
    }
    const font = create(new Uint8Array(await response.arrayBuffer()));
    const run = font.layout(text);
    let x = 0;
    const line: LetteringLine = {
      capHeight: font.capHeight,
      unitsPerEm: font.unitsPerEm,
      glyphs: run.glyphs.map((glyph, i) => {
        const position = run.positions[i];
        const at = x + position.xOffset;
        x += position.xAdvance;
        return {
          x: at,
          commands: glyph.path.commands.map(({ command, args }) => [
            OPS[command],
            ...args,
          ]),
        };
      }),
    };
    return line;
  };

  const [top, bottom] = await Promise.all([
    shape(LINES.top),
    shape(LINES.bottom),
  ]);
  return { top, bottom };
}
