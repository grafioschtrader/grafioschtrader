import { expect, test } from '@playwright/test';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

import { libHelpers } from './helpers';
import { clickShowMenuItem, GTNET_RX, openGTNetSetup, readGTNet, visibleDialog } from './gtnet.helpers';

/**
 * The GTNet data export and its re-import: the `GT_NET_EXPORT` / `GT_NET_IMPORT` entries of the setup table's
 * show menu.
 *
 * This is the baseline-restore path of §3.2 — the way an instance's GTNet configuration is carried from one
 * database to another. It only reaches the library host at all because the resource behind it now lives in
 * `grafiosch-server-base`: it used to sit in `grafioschtrader-server`, so the standalone host answered 404 on
 * both menu entries while routing the UI that calls them.
 *
 * Three details that shape the spec:
 *
 *  - **The two entries are `showMenu`, not context-menu items.** `prepareShowMenu()` hands them to
 *    `ActivePanelService`, which routes them to the top menu bar's "View" / "Ansicht" menu. A right click on a
 *    row only ever yields the CRUD items.
 *  - **The export is a real browser download** (`saveAs`), so it is captured through `page.waitForEvent`.
 *  - **The import replaces the GTNet tables wholesale** — `DELETE` for every table, then the `INSERT`s — and the
 *    uploaded file never carries `globalparameters`. `g.gnet.my.entry.id` is therefore re-resolved by
 *    `importGTNetConfig` itself: it remembers the own `domainRemoteName` before the first `DELETE` and points the
 *    parameter back at whichever imported row carries that domain, falling back to matching an imported domain
 *    against this machine's interfaces. On this round trip the ids are unchanged, so the value stays the same —
 *    what the assertions below check is that the identity still resolves. That is why the captured baseline has to
 *    contain a valid own entry, and why this spec runs after `040`.
 *
 * The negative case is the header check in `importGTNetConfig`: a file that does not begin with this host's
 * marker is refused with `gt.gtnet.import.invalid.header`, so an application export can never be dropped into
 * a library instance whose tables it does not match.
 *
 * Re-runnable: it writes only into the OS temp directory and re-imports what this very database exported, so
 * running it twice leaves the same rows behind.
 */

test.describe.serial('GTNet data export and import', () => {
  const exportFile = path.join(os.tmpdir(), `gtnet-export-${process.pid}.sql`);

  test.afterAll(() => {
    fs.rmSync(exportFile, { force: true });
  });

  test('exports the GTNet tables and re-imports them unchanged', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');

    const before = await readGTNet(page);
    expect(before.gtNetMyEntryId, 'own entry — 040 has to run first').not.toBeNull();
    expect(
      before.gtNetList.length,
      'at least the own entry and the peer of 045, so the round trip covers more than one row'
    ).toBeGreaterThan(1);

    const container = await openGTNetSetup(page);
    const downloadPromise = page.waitForEvent('download', { timeout: 30_000 });
    await clickShowMenuItem(page, container, GTNET_RX.gtNetExport);
    const download = await downloadPromise;
    await download.saveAs(exportFile);

    const sql = fs.readFileSync(exportFile, 'utf-8');
    expect(
      sql.startsWith('-- GTNET_EXPORT_V1_BASE'),
      `a library host must produce the base marker, got: ${sql.slice(0, 40)}`
    ).toBe(true);
    for (const gtNet of before.gtNetList) {
      expect(sql, `export contains ${gtNet.domainRemoteName}`).toContain(gtNet.domainRemoteName);
    }

    // Re-import the very file just produced: every table is deleted and refilled with identical rows, so the
    // state afterwards has to match the state before, ids included.
    await clickShowMenuItem(page, await openGTNetSetup(page), GTNET_RX.gtNetImport);
    const dialog = await visibleDialog(page);
    await dialog.locator('input[type="file"]#fileToUpload').setInputFiles(exportFile);

    const importPromise = page.waitForResponse(
      (response) => response.url().endsWith('/api/gtnetdataexport/import') && response.request().method() === 'POST'
    );
    await dialog.getByRole('button', { name: GTNET_RX.upload }).click();
    const importResponse = await importPromise;
    expect(importResponse.ok(), `${importResponse.status()} ${await importResponse.text()}`).toBeTruthy();
    await dialog.waitFor({ state: 'hidden', timeout: 15_000 });

    const after = await readGTNet(page);
    expect(after.gtNetMyEntryId).toBe(before.gtNetMyEntryId);
    expect(after.gtNetList.map((gtNet) => gtNet.idGtNet).sort()).toEqual(
      before.gtNetList.map((gtNet) => gtNet.idGtNet).sort()
    );
    expect(after.gtNetList.map((gtNet) => gtNet.domainRemoteName).sort()).toEqual(
      before.gtNetList.map((gtNet) => gtNet.domainRemoteName).sort()
    );
  });

  test('an import file without the expected marker is refused', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, 'admin');
    const before = await readGTNet(page);

    // The statements are valid and would run; only the missing marker stops them. Asserted at REST level, so
    // the check is on importGTNetConfig itself rather than on whatever the upload dialog reports.
    const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
    const response = await page.request.post('/api/gtnetdataexport/import', {
      headers: { 'x-auth-token': token! },
      multipart: {
        file: {
          name: 'not-a-gtnet-export.sql',
          mimeType: 'application/sql',
          buffer: Buffer.from('DELETE FROM `gt_net_message_attempt`;\n', 'utf-8')
        }
      },
      failOnStatusCode: false
    });
    expect(response.ok(), 'an import without the header marker must be refused').toBeFalsy();

    const after = await readGTNet(page);
    expect(after.gtNetList).toHaveLength(before.gtNetList.length);
  });
});
