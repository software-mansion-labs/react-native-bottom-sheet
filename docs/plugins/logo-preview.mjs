// Serves the logo preview (`src/logo`) at `/logo-preview`, in development only:
// it is a tool for working on the logo, and it reads the licensed fonts, so it
// has no place in the published site.
/** @type {import('@docusaurus/types').PluginModule} */
const logoPreview = (context) => ({
  name: 'logo-preview',
  contentLoaded({ actions }) {
    if (process.env.NODE_ENV !== 'development') return;
    actions.addRoute({
      path: `${context.baseUrl}logo-preview`,
      component: '@site/src/logo/LogoPreview.tsx',
      exact: true,
    });
  },
});

export default logoPreview;
