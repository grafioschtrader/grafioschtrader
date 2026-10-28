import { expect, Page } from '@playwright/test';

/** Checks actual browser error events, which zoneless hosts must forward to Angular's ErrorHandler. */
export async function assertUnhandledErrorsReachAngular(page: Page): Promise<void> {
  const errors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error' && message.text().startsWith('ERROR')) errors.push(message.text());
  });
  await page.goto('/login');
  await expect(page.locator('#email')).toBeVisible();
  await page.evaluate(() => {
    setTimeout(() => {
      throw new Error('bootstrap-timer-error');
    }, 0);
    void Promise.reject(new Error('bootstrap-rejected-promise'));
  });
  await expect.poll(() => errors.some((message) => message.includes('bootstrap-timer-error'))).toBe(true);
  await expect.poll(() => errors.some((message) => message.includes('bootstrap-rejected-promise'))).toBe(true);
}
