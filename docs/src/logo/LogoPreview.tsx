import Head from '@docusaurus/Head';
import useBaseUrl from '@docusaurus/useBaseUrl';
import { useEffect, useState } from 'react';

import { buildDimensions } from './dimensions';
import { loadLettering } from './lettering';
import {
  LAYOUT,
  VARIANTS,
  buildSvg,
  fileName,
  layoutLettering,
  type Lettering,
  type VariantName,
} from './logo';
import styles from './LogoPreview.module.css';

// A dev-only page (see `plugins/logo-preview.mjs`) for inspecting the logo and
// exporting the files in `static/img`.

const STORAGE_KEY = 'logo-preview-view';
/** Room around the canvas for the dimension lines, in logo units. */
const PADDING = 7;
const PNG_HEIGHTS = [64, 128, 256, 512, 1024];
const DIMENSION_INK = { light: '#d6336c', dark: '#ff9db8' };

interface View {
  variant: VariantName;
  zoom: number;
  text: boolean;
  dimensions: boolean;
}

const DEFAULT_VIEW: View = {
  variant: 'light',
  zoom: 12,
  text: true,
  dimensions: true,
};

function storedView(): View {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
    const view = { ...DEFAULT_VIEW, ...stored };
    return view.variant in VARIANTS ? view : DEFAULT_VIEW;
  } catch {
    return DEFAULT_VIEW;
  }
}

function download(name: string, blob: Blob) {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

const dataUrl = (svg: string) =>
  `data:image/svg+xml,${encodeURIComponent(svg)}`;

const LogoPreview = () => {
  const fontsUrl = useBaseUrl('/fonts/aeonik');
  const [view, setView] = useState(storedView);
  const [lettering, setLettering] = useState<Lettering | null>(null);
  const [letteringError, setLetteringError] = useState('');
  const [pngHeight, setPngHeight] = useState(512);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    loadLettering(fontsUrl).then(setLettering, (error: Error) =>
      setLetteringError(`Text unavailable: ${error.message}`)
    );
  }, [fontsUrl]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(view));
    } catch {
      // Not being able to remember the view is fine.
    }
  }, [view]);

  const update = (change: Partial<View>) => setView({ ...view, ...change });

  const variant = VARIANTS[view.variant];
  const text = view.text ? lettering : null;
  const { size } = LAYOUT;
  const width = text ? layoutLettering(text).right + size / 2 : size;
  const full = size + 2 * PADDING;
  const onDark = variant.page !== VARIANTS.light.page;
  const svg = buildSvg(view.variant, { lettering: text });
  const name = (extension: string) =>
    fileName(view.variant, text !== null, extension);

  const copy = async () => {
    await navigator.clipboard.writeText(svg);
    setCopied(true);
    setTimeout(() => setCopied(false), 1200);
  };

  const downloadPng = () => {
    const image = new Image();
    image.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.height = pngHeight;
      canvas.width = Math.round((pngHeight * width) / size);
      canvas
        .getContext('2d')
        ?.drawImage(image, 0, 0, canvas.width, canvas.height);
      canvas.toBlob((blob) => blob && download(name('png'), blob));
    };
    image.src = dataUrl(
      buildSvg(view.variant, { height: pngHeight, lettering: text })
    );
  };

  return (
    <div className={styles.page}>
      <Head>
        <title>Logo Preview</title>
      </Head>
      <div className={styles.bar}>
        <h1>Logo Preview</h1>
        <div className={styles.segments}>
          {(Object.keys(VARIANTS) as VariantName[]).map((key) => (
            <button
              key={key}
              type="button"
              aria-pressed={key === view.variant}
              onClick={() => update({ variant: key })}
            >
              {key[0].toUpperCase() + key.slice(1)}
            </button>
          ))}
        </div>
        <label className={styles.group}>
          Zoom
          <input
            type="range"
            min={4}
            max={32}
            value={view.zoom}
            onChange={(event) => update({ zoom: +event.target.value })}
          />
          {view.zoom}×
        </label>
        <div className={styles.group}>
          <label
            title={lettering ? '' : letteringError || 'Loading Aeonik…'}
            style={{ opacity: lettering ? 1 : 0.5 }}
          >
            <input
              type="checkbox"
              checked={view.text && lettering !== null}
              disabled={!lettering}
              onChange={(event) => update({ text: event.target.checked })}
            />
            Text
          </label>
          <label>
            <input
              type="checkbox"
              checked={view.dimensions}
              onChange={(event) => update({ dimensions: event.target.checked })}
            />
            Dimensions
          </label>
          <span>(unit = stroke width)</span>
        </div>
        <div className={styles.swatches} title="Strokes, back to front">
          {variant.sheets.map((color, i) => (
            <span key={i}>
              <i
                style={{
                  background: color,
                  opacity: variant.opacity?.[i] ?? 1,
                }}
              />
              {variant.opacity
                ? `${Math.round(variant.opacity[i] * 100)}%`
                : color}
            </span>
          ))}
        </div>
        <span className={styles.spacer} />
        <div className={styles.group}>
          <button
            type="button"
            className={styles.primary}
            onClick={() =>
              download(name('svg'), new Blob([svg], { type: 'image/svg+xml' }))
            }
          >
            Download SVG
          </button>
          <button type="button" onClick={copy}>
            {copied ? 'Copied' : 'Copy SVG'}
          </button>
          <button type="button" onClick={downloadPng}>
            Download PNG
          </button>
          <select
            aria-label="PNG height"
            value={pngHeight}
            onChange={(event) => setPngHeight(+event.target.value)}
          >
            {PNG_HEIGHTS.map((height) => (
              <option key={height} value={height}>
                {height} px
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className={styles.stageWrap}>
        <div
          className={styles.stage}
          style={{
            width: (width + 2 * PADDING) * view.zoom,
            height: full * view.zoom,
            background: variant.page,
          }}
        >
          <div
            className={styles.logo}
            style={{ left: PADDING * view.zoom, top: PADDING * view.zoom }}
            dangerouslySetInnerHTML={{
              __html: buildSvg(view.variant, {
                height: size * view.zoom,
                id: 'preview',
                lettering: text,
              }),
            }}
          />
          {view.dimensions && (
            <svg
              className={styles.overlay}
              viewBox={`${-full / 2} ${-full / 2} ${width + 2 * PADDING} ${full}`}
              dangerouslySetInnerHTML={{
                __html: buildDimensions(
                  DIMENSION_INK[onDark ? 'dark' : 'light'],
                  variant.page,
                  text
                ),
              }}
            />
          )}
        </div>
      </div>

      <div className={styles.sizes}>
        {(text ? [24, 32, 48, 64, 96] : [24, 32, 48, 64, 128, 256]).map(
          (height) => (
            <figure key={height}>
              <div style={{ background: variant.page }}>
                <img src={dataUrl(svg)} height={height} alt="" />
              </div>
              {height} px{text ? ' tall' : ''}
            </figure>
          )
        )}
      </div>
    </div>
  );
};

export default LogoPreview;
