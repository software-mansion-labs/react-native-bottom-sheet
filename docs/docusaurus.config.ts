import type { Config } from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';
import type { Options as RedirectsOptions } from '@docusaurus/plugin-client-redirects';
import reactNativeWeb from './plugins/react-native-web.mjs';
import swmGeo from './plugins/swm-geo.mjs';
import { swmDark, swmLight } from './src/prism/swm';

const config: Config = {
  title: 'React Native Bottom Sheet',
  tagline: 'Native bottom-sheet components for React Native',

  future: {
    v4: true,
  },

  url: 'https://software-mansion-labs.github.io',
  baseUrl: process.env.DOCUSAURUS_BASE_URL ?? '/',
  organizationName: 'software-mansion-labs',
  projectName: 'react-native-bottom-sheet',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  plugins: [
    reactNativeWeb,
    swmGeo,
    [
      '@docusaurus/plugin-client-redirects',
      {
        redirects: [
          {
            from: ['/scrollable-negotiation', '/docs/scrollable-negotiation'],
            to: '/docs/scroll-coordination',
          },
        ],
        // The docs used to sit at the root, with the overview at /overview.
        createRedirects: (path) => {
          if (path === '/docs' || path === '/docs/') return '/overview';
          if (path.startsWith('/docs/')) return path.slice('/docs'.length);
          return undefined;
        },
      } satisfies RedirectsOptions,
    ],
  ],

  themes: [
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        indexBlog: false,
        docsDir: 'content',
        docsRouteBasePath: '/docs',
        highlightSearchTermsOnTargetPage: true,
        explicitSearchResultPath: true,
      },
    ],
  ],

  presets: [
    [
      'classic',
      {
        docs: {
          path: './content',
          breadcrumbs: false,
          sidebarPath: './sidebars.ts',
          editUrl:
            'https://github.com/software-mansion-labs/react-native-bottom-sheet/tree/main/docs/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/cover.png',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      logo: {
        alt: 'React Native Bottom Sheet',
        src: 'img/logo-with-text.svg',
        srcDark: 'img/logo-with-text-dark.svg',
        width: 155,
        height: 36,
      },
      items: [
        { type: 'search', position: 'right' },
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          label: 'Docs',
          position: 'right',
          className: 'navbar-docs',
        },
        {
          'href':
            'https://github.com/software-mansion-labs/react-native-bottom-sheet',
          'position': 'right',
          'className': 'navbar-github',
          'aria-label': 'GitHub',
          'html':
            '<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="currentColor" d="M12 .5a11.5 11.5 0 0 0-3.64 22.41c.58.1.79-.25.79-.56v-2c-3.2.7-3.88-1.37-3.88-1.37-.52-1.33-1.28-1.69-1.28-1.69-1.05-.71.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.29 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.17 1.18a11 11 0 0 1 5.77 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.8 1.19 1.83 1.19 3.09 0 4.42-2.69 5.39-5.26 5.68.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.5 11.5 0 0 0 12 .5Z"/></svg>',
        },
      ],
    },
    footer: {
      copyright: `© ${new Date().getFullYear()} Software Mansion. Read about our <a href="https://swmansion.com/privacy/policy/" target="_blank" rel="noopener noreferrer">Privacy Policy</a>.`,
    },
    prism: {
      theme: swmLight,
      darkTheme: swmDark,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
