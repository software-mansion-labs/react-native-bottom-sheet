// The library's logo, as code: three bottom sheets stacked over the React atom.
//
// It is built around the React logo at its intrinsic size, where the stroke is
// exactly 1 unit wide and the atom's center is the origin. Everything else is
// derived from the atom. The files in `static/img` are exports of this module,
// made from the dev-only preview page (`/logo-preview`).

/** The reference React SVG, unchanged. */
export const ATOM = { rx: 11, ry: 4.2, nucleus: 2.05, stroke: 1 };

export const LAYOUT = {
  /** Square canvas, edge to edge with the front sheet (2 × 16 + stroke). */
  size: 33,
  /** Clear space between the horizontal orbit's ends and the middle sheet's sides. */
  gap: 1,
  /**
   * How much wider each sheet is than the one behind it, per side. The back
   * sheet's outer edge then lines up with the inner edge of the horizontal
   * orbit's ends. Also the corner radius, so each sheet's side lines up with
   * where the next sheet's corner begins.
   */
  step: 3,
  /** Letter spacing of the lettering's top line, in em. */
  tracking: 0.1,
};

// ---- Colors -----------------------------------------------------------------
// All three strokes are the brand ultramarine moved along OKLCH lightness only:
// same hue, and the same chroma wherever sRGB can show it (otherwise the highest
// chroma it can). Each stroke is placed by its lightness distance from the page:
// the middle sheet at `middle`, the front one `step` further from the page, and
// the back one `step` closer.
//
// In light mode the middle sheet is ultramarine itself. The same distances on
// the dark page look far weaker (equal lightness distance is not equal contrast
// between dark colors), so dark mode sits 0.1 further from the page, which puts
// its middle sheet at the lightness of the site's dark accent, and uses a
// smaller step so the front sheet keeps some color instead of reaching white.

const ULTRAMARINE = '#4242f0';
const PAGE = { light: '#ffffff', dark: '#141122' };

type Oklch = { L: number; C: number; h: number };

const toLinear = (c: number) =>
  c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
const toGamma = (c: number) =>
  c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055;
const hexToRgb = (hex: string) =>
  [1, 3, 5].map((i) => toLinear(parseInt(hex.slice(i, i + 2), 16) / 255));

function rgbToOklch([r, g, b]: number[]): Oklch {
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
  return {
    L: 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    C: Math.hypot(A, B),
    h: Math.atan2(B, A),
  };
}

function oklchToRgb({ L, C, h }: Oklch) {
  const A = C * Math.cos(h);
  const B = C * Math.sin(h);
  const l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3;
  const m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3;
  const s = (L - 0.0894841775 * A - 1.291485548 * B) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
}

/** Reduces chroma (only) until the color fits in sRGB. */
function oklchToHex(color: Oklch) {
  const fits = (C: number) =>
    oklchToRgb({ ...color, C }).every((v) => v >= -1e-4 && v <= 1 + 1e-4);
  let C = color.C;
  if (!fits(C)) {
    let low = 0;
    for (let i = 0; i < 30; i++) {
      const middle = (low + C) / 2;
      if (fits(middle)) low = middle;
      else C = middle;
    }
    C = low;
  }
  const channels = oklchToRgb({ ...color, C }).map((v) =>
    Math.round(Math.min(1, Math.max(0, toGamma(v))) * 255)
      .toString(16)
      .padStart(2, '0')
  );
  return `#${channels.join('')}`;
}

const ultramarine = rgbToOklch(hexToRgb(ULTRAMARINE));

/** Lightness distances from the page, back to front. */
const distances = (middle: number, step: number) => [
  middle - step,
  middle,
  middle + step,
];
const DISTANCES = {
  light: distances(1 - ultramarine.L, 0.2),
  dark: distances(1 - ultramarine.L + 0.1, 0.15),
};

/** Stroke colors, back to front. */
function strokes(theme: 'light' | 'dark') {
  const page = rgbToOklch(hexToRgb(PAGE[theme])).L;
  // Light strokes are darker than the page, dark ones lighter.
  const direction = theme === 'dark' ? 1 : -1;
  return DISTANCES[theme].map((distance) =>
    oklchToHex({ ...ultramarine, L: page + direction * distance })
  );
}

// Monochrome: one ink, with each sheet's opacity proportional to its lightness
// distance from the page in the light version (front = 1), so it reads like the
// color logo desaturated.
const opacities = DISTANCES.light.map(
  (distance) => +(distance / DISTANCES.light[2]).toFixed(3)
);

export type VariantName = 'light' | 'dark' | 'black' | 'white';

