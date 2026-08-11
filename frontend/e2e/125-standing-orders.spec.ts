import {test} from '@playwright/test';
import {loginAsFixtureUser} from './helpers';
import {
  createCashStandingOrder,
  createSecurityStandingOrder,
  deleteExistingStandingOrders,
  expectStandingOrders,
  loadStandingOrders,
} from './standing-order.helpers';

/**
 * Recreates the cash and security standing orders entered manually by user 'alledit'. Matching orders from an earlier
 * successful or partial run are deleted first, so this spec can be repeated against the same grafioschtrader_t.
 */
const STANDING_ORDERS = loadStandingOrders();

test.describe.serial("standing orders of 'alledit'", () => {
  // The security dialog contains both the transaction and scheduling fieldsets. At the default 720px height its
  // lower date fields sit outside the non-scrolling centered dialog, just like the instrument search in 065.
  test.use({viewport: {width: 1600, height: 1400}});

  test('recreates and verifies every fixture standing order', async ({page}) => {
    await loginAsFixtureUser(page, 'alledit');
    await deleteExistingStandingOrders(page, STANDING_ORDERS);

    for (const fixture of STANDING_ORDERS) {
      if (fixture.standingOrder.kind === 'cash') {
        await createCashStandingOrder(page, fixture);
      } else {
        await createSecurityStandingOrder(page, fixture);
      }
    }

    await expectStandingOrders(page, STANDING_ORDERS);
  });
});
