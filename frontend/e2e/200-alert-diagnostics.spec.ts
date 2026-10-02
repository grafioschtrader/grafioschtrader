import { expect, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

/**
 * The alert diagnostics dialog — the one place a user can see why an alert did or did not fire, and can push a
 * notification that failed to be delivered back into the queue.
 *
 * The dialog has three tabs, and they answer three different questions, which is the whole reason this spec exists:
 *
 * 1. *Evaluation* — was the rule evaluable at all? A rule whose quote is missing or stale reports `UNAVAILABLE`
 *    with a reason, instead of silently producing no alarm.
 * 2. *Trading decisions* — what did the decision engine actually recommend for an instrument, with which units,
 *    amount and rationale.
 * 3. *Notifications* — did the message reach the user, and if not, may it be retried.
 *
 * Every response of `/api/algoalerts/**` is intercepted, so this spec creates no database record, sends no mail and
 * writes no message to anybody's inbox. That is deliberate: whether an evaluation is correct, whether routing picks
 * the right channel and whether a redelivery really goes out is decided by the backend tests, which can set up the
 * market data for it. What cannot be tested there is the dialog itself — that the three tabs are fed from three
 * different endpoints, that a row has to be selected before a retry is offered, and that a retried row loses the
 * offer again. That is what is covered here.
 *
 * Two details in the interception are load-bearing.
 *
 * - The handler dispatches on the path suffix and treats everything it does not recognize as the notification list.
 * - The notification row is served as `FAILED` until the first retry and as `PENDING` afterwards. The context menu of
 *   the notification table only enables the retry entry for `FAILED` and `REVIEW_REQUIRED`, and the dialog reloads
 *   the list after a retry — so this single flag is what proves the entry follows the delivery state rather than
 *   merely the selection.
 *
 * The retry entry lives in the context menu of the table, which is appended to the page body, not to the dialog; it
 * is therefore looked up on the page. The menu is closed by a left click on the row, never by Escape, which would
 * close the dialog as well.
 *
 * `UC1_ENTRY` and `UC1_DECISIONS` are not labels of this spec: the first is the rationale key that
 * `AlgoUc1DecisionService` writes into `algo_event_log`, the second the NLS key of the tab. Both are matched
 * literally because the raw key is what the UI renders before the translations have arrived.
 *
 * The spec logs in as `alledit`, which is not a read-only user — the evaluate button and the retry menu entry are
 * only rendered for a user who may write.
 */
test('alert diagnostics separates evaluations and delivery recovery', async ({ page, request }) => {
  const info = await request.get(`${process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'}/api/gtinfo`);
  expect((await info.json()).databaseName, 'Never log into the production application in an E2E test').toBe(
    'grafioschtrader_t'
  );
  await loginAsFixtureUser(page, 'alledit');

  // Counts the accepted retries; it doubles as the switch that turns the failed notification into a pending one.
  let retries = 0;
  await page.route('**/api/algoalerts/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/retry')) {
      retries++;
      await route.fulfill({ status: 202, body: '' });
    } else if (path.endsWith('/trading')) {
      await route.fulfill({
        json: [
          {
            idAlgoStrategy: 1,
            idSecuritycurrency: 2,
            contextName: 'Trading fixture',
            strategyName: 'Daily dip',
            securityName: 'Dip instrument',
            valuationDate: '2026-09-07',
            recommendedAction: 'REBALANCE_BUY',
            recommendedUnits: 5,
            recommendedAmount: 400,
            currency: 'CHF',
            price: 80,
            rationale: 'UC1_ENTRY'
          }
        ]
      });
    } else if (path.endsWith('/evaluations')) {
      // A rule that could not be evaluated has to name its reason rather than disappear from the report. The backend
      // stores the reason as an NLS key, which the dialog translates.
      await route.fulfill({
        json: [
          {
            strategyId: 1,
            securityId: 2,
            contextName: 'Diagnostic fixture',
            securityName: 'Price fixture',
            strategyType: 'AS_OBSERVED_SECURITY_ABSOLUTE_PRICE',
            active: true,
            outcome: 'UNAVAILABLE',
            reason: 'ALERT_REASON_QUOTE_MISSING_OR_STALE'
          }
        ]
      });
    } else {
      // The notification list: failed while no retry was accepted, pending once one was.
      await route.fulfill({
        json: [
          {
            id: 999,
            contextName: 'Diagnostic fixture',
            securityName: 'Delivery fixture',
            deliveryStatus: retries ? 'PENDING' : 'FAILED',
            deliveryChannels: 'INTERNAL_AND_EXTERNAL_MAIL',
            deliveryAttempts: retries ? 0 : 8,
            internalCompletedAt: '2026-09-07T12:00:00',
            deliveryError: 'SMTP unavailable',
            alarmDetails: '{}'
          }
        ]
      });
    }
  });

  await page.goto('/mainview/algooverview');
  await page.getByRole('button', { name: /Alert diagnostics|Alarmdiagnose|ALERT_DIAGNOSTICS/i }).click();
  const dialog = page.getByRole('dialog');

  // Evaluation tab: the unavailable rule states why it could not be evaluated, as translated text, not as the key.
  await expect(
    dialog.getByRole('cell', { name: /^(Required quote is missing or stale|Benötigter Kurs fehlt oder ist veraltet)$/ })
  ).toBeVisible();

  // Trading tab: instrument and strategy of a recommendation are shown, not just the raw action.
  await dialog.getByRole('tab', { name: /Trading decisions|Handelsentscheidungen|UC1_DECISIONS/i }).click();
  await expect(dialog.getByText('Dip instrument', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Daily dip', { exact: true })).toBeVisible();

  // Notification tab: the delivery error is visible and a retry needs an explicit, retryable selection.
  await dialog.getByRole('tab', { name: /Notifications|Benachrichtigungen|ALERT_NOTIFICATIONS/i }).click();
  await expect(dialog.getByText('SMTP unavailable', { exact: true })).toBeVisible();
  const row = dialog.getByText('Delivery fixture', { exact: true });
  const retry = page.getByRole('menuitem', { name: /Retry selected|Ausgew.*erneut|ALERT_RETRY_SELECTED/i });
  await row.click({ button: 'right' });
  await expect(retry, 'nothing is selected yet').toBeDisabled();
  await row.click();
  await row.click({ button: 'right' });
  await expect(retry, 'a failed notification may be retried').toBeEnabled();
  await retry.click();
  await expect.poll(() => retries, { message: 'the retry reached the backend exactly once' }).toBe(1);
  // The reload clears the selection; selecting the row again shows it pending, so no second retry is offered.
  await row.click();
  await row.click({ button: 'right' });
  await expect(retry, 'a pending notification may not be retried again').toBeDisabled();
});
