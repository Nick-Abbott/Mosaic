import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
declare global {
  interface Window {
    mosaicLayoutShift: number;
  }
}

const routes = [
  '/',
  '/start/quick-start/',
  '/concepts/shared-work/',
  '/reference/performance/',
  '/concepts/tiles/',
  '/api/',
];
for (const width of [1440, 1920, 1280, 820, 390, 430]) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${width}px ${theme}: responsive, accessible reading routes`, async ({ page }) => {
      await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 });
      await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' });
      await page.addInitScript(() => {
        window.mosaicLayoutShift = 0;
        new PerformanceObserver((list) => {
          for (const entry of list.getEntries() as (PerformanceEntry & { hadRecentInput: boolean; value: number })[]) {
            if (!entry.hadRecentInput) window.mosaicLayoutShift += entry.value;
          }
        }).observe({ type: 'layout-shift', buffered: true });
      });
      for (const route of routes) {
        expect((await page.goto(route))?.status()).toBe(200);
        await page.evaluate(() => document.fonts.ready);
        await expect(page.locator('main, [role="main"]')).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        if (route === '/') {
          expect(await page.evaluate(() => window.mosaicLayoutShift)).toBeLessThanOrEqual(0.1);
          expect(
            await page
              .locator('.button.primary')
              .first()
              .evaluate((el) => getComputedStyle(el).transitionDuration),
          ).toBe('0s');
        }
        const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
        expect(results.violations, `${route}: ${JSON.stringify(results.violations)}`).toEqual([]);
      }
    });
  }
}

test('homepage navigation, theme persistence, skip link, code copy, and anchor', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/#main$/);
  const previous = await page.locator('html').getAttribute('data-theme');
  await page.locator('#theme-toggle').click();
  expect(await page.locator('html').getAttribute('data-theme')).not.toBe(previous);
  await page.reload();
  expect(await page.locator('html').getAttribute('data-theme')).not.toBe(previous);
  await page.locator('.hero-composition button').first().click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toContain('OrderPageTile');
  await page.getByRole('link', { name: 'Follow the composition' }).click();
  await expect(page).toHaveURL(/#composition$/);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('.mobile-nav summary').click();
  await expect(page.getByRole('navigation', { name: 'Mobile navigation' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.locator('.mobile-nav')).not.toHaveAttribute('open');
  await expect(page.locator('.mobile-nav summary')).toBeFocused();
});

test('documentation search, sidebar, copy, theme, and keyboard code scrolling', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/start/quick-start/');
  await page.locator('starlight-theme-select select').first().selectOption('dark');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page
    .getByRole('button', { name: /search/i })
    .first()
    .click();
  const input = page.locator('dialog input');
  await input.fill('MultiTile');
  await expect(page.locator('dialog .pagefind-ui__result-link').first()).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.locator('dialog')).not.toBeVisible();
  await page.locator('.expressive-code button').first().click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toContain('plugins');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('.sl-menu-button').click();
  await expect(page.locator('#starlight__sidebar')).toBeVisible();
  await page.locator('#starlight__sidebar summary').filter({ hasText: 'Compose responses' }).click();
  await page.locator('#starlight__sidebar a[href="/concepts/shared-work/"]').click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Shared work and identity');
  const code = page.locator('.expressive-code pre').first();
  await code.focus();
  await expect(code).toBeFocused();
  const before = await code.evaluate((el) => el.scrollLeft);
  await page.keyboard.press('ArrowRight');
  await expect.poll(() => code.evaluate((el) => el.scrollLeft)).toBeGreaterThan(before);
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});

test('native Dokka navigation and search', async ({ page }) => {
  await page.goto('/api/');
  await page.locator('.main-content a[href="mosaic-core/index.html"]').click();
  await expect(page).toHaveURL(/\/api\/mosaic-core\/index.html$/);
  await page.locator('#searchBar').click();
  await page.locator('input:visible').first().fill('Canvas');
  await expect(page.getByRole('button', { name: /class Canvas : AutoCloseable/ })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.locator('#theme-toggle-button').click();
});
