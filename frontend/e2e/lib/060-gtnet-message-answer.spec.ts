import { expect, Locator, Page, test } from '@playwright/test';

import { libHelpers } from './helpers';
import { authHeaders, GTNET_RX, GTNET_ROUTES, openCrudMenu, visibleDialog } from './gtnet.helpers';

/**
 * The auto-answer rules: `gtnet-message-answer-edit.component`, including the variable picker behind
 * `responseMsgConditional`.
 *
 * A `GTNetMessageAnswer` row is what turns an incoming request-response message into an automatic reply, and
 * with an empty table `FirstHandshakeRequestHandler.getDefaultResponseCode()` accepts everything — so every
 * rejection path in GTNet begins with a row created by this form.
 *
 * The form has more behaviour behind it than any other GTNet dialog, and three parts of it need care:
 *
 *  - **`responseMsgCode` is empty until `requestMsgCode` changes.** Its options are recomputed from
 *    `GTNetProtocolService.getAutoAnswerResponseCodes()` in the subscription, so the request code must be
 *    selected first.
 *  - **The variable picker is the point of the spec.** The condition is composed through the context menu of
 *    the textarea rather than typed, because the menu is built from the variable names
 *    `DefaultGTNetResponseResolverConfig` and `GTNetResponseResolver` put into the EvalEx context. Typing the
 *    string would still pass after a variable was renamed on the server; picking it from the menu would not.
 *    The menu is a `p-contextMenu` appended to `body`, opened with a right click on `#responseMsgConditional`.
 *  - **The unique key is `(response_msg_code, priority, request_msg_code)`**, so the same triple twice has to be
 *    refused.
 *
 * Re-runnable: the rows this spec owns are deleted through the REST API before it creates them.
 */

const RULE = {
  requestMsgCode: 'GT_NET_DATA_REQUEST_SEL_RR_C',
  responseMsgCode: 'GT_NET_DATA_REQUEST_REJECTED_S',
  priority: '7',
  waitDaysApply: '3',
  message: 'Library E2E auto-answer rule',
  /** Built through the picker: the variable comes from the menu, the comparison is typed after it. */
  variable: 'RemoteDailyRequestLimit',
  comparison: ' > 100000'
} as const;

/** Every group the picker offers, and one variable from each, as the resolver defines them. */
const PICKER_GROUPS: { label: RegExp; variables: string[] }[] = [
  {
    label: /^(My Server|Mein Server|EVALEX_MY_SERVER)$/,
    variables: ['MyDailyRequestLimit', 'MyTimezone', 'MyMaxLimitLastPrice', 'MyMaxLimitHistorical']
  },
  {
    label: /^(Remote Server|Entfernter Server|EVALEX_REMOTE_SERVER)$/,
    variables: [
      'RemoteDailyRequestLimit',
      'RemoteTimezone',
      'RemoteDomainRemoteName',
      'RemoteMaxLimitLastPrice',
      'RemoteMaxLimitHistorical'
    ]
  },
  { label: /^(Message|Nachricht|EVALEX_MESSAGE)$/, variables: ['Message'] },
  {
    label: /^(Connections|Verbindungen|EVALEX_CONNECTIONS)$/,
    variables: ['TotalConnections', 'ConnectionsLastPrice', 'ConnectionsHistorical']
  },
  {
    label: /^(Calculated|Berechnet|EVALEX_CALCULATED)$/,
    variables: ['TimezoneOffsetHours', 'hour', 'dayOfWeek', 'dailyCount', 'dailyLimit']
  }
];

interface ApiMessageAnswer {
  idGtNetMessageAnswer: number;
  requestMsgCode: string | number;
  responseMsgCode: string | number;
  priority: number;
  responseMsgConditional: string | null;
  responseMsgMessage: string | null;
  waitDaysApply: number;
}