export interface Variant {
  /** File name suffix: `logo<suffix>.svg`. */
  suffix: string;
  /** Stroke colors, back to front. */
  sheets: string[];
  /** Per-sheet opacity, for the monochrome variants. */
  opacity?: number[];
  /** Lettering color: the site's heading color in each theme. */
  text: string;
  /** The page the variant is meant for. Not part of the logo, which is transparent. */
  page: string;
}

export const VARIANTS: Record<VariantName, Variant> = {
  light: {
    suffix: '',
    sheets: strokes('light'),
    text: '#141122',
    page: PAGE.light,
  },
  dark: {
    suffix: '-dark',
    sheets: strokes('dark'),
    text: '#fafafa',
    page: PAGE.dark,
  },
  black: {
    suffix: '-black',
    sheets: Array(3).fill('#000000'),
    opacity: opacities,
    text: '#000000',
    page: PAGE.light,
  },
  white: {
    suffix: '-white',
    sheets: Array(3).fill('#ffffff'),
    opacity: opacities,
    text: '#ffffff',
    page: PAGE.dark,
  },
};

// ---- Geometry ---------------------------------------------------------------

/** Formats a coordinate for SVG output. */
export const format = (value: number, digits = 4) =>
  String(+value.toFixed(digits));

interface Sheet {
  /** Half-width, to the stroke's centerline. */
  w: number;
  /** Top edge, at the stroke's centerline. */
  top: number;
}

export function geometry() {
  const { rx, ry, stroke } = ATOM;
  // Height at which the two tilted orbits cross each other (centerline): ~7.006.
  const cross = 1 / Math.sqrt(0.75 / rx ** 2 + 0.25 / ry ** 2);
  // The middle and front sheets' top edges run through the crossings; the outer
  // edges sit as far beyond those as they are from the center.
  const outer = 2 * cross;
  const middle = rx + stroke + LAYOUT.gap;
  const sheets: Sheet[] = [
    { w: middle - LAYOUT.step, top: -outer },
    { w: middle, top: -cross },
    { w: middle + LAYOUT.step, top: cross },
  ];
  return {
    sheets,
    cross,
    outer,
    // Open bottom ends; they mirror the outer face of the back sheet's top stroke.
    bottom: outer + stroke / 2,
    radius: LAYOUT.step,
  };
}

type Geometry = ReturnType<typeof geometry>;

function sheetPath(sheet: Sheet, g: Geometry, close: boolean, extend = 0) {
  const r = g.radius;
  const arc = `A${r} ${r} 0 0 1 `;
  const bottom = format(g.bottom + extend);
  const { w, top } = sheet;
  return (
    `M${format(-w)} ${bottom}V${format(top + r)}` +
    `${arc}${format(-w + r)} ${format(top)}H${format(w - r)}` +
    `${arc}${format(w)} ${format(top + r)}V${bottom}${close ? 'Z' : ''}`
  );
}

// ---- Lettering --------------------------------------------------------------
// "REACT NATIVE" (Aeonik Regular, tracked caps) over "Bottom Sheet" (Aeonik Bold),
// to the right of the mark, as outlines. See `lettering.ts` for where the
// outlines come from.

type Command = [op: string, ...coordinates: number[]];

/** A shaped line of text, in font units (y up). */
export interface LetteringLine {
  capHeight: number;
  unitsPerEm: number;
  glyphs: { x: number; commands: Command[] }[];
}

export interface Lettering {
  top: LetteringLine;
  bottom: LetteringLine;
}

interface LineBox {
  capTop: number;
  baseline: number;
}

