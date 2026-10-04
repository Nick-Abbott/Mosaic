import { readdir, readFile, stat } from 'node:fs/promises';
import { resolve, join, relative } from 'node:path';
import { load } from 'cheerio';

const root = resolve('dist');
async function files(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  return (
    await Promise.all(
      entries.map((entry) => (entry.isDirectory() ? files(join(dir, entry.name)) : [join(dir, entry.name)])),
    )
  ).flat();
}
const html = (await files(root)).filter((file) => file.endsWith('.html'));
const documents = new Map(await Promise.all(html.map(async (file) => [file, load(await readFile(file, 'utf8'))])));
const errors = [];
let checked = 0;
for (const [file, $] of documents) {
  const from = new URL('/' + relative(root, file), 'https://buildmosaic.org');
  for (const node of $('a[href], img[src], script[src], link[href]').toArray()) {
    const raw = $(node).attr('href') ?? $(node).attr('src');
    if (node.tagName === 'link' && $(node).attr('rel') === 'canonical' && relative(root, file) === '404.html') continue;
    if (!raw || /^(mailto:|tel:|data:|javascript:)/i.test(raw)) continue;
    const url = new URL(raw, from);
    if (url.origin !== from.origin) continue;
    const pathname = decodeURIComponent(url.pathname);
    let target = resolve(root, '.' + pathname);
    if (!target.startsWith(root + '/') && target !== root) {
      errors.push(`${file}: path escapes output: ${raw}`);
      continue;
    }
    try {
      if ((await stat(target)).isDirectory()) target = join(target, 'index.html');
      await stat(target);
      if (url.hash && documents.has(target)) {
        const id = decodeURIComponent(url.hash.slice(1));
        // Dokka uses content switches with generated fragment names as well as DOM IDs.
        const doc = documents.get(target);
        const found = doc('[id], a[name], [data-name], [data-togglable]')
          .toArray()
          .some((el) =>
            ['id', 'name', 'data-name', 'data-togglable'].some(
              (key) => doc(el).attr(key) === id || doc(el).attr(key) === url.hash.slice(1),
            ),
          );
        if (!found) errors.push(`${relative(root, file)}: missing fragment ${raw}`);
      }
      checked++;
    } catch {
      errors.push(`${relative(root, file)}: missing ${raw}`);
    }
  }
}
if (!documents.has(join(root, 'api/index.html')))
  errors.push('API is missing: run pnpm api before a complete production build.');
if (errors.length) {
  console.error(errors.join('\n'));
  process.exitCode = 1;
} else console.log(`Checked ${checked} internal links and assets across ${html.length} HTML pages (Cheerio).`);
