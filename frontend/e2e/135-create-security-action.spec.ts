import {expect, test} from '@playwright/test';

import {loginAsFixtureUser} from './helpers';
import {
  createIsinChange,
  expectIsinChangeMail,
  expectSecurityAction,
  findSecurityAction,
  getSecurityActionTree,
  loadE2EIsinChanges,
  openSecurityActions,
  securityActionRow,
} from './security-action.helpers';

const ISIN_CHANGES = loadE2EIsinChanges();

for (const fixture of ISIN_CHANGES) {
  test.describe.serial(`create ISIN change ${fixture.isinOld} -> ${fixture.isinNew}`, () => {
    // SecurityActionCreateComponent submits the date picker's JavaScript Date. UTC keeps the displayed fixture date
    // from becoming the previous day when it is serialized from a Europe/Zurich browser context.
    test.use({viewport: {width: 1600, height: 1200}, timezoneId: 'UTC'});

    test('admin creates and verifies the ISIN change and notification', async ({page}) => {
      const credentials = await loginAsFixtureUser(page, fixture.createNickname);
      await openSecurityActions(page);

      let tree = await getSecurityActionTree(page);
      let action = findSecurityAction(tree, fixture);
      const created = !action;
      if (created) {
        await createIsinChange(page, fixture, credentials.locale ?? 'de-CH');
        tree = await getSecurityActionTree(page);
        action = findSecurityAction(tree, fixture);
      }

      expectSecurityAction(action, fixture, created ? 0 : action!.appliedCount);
      await expect(securityActionRow(page, action!)).toBeVisible({timeout: 15_000});
      await expectIsinChangeMail(page, fixture, 'S');
    });
  });
}
