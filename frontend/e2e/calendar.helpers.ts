import { expect, Page, Route } from '@playwright/test';

/** Holds the first real response until released, including requests cancelled by navigation. */
export async function holdCalendarResponse(
  page: Page,
  url: string,
  method: 'GET' | 'PUT' = 'GET'
): Promise<{ release: () => void; arrived: Promise<void>; done: Promise<void> }> {
  let release!: () => void;
  let arrived!: () => void;
  let done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const requestArrived = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  let holding = false;
  const handler = async (route: Route): Promise<void> => {
    if (holding || route.request().method() !== method) {
      await route.fallback();
      return;
    }
    holding = true;
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    arrived();
    await gate;
    try {
      await route.fulfill({ response });
    } catch (error) {
      // Superseding a request or closing its owner aborts the browser request while the answer is held.
      if (route.request().failure()?.errorText !== 'net::ERR_ABORTED') {
        throw error;
      }
    } finally {
      await page.unroute(url, handler);
      done();
    }
  };
  await page.route(url, handler);
  return { release, arrived: requestArrived, done: completed };
}
