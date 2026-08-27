import { expect, Page, test } from '@playwright/test';
import {
  authHeaders,
  login,
  openSetup,
  PEER_A_FRONTEND,
  PEER_A_OWN,
  PEER_B_FRONTEND,
  PEER_B_OWN,
  readGTNet
} from './peer.helpers';

const REQUEST_CODE = 'GT_NET_DATA_REQUEST_SEL_RR_C';
const ACCEPT_CODE = 'GT_NET_DATA_REQUEST_ACCEPT_S';
const ENTITY_KIND = 'INTEGRATION_STREAM';

/**
 * The two-sided data-request approval. With no auto-answer rule installed the request has neither a matching rule nor
 * a handler default, so the answering peer only acknowledges it and stores it for an administrator - and that
 * administrator answers it from the message tree in the expanded setup row.
 *
 * The spec cleans up before it acts, not after: a previous run that failed halfway leaves an unanswered
 * request-response message, and such a row blocks the deletion of the peer entry for every later spec.
 */
test('a data request awaits manual approval and the approval creates the entity configuration', async ({
  browser,
  page
}) => {
  const peerBContext = await browser.newContext({ baseURL: PEER_B_FRONTEND });
  const peerB = await peerBContext.newPage();
  try {
    await login(page, PEER_A_FRONTEND);
    await login(peerB, PEER_B_FRONTEND);
    await removeLeftoverDataRequests(page, PEER_B_OWN!);
    await removeLeftoverDataRequests(peerB, PEER_A_OWN!);

    await sendDataRequest(page);
    await approveOnPeerB(peerB);

    await expect
      .poll(async () => {
        const remote = (await readGTNet(page)).gtNetList.find((entry: any) => entry.domainRemoteName === PEER_B_OWN);
        return (remote?.gtNetEntities ?? []).some((entity: any) => entity.entityKind === ENTITY_KIND);
      })
      .toBe(true);
  } finally {
    await peerBContext.close();
  }
});

/** Sends the data request from the setup row of the other peer, asserting the option set the dialog offers first. */
async function sendDataRequest(page: Page): Promise<void> {
  await openSetup(page, PEER_A_FRONTEND);
  const row = page.locator('.data-container-full').first().locator('tbody tr', { hasText: PEER_B_OWN! }).first();
  await row.click();
  await row.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: /Send message|Nachricht senden|GT_NET_MESSAGE_SEND/ })
    .click();
  const dialog = page.locator('.p-dialog:visible');

  // The dialog filters its options through getAvailableMessageCodes(); that filter is the only guard against sending
  // a code the peer cannot answer, so the offered set is asserted rather than assumed.
  const offered = await dialog
    .locator('#messageCode option')
    .evaluateAll((options) => options.map((option) => (option as HTMLOptionElement).value).filter((value) => !!value));
  expect(offered).toContain(REQUEST_CODE);
  expect(offered.every((code) => code.endsWith('_C'))).toBeTruthy();

  await dialog.locator('#messageCode').selectOption(REQUEST_CODE);
  await selectEntityKind(page, dialog);
  const submitted = page.waitForResponse((response) => response.url().endsWith('/api/gtnet/submitmsg'));
  await dialog.locator('button[type="submit"]').click();
  expect((await submitted).ok()).toBeTruthy();
  await dialog.waitFor({ state: 'hidden' });
}

/** entityKinds is a multi-select whose options come from the backend exchange-kind registry. */
async function selectEntityKind(page: Page, dialog: ReturnType<Page['locator']>): Promise<void> {
  const field = dialog.locator('#entityKinds');
  await field.waitFor({ state: 'visible' });
  await field.click();
  const option = page.locator('.p-multiselect-overlay:visible, .p-multiselect-panel:visible').first();
  await option.getByText(ENTITY_KIND, { exact: false }).first().click();
  await page.keyboard.press('Escape');
}

/** Answers the pending request from the message tree of the expanded setup row. */
async function approveOnPeerB(peerB: Page): Promise<void> {
  await openSetup(peerB, PEER_B_FRONTEND);
  const row = peerB.locator('.data-container-full').first().locator('tbody tr', { hasText: PEER_A_OWN! }).first();
  await row.locator('a[href="#"]').first().click();
  const tree = peerB.locator('gtnet-message-treetable').first();
  await tree.waitFor({ state: 'visible' });
  const pending = tree.locator('tbody tr', { hasText: REQUEST_CODE }).last();
  await pending.click();
  await pending.click({ button: 'right' });
  await peerB
    .locator('[role="menu"]:visible')
    .getByRole('menuitem', { name: /Reply|Antworten|REPLY/ })
    .click();
  const dialog = peerB.locator('.p-dialog:visible');
  await dialog.locator('#messageCode').selectOption(ACCEPT_CODE);
  const submitted = peerB.waitForResponse((response) => response.url().endsWith('/api/gtnet/submitmsg'));
  await dialog.locator('button[type="submit"]').click();
  expect((await submitted).ok()).toBeTruthy();
  await dialog.waitFor({ state: 'hidden' });
}

/**
 * Deletes the data-request messages of an earlier run. A message the protocol still considers pending cannot be
 * deleted, so the batch call is allowed to fail; the run then simply adds one more request to the thread.
 */
async function removeLeftoverDataRequests(page: Page, remoteDomain: string): Promise<void> {
  const headers = await authHeaders(page);
  const remote = (await readGTNet(page)).gtNetList.find((entry: any) => entry.domainRemoteName === remoteDomain);
  if (!remote) {
    return;
  }
  const response = await page.request.get(`/api/gtnet/messages/${remote.idGtNet}`, { headers });
  if (!response.ok()) {
    return;
  }
  const ids = ((await response.json()) as any[])
    .filter((message) => message.messageCode === REQUEST_CODE || message.messageCode === ACCEPT_CODE)
    .map((message) => message.idGtNetMessage);
  if (ids.length) {
    await page.request.post('/api/gtnet/deletemessagebatch', { headers, data: ids });
  }
}
