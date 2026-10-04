import { defineEcConfig } from 'astro-expressive-code';

export default defineEcConfig({
  themes: ['github-dark', 'github-light'],
  styleOverrides: {
    codeFontFamily: 'JetBrains Mono Variable, monospace',
    codeFontSize: '0.875rem',
    borderRadius: '0.25rem',
  },
  plugins: [
    {
      name: 'Keyboard-accessible code',
      hooks: {
        postprocessRenderedBlock: ({ renderData }) => {
          const walk = (node) => {
            if (node.type === 'element' && node.tagName === 'pre') node.properties.tabIndex = 0;
            node.children?.forEach(walk);
          };
          walk(renderData.blockAst);
        },
      },
    },
  ],
});
