import { expect, Page, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';
import {
  ApiSecurityAction,
  applySpinOff,
  authHeaders,
  findSpinOff,
  getSecurityActionTree,
  loadE2ESpinOffApplications,
  openSecurityActions,
  reverseActiveApplication,
  SpinOffApplyFixture,
  spinOffPreviewStatus
} from './security-action.helpers';

/**
 * Applies the spin-offs of General Electric recorded by 307 for the tenant of testdata/spin_off_apply.json: GE
 * HealthCare on 2023-01-04 (3 : 1) and GE Vernova on 2024-04-02 (4 : 1) on 300 GE bought on 2022-12-01. Because the
 * provider splits of Yahoo on both dates are dropped, the entitlement stays 300 parent units and the tenant receives
 * 100 and 75 child units. A previous run is undone first: the applications are reversed, newest first, and the
 * purchase with the fixture note is deleted, which the entitlement protection allows only after the reversals.
 */
const APPLICATIONS = loadE2ESpinOffApplications();

interface ApiNamedAccount {
  idSecuritycashAccount: number;
  name: string;
}

interface ApiPortfolio {
  name: string;
  securityaccountList?: ApiNamedAccount[];
  cashaccountList?: (ApiNamedAccount & Record<string, unknown>)[];
}

interface ApiTransaction {
  idTransaction: number;
  idSecurityaccount: number | null;
  transactionType: string;
  units: number | null;
  note: string | null;
  securityActionRole?: string | null;
  security?: { idSecuritycurrency: number; isin: string; currency: string } | null;
}

for (const fixture of APPLICATIONS) {
  test.describe.serial(`apply spin-offs of ${fixture.parentIsin} for ${fixture.nickname}`, () => {
    test.use({ viewport: { width: 1600, height: 1200 }, timezoneId: 'Europe/Zurich' });

    test('the tenant applies both spin-offs on 300 parent units', async ({ page }) => {
      test.setTimeout(300_000);
      await loginAsFixtureUser(page, fixture.nickname);
      await openSecurityActions(page);

      let tree = await getSecurityActionTree(page);
      const actions = fixture.spinOffs.map((spinOff) => {
        const action = findSpinOff(tree, {
          ...spinOff,
          parentIsin: fixture.parentIsin,
          parentCurrency: fixture.parentCurrency
        });
        expect(action, `spin-off ${fixture.parentIsin} -> ${spinOff.childIsin} recorded by 307`).toBeDefined();
        return action!;
      });

      await test.step('cleanup of an earlier run', async () => {
        for (const action of [...actions].reverse()) {
          await reverseActiveApplication(page, action, tree.appliedByCurrentTenant[action.idSecurityAction]);
        }
        for (const purchase of (await tenantTransactions(page)).filter((t) => t.note === fixture.note)) {
          const deleted = await page.request.delete(`/api/transaction/${purchase.idTransaction}`, {
            headers: await authHeaders(page),
            failOnStatusCode: false
          });
          expect(deleted.ok(), `deleting the entitlement purchase: ${await deleted.text()}`).toBeTruthy();
        }
      });

      const accountId = await createEntitlementPurchase(page, fixture, actions[0]);

      await test.step('wait until the parent refresh has decided the history bases', async () => {
        for (const action of actions) {
          await expect
            .poll(() => spinOffPreviewStatus(page, action), { timeout: 120_000, intervals: [2_000] })
            .toBe(200);
        }
      });

      await page.reload();
      await openSecurityActions(page);
      for (const [index, action] of actions.entries()) {
        const application = await applySpinOff(page, action, fixture.securityAccount, fixture.spinOffs[index]);
        expect(application.bookedAccounts, 'booked accounts').toBe(1);
      }

      tree = await getSecurityActionTree(page);
      for (const action of actions) {
        const stored = tree.systemActions.find((a) => a.idSecurityAction === action.idSecurityAction);
        expect(stored?.appliedCount ?? 0, 'applied count').toBeGreaterThanOrEqual(1);
        expect(tree.appliedByCurrentTenant[action.idSecurityAction]?.reversed, 'active application').toBe(false);
      }
      const transactions = (await tenantTransactions(page)).filter((t) => t.idSecurityaccount === accountId);
      expect(
        transactions
          .filter((t) => t.securityActionRole)
          .map((t) => t.securityActionRole)
          .sort(),
        'three legs per application'
      ).toEqual([
        'SPIN_OFF_CHILD',
        'SPIN_OFF_CHILD',
        'SPIN_OFF_REPURCHASE',
        'SPIN_OFF_REPURCHASE',
        'SPIN_OFF_SALE',
        'SPIN_OFF_SALE'
      ]);
      expect(units(transactions, fixture.parentIsin), 'parent units at the end').toBeCloseTo(fixture.units, 8);
      for (const spinOff of fixture.spinOffs) {
        expect(units(transactions, spinOff.childIsin), `units of ${spinOff.childIsin}`).toBeCloseTo(
          spinOff.childUnits,
          8
        );
      }
    });
  });
}

/** All transactions of the logged-in tenant. */
async function tenantTransactions(page: Page): Promise<ApiTransaction[]> {
  const response = await page.request.get('/api/transaction', { headers: await authHeaders(page) });
  expect(response.ok(), `loading transactions: ${await response.text()}`).toBeTruthy();
  return (await response.json()) as ApiTransaction[];
}

/** Net units of a security over the given transactions. */
function units(transactions: ApiTransaction[], isin: string): number {
  return transactions
    .filter((t) => t.security?.isin === isin)
    .reduce(
      (sum, t) =>
        sum + (t.transactionType === 'ACCUMULATE' ? 1 : t.transactionType === 'REDUCE' ? -1 : 0) * (t.units ?? 0),
      0
    );
}

/**
 * Books the entitlement purchase through REST at the stored close of the parent on the purchase date (set-up, not
 * under test) and returns the id of the securities account.
 */
async function createEntitlementPurchase(
  page: Page,
  fixture: SpinOffApplyFixture,
  action: ApiSecurityAction
): Promise<number> {
  return test.step(`buy ${fixture.units} ${fixture.parentIsin} on ${fixture.purchaseDate}`, async () => {
    const headers = await authHeaders(page);
    const portfolios = await page.request.get('/api/portfolio/tenant', { headers });
    expect(portfolios.ok(), `loading portfolios: ${await portfolios.text()}`).toBeTruthy();
    const portfolio = ((await portfolios.json()) as ApiPortfolio[]).find((p) => p.name === fixture.portfolio);
    const securityAccount = portfolio?.securityaccountList?.find((a) => a.name === fixture.securityAccount);
    const cashAccount = portfolio?.cashaccountList?.find((a) => a.name === fixture.cashAccount);
    expect(securityAccount, `securities account ${fixture.securityAccount}`).toBeDefined();
    expect(cashAccount, `cash account ${fixture.cashAccount}`).toBeDefined();

    const parent = action.securityOld!;
    const basicDate = fixture.purchaseDate.replaceAll('-', '');
    const close = await page.request.get(`/api/historyquote/${parent.idSecuritycurrency}/${basicDate}/true`, {
      headers
    });
    expect(close.ok(), `close of the parent on ${fixture.purchaseDate}: ${await close.text()}`).toBeTruthy();
    const quotation = ((await close.json()) as { close: number }).close;
    const created = await page.request.post('/api/transaction', {
      headers: { ...headers, 'Content-Type': 'application/json' },
      data: {
        idSecurityaccount: securityAccount!.idSecuritycashAccount,
        cashaccount: cashAccount,
        security: parent,
        transactionType: 'ACCUMULATE',
        units: fixture.units,
        quotation,
        cashaccountAmount: -Math.round(fixture.units * quotation * 100) / 100,
        transactionTime: `${fixture.purchaseDate}T00:00:00`,
        note: fixture.note
      },
      failOnStatusCode: false
    });
    expect(created.ok(), `creating the entitlement purchase: ${await created.text()}`).toBeTruthy();
    return securityAccount!.idSecuritycashAccount;
  });
}
