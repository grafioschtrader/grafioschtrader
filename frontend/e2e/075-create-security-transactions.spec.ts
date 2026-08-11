import {test} from '@playwright/test';
import {loginAsFixtureUser} from './helpers';
import {
  createSecurityTransactionGroup,
  deleteSecurityTransactionGroup,
  expectSecurityTransactionGroup,
  loadE2ESecurityTransactionGroups,
} from './security-transaction.helpers';

for (const group of loadE2ESecurityTransactionGroups()) {
  test(`${group.identifier} recreates its security transactions on ${group.watchlistName}`, async ({page}) => {
    await loginAsFixtureUser(page, group.loginNickname);
    await deleteSecurityTransactionGroup(page, group);
    await createSecurityTransactionGroup(page, group);
    await expectSecurityTransactionGroup(page, group);
  });
}
