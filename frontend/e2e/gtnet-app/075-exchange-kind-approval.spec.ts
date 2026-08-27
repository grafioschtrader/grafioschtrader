import { expect, Page, test } from '@playwright/test';
import {
  authHeaders,
  login,
  openSetup,
  PEER_A_FRONTEND,
  PEER_A_OWN,
  PEER_B_FRONTEND,
  PEER_B_OWN,
  readGTNet,
  sendMessageFromRow,
  waitingTasks
} from './peer.helpers';

const REQUEST_CODE = 'GT_NET_DATA_REQUEST_SEL_RR_C';
const ACCEPT_CODE = 'GT_NET_DATA_REQUEST_ACCEPT_S';
const ENTITY_KIND = 'HISTORICAL_PRICES';

/**
 * The one browser flow of the application peers. Two full Grafioschtrader peers are expensive, so the payload codes
 * are covered by the client-only suite and the browser share stays at the piece that only a browser can show: an
 * administrator approving an exchange kind on one peer, and the delivery that approval sets in motion on the other.
 *
 * The delivery is asserted on the queued task row, not by waiting for it: both peers run with the background worker
 * disabled, so a GTNET_EXCHANGE_SYNC row stays queued until something runs it, and that is exactly what makes the
 * assertion deterministic.
 */
test('approving an exchange kind queues the sync it triggers', async ({ browser, page }) => {
  const peerBContext = await browser.newContext({ baseURL: PEER_B_FRONTEND });
  const peerB = await peerBContext.newPage();
  try {
    await login(page, PEER_A_FRONTEND);
    await login(peerB, PEER_B_FRONTEND);
    await installAcceptRule(peerB);
    const tasksBefore = (await waitingTasks(page, 'GTNET_EXCHANGE_SYNC')).length;

    await sendMessageFromRow(page, PEER_A_FRONTEND, PEER_B_OWN!, REQUEST_CODE);

    // The approval writes the entity configuration on the requesting side ...
    await expect
      .poll(async () => {
        const remote = (await readGTNet(page)).gtNetList.find((entry: any) => entry.domainRemoteName === PEER_B_OWN);
        return (remote?.gtNetEntities ?? []).some((entity: any) => entity.entityKind === ENTITY_KIND);
      })
      .toBe(true);

    // ... and the setup table of the answering peer shows the requester as a configured counterpart.
    await openSetup(peerB, PEER_B_FRONTEND);
    await expect(peerB.locator('.data-container-full').first()).toContainText(PEER_A_OWN!);

    // Mutual acceptance queues an exchange sync rather than running one inline.
    await expect
      .poll(async () => (await waitingTasks(page, 'GTNET_EXCHANGE_SYNC')).length)
      .toBeGreaterThan(tasksBefore);
    const queued = await waitingTasks(page, 'GTNET_EXCHANGE_SYNC');
    const newest = queued.reduce((a, b) => (a.idTaskDataChange > b.idTaskDataChange ? a : b));
    const run = await page.request.post(`/api/gtnet-peer-test/tasks/${newest.idTaskDataChange}/run`, {
      headers: await authHeaders(page),
      data: null
    });
    expect(run.ok(), await run.text()).toBeTruthy();
    expect(await run.text()).toContain('PROG_PROCESSED');
  } finally {
    await removeAcceptRule(peerB);
    await peerBContext.close();
  }
});

/** An auto-answer rule is what turns the request into an approval without an administrator sitting in front of it. */
async function installAcceptRule(peerB: Page): Promise<void> {
  await removeAcceptRule(peerB);
  const headers = await authHeaders(peerB);
  const response = await peerB.request.post('/api/gtnetmessageanswer', {
    headers,
    data: { requestMsgCode: REQUEST_CODE, responseMsgCode: ACCEPT_CODE, priority: 51, waitDaysApply: 0 }
  });
  expect(response.ok(), await response.text()).toBeTruthy();
}

async function removeAcceptRule(peerB: Page): Promise<void> {
  const headers = await authHeaders(peerB);
  const response = await peerB.request.get('/api/gtnetmessageanswer', { headers });
  if (!response.ok()) {
    return;
  }
  for (const rule of (await response.json()) as any[]) {
    if (rule.priority === 51) {
      await peerB.request.delete(`/api/gtnetmessageanswer/${rule.idGtNetMessageAnswer}`, { headers });
    }
  }
}
