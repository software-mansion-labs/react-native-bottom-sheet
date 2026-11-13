import {
  ATOM,
  LAYOUT,
  format,
  geometry,
  layoutLettering,
  type Lettering,
} from './logo';

const TICK = 0.35;
const FONT_SIZE = 0.8;

/**
 * Dimension lines for the preview: what each distance in the logo is, in stroke
 * widths. Returns SVG markup in the logo's coordinate system.
 */
export function buildDimensions(
  ink: string,
  halo: string,
  lettering: Lettering | null
) {
  const g = geometry();
  const { rx, ry, stroke } = ATOM;
  const half = stroke / 2;
  const [back, middle, front] = g.sheets;
  const { cross, outer, bottom, radius } = g;
  // Canvas edge; rows above and below the artwork sit outside it.
  const edge = LAYOUT.size / 2;
  const value = (v: number) => format(v, 3);

  let lines = '';
  let labels = '';
  const label = (x: number, y: number, text: string, anchor = 'middle') => {
    labels += `<text x="${format(x)}" y="${format(y)}" text-anchor="${anchor}">${text}</text>`;
  };
  // `side` puts the label above (-1) or below (1) a horizontal dimension, and
  // left (-1) or right (1) of a vertical one.
  const horizontal = (
    x1: number,
    x2: number,
    y: number,
    text: string,
    side = -1
  ) => {
    const ticks = [x1, x2].map(
      (x) => `M${format(x)} ${format(y - TICK)}v${2 * TICK}`
    );
    lines += `M${format(x1)} ${format(y)}H${format(x2)}${ticks.join('')}`;
    const offset = side < 0 ? -0.45 : 0.45 + FONT_SIZE * 0.72;
    label((x1 + x2) / 2, y + offset, text);
  };
  const vertical = (
    x: number,
    y1: number,
    y2: number,
    text: string,
    side = 1
  ) => {
    const ticks = [y1, y2].map(
      (y) => `M${format(x - TICK)} ${format(y)}h${2 * TICK}`
    );
    lines += `M${format(x)} ${format(y1)}V${format(y2)}${ticks.join('')}`;
    const anchor = side < 0 ? 'end' : 'start';
    label(x + side * 0.55, (y1 + y2) / 2 + FONT_SIZE * 0.36, text, anchor);
  };

  // Overall size, and the atom inside it.
  horizontal(
    -front.w - half,
    front.w + half,
    -edge - 1.2,
    value(2 * front.w + stroke)
  );
  vertical(-front.w - half - 2.4, -bottom, bottom, value(2 * bottom), -1);
  horizontal(
    -rx - half,
    rx + half,
    edge + 3.6,
    `${value(2 * rx + stroke)} · atom`,
    1
  );
  // Width steps between the sheets.
  horizontal(-front.w, -middle.w, edge + 1.2, value(LAYOUT.step), 1);
  horizontal(-middle.w, -back.w, edge + 1.2, value(LAYOUT.step), 1);
  // Gap between the orbit's end and the middle sheet, and the stroke itself.
  horizontal(rx + half, middle.w - half, 0, value(LAYOUT.gap));
  horizontal(
    front.w - half,
    front.w + half,
    edge + 1.2,
    `${value(stroke)} · stroke`,
    1
  );
  // Vertical rhythm: center, orbit crossing, outer edge.
  const x = front.w + half + 1.6;
  vertical(x, -cross, 0, value(cross));
  vertical(x, -outer, -cross, value(outer - cross));
  vertical(x, 0, cross, value(cross));
  vertical(x, cross, outer, value(outer - cross));
  // Clearance between the horizontal orbit and the meeting edges.
  const clearance = value(cross - ry - stroke);
  vertical(-5, -cross + half, -ry - half, clearance, -1);
  vertical(-5, ry + half, cross - half, clearance, -1);
  // Corner radius, on the back sheet.
  const cx = back.w - radius;
  const cy = back.top + radius;
  const k = radius * Math.SQRT1_2;
  lines += `M${format(cx)} ${format(cy)}L${format(cx + k)} ${format(cy - k)}`;
  label(cx - 0.3, cy + FONT_SIZE * 0.36, `r ${value(radius)}`, 'end');
  // Lettering: clear space next to the mark, and the two cap heights.
  if (lettering) {
    const text = layoutLettering(lettering);
    const tx = text.right + 1.2;
    horizontal(edge, text.left, -edge - 1.2, value(text.left - edge));
    horizontal(
      text.left,
      text.right,
      edge + 1.2,
      value(text.right - text.left),
      1
    );
    const spans = [
      [text.top.capTop, text.top.baseline],
      [text.top.baseline, text.bottom.capTop],
      [text.bottom.capTop, text.bottom.baseline],
    ];
    for (const [y1, y2] of spans) vertical(tx, y1, y2, value(y2 - y1));
  }

  const fixed = 'vector-effect="non-scaling-stroke"';
  // Where the tilted orbits cross, and the corner's center.
  const dots =
    [-cross, cross]
      .map((y) => `<circle cy="${format(y)}" r="0.45" ${fixed}/>`)
      .join('') +
    `<circle cx="${format(cx)}" cy="${format(cy)}" r="0.12" ${fixed}/>`;

  return (
    `<g fill="none" stroke="${ink}" stroke-width="1"><path d="${lines}" ${fixed}/>${dots}</g>` +
    `<g font-family="ui-sans-serif, system-ui, sans-serif" font-size="${FONT_SIZE}" font-weight="600" fill="${ink}" stroke="${halo}" stroke-width="0.3" stroke-linejoin="round" paint-order="stroke">${labels}</g>`
  );
}
