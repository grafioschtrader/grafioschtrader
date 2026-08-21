import {test} from '@playwright/test';

import {loginAsFixtureUser} from './helpers';
import {
  expectSecurityTransfer,
  ensureSecurityTransferQuote,
  loadE2ESecurityTransfers,
  openSecurityTransferDialog,
  reconcileSecurityTransfer,
  resolveTransferAccounts,
  submitSecurityTransfer,
} from './security-transfer.helpers';

const SECURITY_TRANSFERS = loadE2ESecurityTransfers();

for (const fixture of SECURITY_TRANSFERS) {
  test.describe.serial(`security transfer ${fixture.isin} from ${fixture.sourceAccountName} to ${fixture.targetAccountName}`,
    () => {
      test.use({viewport: {width: 1600, height: 1200}, timezoneId: 'UTC'});

      test('creates the security transfer from its fixture', async ({page}) => {
        test.setTimeout(240_000);
        await loginAsFixtureUser(page, 'alledit');
        await ensureSecurityTransferQuote(page, fixture);
        const credentials = await loginAsFixtureUser(page, fixture.loginNickname);
        const resolved = await resolveTransferAccounts(page, fixture);
        await reconcileSecurityTransfer(page, fixture, resolved);
        const dialog = await openSecurityTransferDialog(page, fixture, resolved);
        await submitSecurityTransfer(page, dialog, fixture, credentials.locale!);
        await expectSecurityTransfer(page, fixture, resolved);
      });
    });
}
