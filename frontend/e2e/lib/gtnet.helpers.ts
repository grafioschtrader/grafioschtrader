import { expect, Locator, Page } from '@playwright/test';

/**
 * Helpers shared by the GTNet specs of the portable library suite.
 *
 * They live beside the specs and never import from `e2e/`, so the whole GTNet browser coverage moves with
 * `src/app/lib` when the library is extracted — the same one-way rule `e2e/lib/helpers.ts` states.
 *
 * Two properties of the GTNet UI shape almost every helper here:
 *
 *  - **Nothing carries a `data-*` hook.** Dynamic-form controls are addressed by `#<fieldName>` because every
 *    input renders with `[id]="config.field"`; everything else is structural (`.p-dialog:visible`,
 *    `.data-container-full`, `[role="menu"]`, `editable-table tbody tr`).
 *  - **The host renders in the language of the signed-in user.** `020-nickname-lang-edit` switches the library
 *    `admin` to `de-CH`, so a full run is German while a lone spec against a fresh `grafiosch_t` is English.
 *    Every text selector therefore matches German, English and the raw NLS key.
 */

/**
 * Bilingual (plus raw-key) matchers for the texts the GTNet specs touch.
 *
 * The CRUD entries of a `TableCrudSupportMenu` are built as `CREATE|<ENTITY>...`, `EDIT_RECORD|<ENTITY>...`
 * and `DELETE_RECORD|<ENTITY>`, which `TranslateHelper` renders as "Create <Entity>..." / "Erstellen
 * <Entity>...". The trailing separator in {@link editRecord} is what keeps it from also matching the bare
 * "Edit" of the top menu bar.
 */
export const GTNET_RX = {
  netAndMessage: /^(GT Net and Message|GTNet und Nachrichten|GT_NET_NET_AND_MESSAGE)$/,
  messageAnswerNode: /^(Automatic message|Automatische Nachricht|GT_NET_MESSAGE_ANSWER)$/,
  save: /^(Save|Speichern|SAVE)$/,
  upload: /^(Upload|Hochladen|UPLOAD)$/,
  createRecord: /^(Create|Erstellen|CREATE)\s/,
  editRecord: /^(Edit|Bearbeiten|EDIT_RECORD)\s/,
  deleteRecord: /^(Delete|Löschen|DELETE_RECORD)\s/,
  view: /^(View|Ansicht|SHOW)$/,
  gtNetExport: /^(Export GTNet data|GTNet-Daten exportieren|GT_NET_EXPORT)$/,
  gtNetImport: /^(Import GTNet data|GTNet-Daten importieren|GT_NET_IMPORT)$/,
  commRequirement:
    /communication with other participants|Kommunikation mit anderen Teilnehmern|GT_NET_COMM_REQUIREMENT/,
  yes: /^(Yes|Ja|YES)$/
};

/** How the library host addresses its GTNet screens. Mirrors `BaseSettings` / `GrafioschSettings`. */
export const GTNET_ROUTES = {
  setup: '/mainview/gtnettabmenu/gtnetsetup',
  messageAnswer: '/mainview/gtnetmessageanswer'
};

/**
 * The URL under which this instance reaches itself, exported by `scripts/e2e-test.mjs`.
 *
 * A GTNet own entry is only recognised as *this* instance when its `domainRemoteName` resolves to a
 * **non-loopback** interface — `GTNetJpaRepositoryImpl.isDomainNameThisMachine` skips every loopback one — and
 * the very first `gt_net` insert additionally probes that URL from the instance itself. So neither `localhost`
 * nor a hard-coded address works, and the value has to come from the runner.
 *
 * Throws rather than skipping: a silent skip would hide a broken harness, and the fixture readers of this
 * repository fail loudly for the same reason.
 */
export function ownPeerUrl(): string {
  const url = process.env.GTNET_OWN_URL;
  if (!url) {
    throw new Error(
      [
        'GTNET_OWN_URL is not set. The e2e runner resolves this backend own non-loopback address after it is up',
        'and exports it. To run this spec by hand, resolve the address once and export it:',
        '  node scripts/gtnet-peer-address.mjs 8081',
        '  GTNET_OWN_URL=http://<that address> npx playwright test <spec> --config=playwright.lib.config.ts --no-deps'
      ].join('\n')
    );
  }
  return url;
}

/** JWT header the library REST endpoints expect; the token is written to sessionStorage on login. */
export async function authHeaders(page: Page): Promise<{ [key: string]: string }> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token! };
}

/** One `gt_net_entity` row of `GET /api/gtnet/gtnetwithmessage`, reduced to what the specs assert on. */
export interface ApiGTNetEntity {
  idGtNetEntity: number;
  entityKind: number;
  acceptRequest: number | string;
  serverState: number | string;
  maxLimit: number;
}

export interface ApiGTNet {
  idGtNet: number;
  domainRemoteName: string;
  timeZone: string;
  spreadCapability: boolean;
  dailyRequestLimit: number;
  serverBusy: boolean;
  allowServerCreation: boolean;
  gtNetEntities: ApiGTNetEntity[];
  gtNetConfig: { connectionTimeout: number | null; authorizedRemoteEntry: boolean } | null;
}

/** `ExchangeKindTypeInfo` — what the host has registered, which decides the batch table rows. */
export interface ApiExchangeKind {
  name: string;
  value: number;
  supportsPush: boolean;
  syncable: boolean;
}

