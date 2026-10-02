import { expect, Page } from '@playwright/test';

/** Creates the allocation hierarchy and returns the instrument node that accepts a dip strategy. */
export async function createStrategySecurityNode(
  page: Page,
  headers: Record<string, string>,
  name: string,
  idWatchlist: number,
  idSecuritycurrency: number
) {
  const created = await page.request.post('/api/algotop/createfromwatchlist', {
    headers,
    data: { name, idWatchlist }
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const top = await created.json();
  const response = await page.request.get(`/api/algotop/${top.idAlgoAssetclassSecurity}/hierarchy`, { headers });
  expect(response.ok(), await response.text()).toBeTruthy();
  const hierarchy = await response.json();
  const node = hierarchy.algoAssetclassList
    .flatMap((assetclass: any) => assetclass.algoSecurityList)
    .find((security: any) => security.security.idSecuritycurrency === idSecuritycurrency);
  expect(node, 'the watchlist instrument has a security-level strategy node').toBeTruthy();
  return node;
}
