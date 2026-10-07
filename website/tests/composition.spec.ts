import { test, expect } from '@playwright/test';

test('composition dependencies use distinct node anchors and never cross or overlap', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('img', { name: /^An order response, composed from shared work/ })).toBeVisible();
  await expect(page.locator('.graph-node text')).toHaveText([
    'OrderPageTile',
    'OrderSummaryTile',
    'LogisticsTile',
    'CustomerTile',
    'LineItemsTile',
    'OrderTile',
  ]);
  await expect(page.locator('.graph-shared text')).toHaveText('OrderTile');
  await expect(page.locator('.composition-figure figcaption')).toHaveText(
    'Three branches. One OrderTile execution per Mosaic.',
  );
  const edges = await page.locator('.graph-edges path').evaluateAll((paths) => {
    const bounds = new Map(
      Array.from(document.querySelectorAll<SVGGraphicsElement>('.composition-figure > svg [data-tile]')).map((node) => [
        node.dataset.tile,
        node.getBoundingClientRect(),
      ]),
    );
    const onBorder = (point: number[], box: DOMRect) =>
      ((Math.abs(point[0] - box.left) <= 1 || Math.abs(point[0] - box.right) <= 1) &&
        point[1] >= box.top - 1 &&
        point[1] <= box.bottom + 1) ||
      ((Math.abs(point[1] - box.top) <= 1 || Math.abs(point[1] - box.bottom) <= 1) &&
        point[0] >= box.left - 1 &&
        point[0] <= box.right + 1);
    return paths.map((element) => {
      const path = element as SVGPathElement;
      const length = path.getTotalLength();
      const steps = Math.max(1, Math.ceil(length / 4));
      const points = Array.from({ length: steps + 1 }, (_, index) => {
        const point = path.getPointAtLength((length * index) / steps).matrixTransform(path.getScreenCTM()!);
        return [point.x, point.y];
      });
      const source = path.dataset.source!;
      const target = path.dataset.target!;
      return {
        source,
        target,
        points,
        anchored: onBorder(points[0], bounds.get(source)!) && onBorder(points.at(-1)!, bounds.get(target)!),
      };
    });
  });
  expect(edges.map(({ source, target }) => `${source} → ${target}`).sort()).toEqual(
    [
      'OrderPageTile → OrderSummaryTile',
      'OrderPageTile → LogisticsTile',
      'OrderSummaryTile → CustomerTile',
      'OrderSummaryTile → LineItemsTile',
      'OrderSummaryTile → OrderTile',
      'CustomerTile → OrderTile',
      'LineItemsTile → OrderTile',
    ].sort(),
  );
  for (const edge of edges) expect(edge.anchored, `${edge.source} → ${edge.target}`).toBe(true);
  const segments = edges.map(({ points }) => points.slice(1).map((point, i) => [points[i], point]));
  // Sample the rendered path, allowing curves, transforms, and any SVG command syntax.
  const intersect = (a: number[][], b: number[][]) => {
    const cross = (p: number[], q: number[], r: number[]) =>
      (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0]);
    const boundsOverlap =
      Math.max(Math.min(a[0][0], a[1][0]), Math.min(b[0][0], b[1][0])) <=
        Math.min(Math.max(a[0][0], a[1][0]), Math.max(b[0][0], b[1][0])) &&
      Math.max(Math.min(a[0][1], a[1][1]), Math.min(b[0][1], b[1][1])) <=
        Math.min(Math.max(a[0][1], a[1][1]), Math.max(b[0][1], b[1][1]));
    return (
      boundsOverlap &&
      cross(a[0], a[1], b[0]) * cross(a[0], a[1], b[1]) <= 0 &&
      cross(b[0], b[1], a[0]) * cross(b[0], b[1], a[1]) <= 0
    );
  };
  for (let i = 0; i < edges.length; i++) {
    for (let j = i + 1; j < edges.length; j++) {
      expect(
        segments[i].some((a) => segments[j].some((b) => intersect(a, b))),
        `Edges ${i} and ${j} cross, touch, or share a segment`,
      ).toBe(false);
    }
  }
});
