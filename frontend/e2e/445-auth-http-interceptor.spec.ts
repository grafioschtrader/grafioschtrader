import { authHttpInterceptorScenarios } from './lib/auth-http-interceptor.scenarios';
import { gtHelpers } from './helpers';

authHttpInterceptorScenarios(gtHelpers, async (page, user) => {
  await page.locator('#tenantName').fill(user.tenantName);
  await page.locator('select#currency').selectOption({ label: user.currency });
  await page.locator('button[type="submit"]').click();
});
