import { expect, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';
import {
  applyIsinChange,
  expectIsinChangeMail,
  expectSecurityAction,
  findSecurityAction,
  getSecurityActionTree,
  loadE2EIsinChanges,
  openSecurityActions,
  reverseActiveApplication,
  securityActionRow
} from './security-action.helpers';

const ISIN_CHANGES = loadE2EIsinChanges();

for (const fixture of ISIN_CHANGES) {
  test.describe.serial(`apply ISIN change ${fixture.isinOld} -> ${fixture.isinNew}`, () => {
    test.use({ viewport: { width: 1600, height: 1200 } });

    test('user applies and verifies the ISIN change and notification', async ({ page }) => {
      await loginAsFixtureUser(page, fixture.applyNickname);
      await openSecurityActions(page);

      let tree = await getSecurityActionTree(page);
      let action = findSecurityAction(tree, fixture);
      expect(action, 'spec 135 must create the fixture Security Action before spec 140').toBeDefined();
      await reverseActiveApplication(page, action!, tree.appliedByCurrentTenant[String(action!.idSecurityAction)]);

      await page.reload();
      await openSecurityActions(page);
      tree = await getSecurityActionTree(page);
      action = findSecurityAction(tree, fixture);
      await expect(securityActionRow(page, action!)).toBeVisible({ timeout: 15_000 });
      await applyIsinChange(page, action!);

      tree = await getSecurityActionTree(page);
      action = findSecurityAction(tree, fixture);
      expectSecurityAction(action, fixture, 1);
      const application = tree.appliedByCurrentTenant[String(action!.idSecurityAction)];
      expect(application, 'current tenant Security Action application').toBeDefined();
      expect(application.reversed).toBe(false);
      await expectIsinChangeMail(page, fixture, 'R');
    });
  });
}
