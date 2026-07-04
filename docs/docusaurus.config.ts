import { themes as prismThemes } from 'prism-react-renderer';
import type { Config } from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

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

  presets: [
    [
      'classic',
      {
        docs: {
          path: './content',
          routeBasePath: '/',
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
      title: 'React Native Bottom Sheet',
      items: [
        {
          href: 'https://github.com/software-mansion-labs/react-native-bottom-sheet',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      copyright: `© ${new Date().getFullYear()} Software Mansion`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