export function layoutLettering(lettering: Lettering) {
  const g = geometry();
  // Top line: caps from the top of the artwork down to the top of the horizontal
  // orbit. Both are the visible (outer) edges of the strokes, not their
  // centerlines.
  const top: LineBox = {
    capTop: -g.bottom,
    baseline: -ATOM.ry - ATOM.stroke / 2,
  };
  // Bottom line: caps from the atom's center down to the bottom of the artwork,
  // half the mark's height.
  const bottom: LineBox = { capTop: 0, baseline: g.bottom };
  // Clear space next to the mark: the top line's cap height.
  const left = LAYOUT.size / 2 + (top.baseline - top.capTop);

  const place = (line: LetteringLine, box: LineBox, tracking = 0) => {
    const scale = (box.baseline - box.capTop) / line.capHeight;
    let min = Infinity;
    let max = -Infinity;
    const commands = line.glyphs.flatMap((glyph, i) =>
      glyph.commands.map(([op, ...coordinates]): Command => [
        op,
        ...coordinates.map((c, k) => {
          if (k % 2) return box.baseline - c * scale;
          const x = (glyph.x + c) * scale + i * tracking;
          min = Math.min(min, x);
          max = Math.max(max, x);
          return x;
        }),
      ])
    );
    const shift = left - min;
    const d = () =>
      commands
        .map(
          ([op, ...coordinates]) =>
            op +
            coordinates
              .map((c, k) => format(k % 2 ? c : c + shift, 3))
              .join(' ')
        )
        .join('');
    return { width: max - min, d };
  };

  const bottomLine = place(lettering.bottom, bottom);
  const em =
    ((top.baseline - top.capTop) / lettering.top.capHeight) *
    lettering.top.unitsPerEm;
  const topLine = place(lettering.top, top, LAYOUT.tracking * em);
  return {
    left,
    // Both lines start at the same left edge; the lockup ends with the wider one.
    right: left + Math.max(bottomLine.width, topLine.width),
    top,
    bottom,
    d: topLine.d() + bottomLine.d(),
  };
}

// ---- SVG --------------------------------------------------------------------

export interface SvgOptions {
  /** Output height; the width follows from whether the lettering is included. */
  height?: number;
  /** Prefix for the ids of the clip paths and masks. */
  id?: string;
  /** Include the lettering to the right of the mark. */
  lettering?: Lettering | null;
}

/**
 * No fills: the logo is transparent. Each sheet hides what is behind it by
 * masking the sheets behind with its own shape, rather than by painting over
 * them. In color, the mask stops at the stroke's centerline and the opaque
 * stroke covers the rest, so there is no seam. In monochrome the strokes are
 * translucent, so the mask includes the full stroke.
 */
export function buildSvg(
  variant: VariantName,
  { height = LAYOUT.size, id = 'bs-logo', lettering = null }: SvgOptions = {}
) {
  const g = geometry();
  const { sheets: colors, opacity, text: textColor } = VARIANTS[variant];
  const { size } = LAYOUT;
  const origin = -size / 2;
  const stroke = ATOM.stroke;
  const text = lettering ? layoutLettering(lettering) : null;
  const width = text ? text.right - origin : size;

  const box = `x="${origin}" y="${origin}" width="${size}" height="${size}"`;
  const closed = g.sheets.map((sheet) => sheetPath(sheet, g, true));
  // Mask shapes run past the open bottom ends so no sliver of a hidden stroke
  // end survives there.
  const cover = g.sheets.map((sheet) => sheetPath(sheet, g, true, 1));
  const clips = closed.map(
    (d, i) => `<clipPath id="${id}-${i + 1}"><path d="${d}"/></clipPath>`
  );
  const masks = closed.slice(1).map(
    (_, i) =>
      `<mask id="${id}-behind-${i + 2}" maskUnits="userSpaceOnUse" ${box}${
        opacity ? ` stroke="#000" stroke-width="${stroke}"` : ''
      }><rect ${box} fill="#fff" stroke="none"/>` +
      cover
        .slice(i + 1)
        .map((d) => `<path d="${d}"/>`)
        .join('') +
      '</mask>'
  );
  const orbits = [0, 60, 120].map(
    (angle) =>
      `<ellipse rx="${ATOM.rx}" ry="${ATOM.ry}"${
        angle ? ` transform="rotate(${angle})"` : ''
      }/>`
  );
  const body = g.sheets.map(
    (sheet, i) =>
      `<g fill="none" stroke="${colors[i]}" stroke-width="${stroke}"${
        opacity && opacity[i] < 1 ? ` opacity="${opacity[i]}"` : ''
      }${i < closed.length - 1 ? ` mask="url(#${id}-behind-${i + 2})"` : ''}>` +
      `<path d="${sheetPath(sheet, g, false)}"/>` +
      `<g clip-path="url(#${id}-${i + 1})">${orbits.join('')}` +
      `<circle r="${ATOM.nucleus}" fill="${colors[i]}" stroke="none"/></g></g>`
  );

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${origin} ${origin} ${format(width)} ${size}" width="${format((height * width) / size, 2)}" height="${height}" role="img" aria-label="React Native Bottom Sheet">
<defs>${clips.join('')}${masks.join('')}</defs>
${body.join('\n')}${text ? `\n<path fill="${textColor}" d="${text.d}"/>` : ''}
</svg>
`;
}

export const fileName = (
  variant: VariantName,
  withText: boolean,
  extension: string
) =>
  `logo${withText ? '-with-text' : ''}${VARIANTS[variant].suffix}.${extension}`;
