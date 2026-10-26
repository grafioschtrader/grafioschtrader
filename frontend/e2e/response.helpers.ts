import { expect, Page, Route } from '@playwright/test';

/** Holds one real HTTP response so assertions can observe the view before and after its delivery. */
export async function holdResponse(page: Page, url: string, method = 'GET', responseJson?: unknown) {
  let release!: () => void, arrived!: () => void, done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const pending = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  let holding = false;
  const handler = async (route: Route) => {
    if (holding || route.request().method() !== method) return route.fallback();
    holding = true;
    try {
      const response = await route.fetch();
      expect(response.ok(), await response.text()).toBeTruthy();
      arrived();
      await gate;
      try {
        await route.fulfill({ response, ...(responseJson === undefined ? {} : { json: responseJson }) });
      } catch (error) {
        if (route.request().failure()?.errorText !== 'net::ERR_ABORTED') throw error;
      }
    } finally {
      await page.unroute(url, handler);
      done();
    }
  };
  await page.route(url, handler);
  return { release, arrived: pending, done: completed };
}
