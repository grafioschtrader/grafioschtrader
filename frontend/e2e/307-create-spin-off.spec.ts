import { expect, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';
import {
  createSpinOff,
  deleteSecurityAction,
  expectSpinOff,
  findSpinOff,
  getSecurityActionTree,
  loadE2ESpinOffs,
  openSecurityActions,
  securityActionRow
} from './security-action.helpers';

/**
 * Records the spin-offs of testdata/spin_off.json as administrator. Parent and existing child are created by the
 * JUnit suite from testdata/spin-off-securities.json; the child of a LOOKUP row is given only by ISIN and trading
 * currency and is created by the dialog. An unapplied action left by a previous run is deleted first, so every run
 * exercises the dialog again; an applied one is kept and only verified.
 */
const SPIN_OFFS = loadE2ESpinOffs();

for (const fixture of SPIN_OFFS) {
  test.describe.serial(`record spin-off ${fixture.parentIsin} -> ${fixture.childIsin}`, () => {
    test.use({ viewport: { width: 1600, height: 1200 }, timezoneId: 'Europe/Zurich' });

    test(`admin records the spin-off, child ${fixture.childMode}`, async ({ page }) => {
      test.setTimeout(180_000);
      const credentials = await loginAsFixtureUser(page, fixture.createNickname);
      await openSecurityActions(page);

      let tree = await getSecurityActionTree(page);
      let action = findSpinOff(tree, fixture);
      if (action && action.appliedCount === 0) {
        await deleteSecurityAction(page, action);
        action = undefined;
        await page.reload();
        await openSecurityActions(page);
      }
      if (!action) {
        await createSpinOff(page, fixture, credentials.locale ?? 'de-CH');
        tree = await getSecurityActionTree(page);
        action = findSpinOff(tree, fixture);
      }

      expectSpinOff(action, fixture);
      await expect(securityActionRow(page, action!)).toBeVisible({ timeout: 15_000 });
    });
  });
}