async function readAnswers(page: Page): Promise<ApiMessageAnswer[]> {
  const response = await page.request.get('/api/gtnetmessageanswer', { headers: await authHeaders(page) });
  expect(response.ok(), `loading auto-answer rules: ${response.status()} ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<ApiMessageAnswer[]>;
}

/** Removes the rows this spec owns, identified by the priority it uses — nothing else may be touched. */
async function deleteOwnAnswers(page: Page): Promise<void> {
  const headers = await authHeaders(page);
  for (const answer of await readAnswers(page)) {
    if (answer.priority === Number(RULE.priority)) {
      const response = await page.request.delete(`/api/gtnetmessageanswer/${answer.idGtNetMessageAnswer}`, { headers });
      expect(response.ok(), `deleting auto-answer rule ${answer.idGtNetMessageAnswer}`).toBeTruthy();
    }
  }
}

/** Opens the auto-answer table and returns its container. */
async function openAnswerTable(page: Page): Promise<Locator> {
  await page.goto(GTNET_ROUTES.messageAnswer);
  const container = page.locator('.data-container').first();
  await container.waitFor({ state: 'visible', timeout: 15_000 });
  return container;
}

/** Opens the create dialog of the auto-answer table. */
async function openCreateDialog(page: Page): Promise<Locator> {
  const container = await openAnswerTable(page);
  const menu = await openCrudMenu(page, container);
  await menu.getByRole('menuitem', { name: GTNET_RX.createRecord }).first().click();
  return visibleDialog(page);
}

/** Opens the edit dialog of the single rule this spec owns. */
async function openEditDialog(page: Page): Promise<Locator> {
  const container = await openAnswerTable(page);
  const row = container.locator('tbody tr').filter({ hasText: RULE.message }).first();
  await row.waitFor({ state: 'visible', timeout: 10_000 });
  const menu = await openCrudMenu(page, container, row);
  await menu
    .getByRole('menuitem', { name: /^(Edit|Bearbeiten|EDIT_RECORD)\s/ })
    .first()
    .click();
  return visibleDialog(page);
}

/** Fills everything except the condition, which the picker test composes. */
async function fillRule(dialog: Locator): Promise<void> {
  // The response options are recomputed from the request code, so this order is mandatory.
  await dialog.locator('select#requestMsgCode').selectOption(RULE.requestMsgCode);
  await expect
    .poll(async () => dialog.locator('select#responseMsgCode option').count(), { timeout: 10_000 })
    .toBeGreaterThan(0);
  await dialog.locator('select#responseMsgCode').selectOption(RULE.responseMsgCode);
  await dialog.locator('#priority').fill(RULE.priority);
  await dialog.locator('#waitDaysApply').fill(RULE.waitDaysApply);
  await dialog.locator('#responseMsgMessage').fill(RULE.message);
}

test.describe.serial('GTNet auto-answer rules', () => {
  test('creates a rule whose condition is composed through the variable picker', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');
    await deleteOwnAnswers(page);

    const dialog = await openCreateDialog(page);
    await fillRule(dialog);

    const conditional = dialog.locator('#responseMsgConditional');
    // bindTextareaEvents() attaches its cursor listeners in a setTimeout(…, 100); clicking first also puts the
    // caret in the empty textarea, which is where insertVariable() writes.
    await conditional.click();

    await conditional.click({ button: 'right' });
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({ state: 'visible', timeout: 10_000 });

    // The picker is the contract between this form and the resolver: assert the whole variable set is offered,
    // then insert one of them. A renamed resolver variable shows up here and nowhere else.
    for (const group of PICKER_GROUPS) {
      const groupItem = menu.getByRole('menuitem', { name: group.label }).first();
      await expect(groupItem, `picker group ${group.label}`).toBeVisible();
      await groupItem.hover();
      for (const variable of group.variables) {
        await expect(
          menu.getByRole('menuitem', { name: variable, exact: true }).first(),
          `picker variable ${variable} of group ${group.label}`
        ).toBeVisible();
      }
    }

    await menu.getByRole('menuitem', { name: PICKER_GROUPS[1].label }).first().hover();
    await menu.getByRole('menuitem', { name: RULE.variable, exact: true }).first().click();
    await expect(conditional).toHaveValue(RULE.variable);

    // insertVariable() restores the caret behind the inserted name, so the comparison simply follows it.
    await conditional.pressSequentially(RULE.comparison);
    await expect(conditional).toHaveValue(RULE.variable + RULE.comparison);

    // A new rule is POSTed; updateEntity() only uses PUT once the entity has an id.
    const savePromise = page.waitForResponse(
      (response) =>
        response.url().endsWith('/api/gtnetmessageanswer') && ['POST', 'PUT'].includes(response.request().method())
    );
    await dialog.getByRole('button', { name: GTNET_RX.save }).click();
    const saveResponse = await savePromise;
    expect(saveResponse.ok(), `${saveResponse.status()} ${await saveResponse.text()}`).toBeTruthy();
    await dialog.waitFor({ state: 'hidden', timeout: 15_000 });

    const answers = await readAnswers(page);
    const created = answers.find((answer) => answer.priority === Number(RULE.priority));
    expect(created, 'the created auto-answer rule').toBeTruthy();
    expect(created!.responseMsgConditional).toBe(RULE.variable + RULE.comparison);
    expect(created!.responseMsgMessage).toBe(RULE.message);
    expect(created!.waitDaysApply).toBe(Number(RULE.waitDaysApply));

    // The two code columns travel as their protocol byte, not as the enum name the form offers, so the codes are
    // asserted by reopening the rule: that covers both directions of the mapping - the byte the backend serialized
    // has to select the option the dialog was given - without pinning the numeric constants in the spec.
    const reopened = await openEditDialog(page);
    await expect(reopened.locator('select#requestMsgCode')).toHaveValue(RULE.requestMsgCode);
    await expect(reopened.locator('select#responseMsgCode')).toHaveValue(RULE.responseMsgCode);
    await expect(reopened.locator('#responseMsgConditional')).toHaveValue(RULE.variable + RULE.comparison);
    await expect(reopened.locator('#priority')).toHaveValue(RULE.priority);
  });

  test('the same request/response/priority triple is refused a second time', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');
    const before = await readAnswers(page);
    expect(before.some((answer) => answer.priority === Number(RULE.priority))).toBe(true);

    // Unique key Unique_GtNetMessageAnswer (response_msg_code, priority, request_msg_code). The status is not
    // pinned: what matters is that the write is rejected and no second row appears.
    const response = await page.request.post('/api/gtnetmessageanswer', {
      headers: await authHeaders(page),
      data: {
        requestMsgCode: RULE.requestMsgCode,
        responseMsgCode: RULE.responseMsgCode,
        priority: Number(RULE.priority),
        responseMsgConditional: null,
        responseMsgMessage: 'duplicate',
        waitDaysApply: 0
      },
      failOnStatusCode: false
    });
    expect(response.ok(), 'a duplicate (response, priority, request) triple must be refused').toBeFalsy();

    const after = await readAnswers(page);
    expect(after.filter((answer) => answer.priority === Number(RULE.priority))).toHaveLength(1);
  });
});
