// Small accessibility corrections for native Dokka 2.2 HTML; no layout fork.
const improveDokkaAccessibility = () => {
  document.querySelector('.library-name--link')?.removeAttribute('tabindex');
  const toc = document.querySelector('#toc-listbox');
  toc?.setAttribute('role', 'presentation');
  toc?.removeAttribute('aria-label');
  toc?.closest('nav')?.setAttribute('aria-label', 'API navigation');
  // Dokka's source-set controls are buttons, not list items.
  document.querySelector('#filter-section')?.setAttribute('role', 'group');
  const docs = document.createElement('a');
  docs.href = '/start/overview/';
  docs.className = 'mosaic-user-docs';
  docs.textContent = 'User docs';
  document.querySelector('.navigation-controls')?.prepend(docs);
  document.querySelector('#go-to-top-link')?.setAttribute('aria-label', 'Back to top');
  const search = document.querySelector('#searchBar');
  const fixSearch = () => {
    if (search?.querySelector('button')) {
      search.removeAttribute('role');
      return true;
    }
    return false;
  };
  if (search && !fixSearch()) {
    const observer = new MutationObserver(() => {
      if (fixSearch()) observer.disconnect();
    });
    observer.observe(search, { childList: true, subtree: true });
  }
  if (!document.querySelector('h1')) {
    const heading = document.querySelector('.cover > h2');
    if (heading) {
      const title = document.createElement('h1');
      title.className = heading.className;
      title.textContent = heading.textContent;
      heading.replaceWith(title);
    }
  }
};
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', improveDokkaAccessibility);
else improveDokkaAccessibility();