export interface ApiGTNetWithMessages {
  gtNetList: ApiGTNet[];
  gtNetMyEntryId: number | null;
  exchangeKindTypes: ApiExchangeKind[];
}

export async function readGTNet(page: Page): Promise<ApiGTNetWithMessages> {
  const response = await page.request.get('/api/gtnet/gtnetwithmessage', { headers: await authHeaders(page) });
  expect(response.ok(), `loading GTNet entries: ${response.status()} ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<ApiGTNetWithMessages>;
}

/** Deletes one GTNet entry through the same endpoint the setup table uses. */
export async function deleteGTNet(page: Page, idGtNet: number): Promise<void> {
  const response = await page.request.delete(`/api/gtnet/${idGtNet}`, { headers: await authHeaders(page) });
  expect(response.ok(), `deleting GTNet ${idGtNet}: ${response.status()} ${await response.text()}`).toBeTruthy();
}

/**
 * Removes every GTNet entry except the own one, so a spec that creates peers can be rerun.
 *
 * The own entry is deliberately kept: `deleteGTNet` refuses it with `gt.gtnet.cannot.delete.own.entry`, and
 * `domainRemoteName` is `@PropertyOnlyCreation`, so an entry deleted here could only be recreated, never
 * repaired — the reset level that also clears `g.gnet.my.entry.id` is recreating the database.
 */
export async function deleteRemoteGTNets(page: Page): Promise<void> {
  const data = await readGTNet(page);
  for (const gtNet of data.gtNetList) {
    if (gtNet.idGtNet !== data.gtNetMyEntryId) {
      await deleteGTNet(page, gtNet.idGtNet);
    }
  }
}

/** Opens the GTNet setup table and waits until its table has rendered. */
export async function openGTNetSetup(page: Page): Promise<Locator> {
  await page.goto(GTNET_ROUTES.setup);
  const container = page.locator('.data-container-full').first();
  await container.waitFor({ state: 'visible', timeout: 15_000 });
  return container;
}

/**
 * Opens the CRUD context menu of a table, optionally after selecting one row.
 *
 * The left click is not decoration. `p-contextMenu` targets the whole container, and a right click only sets
 * PrimeNG's `contextMenuSelection` — which `configurable-table` does not bind. `selectedEntity` is updated by
 * the ordinary `[(selection)]`, and only the container's `(click)` reaches
 * `TableCrudSupportMenu.resetMenu(selectedEntity)`, which is what rebuilds `contextMenuItems`. Without the left
 * click the menu therefore still carries the items of the previous selection, and with no selection at all it
 * offers `CREATE` alone — `EDIT_RECORD` and `DELETE_RECORD` are only added when an entity is selected.
 *
 * @param row the row to select first; omit to open the menu with nothing selected (create only)
 */
export async function openCrudMenu(page: Page, container: Locator, row?: Locator): Promise<Locator> {
  const target = row ?? container;
  await target.click();
  await target.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 10_000 });
  return menu;
}

/**
 * Picks one entry of a table's *show* menu, which lives in the top menu bar rather than in the row context menu.
 *
 * `prepareShowMenu()` hands `GT_NET_EXPORT` / `GT_NET_IMPORT` to `ActivePanelService` as `showMenu`, and
 * `ActivePanelService` routes that to `TopMenuTypes.SHOW` — labelled "View" / "Ansicht". A right click on a row
 * only ever yields the CRUD items, so the panel is activated by a plain click and the root entry opened first.
 *
 * Unlike the context menu this is a `p-menubar`, which renders **no `[role="menu"]` element at all**: its
 * submenu entries are `[role="menuitem"]` nested inside the root one and are in the DOM from the start. Waiting
 * for a popup would therefore hang forever — the entry is addressed directly and Playwright waits for it to
 * become visible.
 */
export async function clickShowMenuItem(page: Page, container: Locator, item: RegExp): Promise<void> {
  await container.click();
  await page.getByRole('menuitem', { name: GTNET_RX.view }).first().click({ timeout: 10_000 });
  await page.getByRole('menuitem', { name: item }).first().click({ timeout: 10_000 });
}

/** The single visible Optimus dialog. */
export async function visibleDialog(page: Page): Promise<Locator> {
  const dialog = page.locator('.p-dialog:visible').first();
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  return dialog;
}

/**
 * Locates a hand-written `<p-button>` by its label — **not** by its accessible name.
 *
 * A `<p-button>` that projects an `<i class="pi …" pButtonIcon>` renders that icon as a font glyph whose
 * `::before` content Chromium folds into the accessible name, so `getByRole('button', { name: /^Speichern$/ })`
 * matches nothing while the button plainly reads "Speichern". `hasText` compares `textContent`, which the
 * pseudo-element does not reach, so an anchored regex works there.
 *
 * The dynamic-form submit button (`form-pbutton`) carries no such icon and is still matched by role and name,
 * the way the other library specs do it.
 */
export function labelledButton(scope: Locator, label: RegExp): Locator {
  return scope.locator('p-button button', { hasText: label });
}

/**
 * Confirms an Optimus confirm dialog. An unfiltered `.first()` of `[role="alertdialog"]` resolves to a
 * permanently hidden host, so the `:visible` filter is required.
 */
export async function acceptConfirmDialog(page: Page): Promise<void> {
  const confirm = page.locator('[role="alertdialog"]:visible').first();
  await confirm.waitFor({ state: 'visible', timeout: 10_000 });
  await confirm.getByRole('button', { name: GTNET_RX.yes }).click();
}
