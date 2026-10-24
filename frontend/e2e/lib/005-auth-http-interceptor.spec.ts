import { authHttpInterceptorScenarios } from './auth-http-interceptor.scenarios';
import { libHelpers } from './helpers';

authHttpInterceptorScenarios(libHelpers, async (page, user) => {
  await page.locator('#tenantName').fill(user.tenantName);
  await page.locator('button[type="submit"]').click();
});
