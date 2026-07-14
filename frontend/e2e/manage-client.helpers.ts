import {expect, Locator, Page} from '@playwright/test';

/**
 * Shared UI helpers for the manage-client feature specs (22-manage-client / 26-share-read-access,
 * issues #200/#201). The advisor/owner user 'alledit' runs a German UI (de-CH) and users created by
 * the feature inherit that locale, while 'e2euser' runs English — therefore every text selector
 * matches both the German and English strings (same convention as 12-correlation-matrix.spec.ts).
 */
// Each menu regex also matches the raw NLS key. The menu bar translates its labels once in the
// menubar component's ngOnInit and never retries; after switchAndReload's hard window.location
// .reload() the menu bar can rebuild before the translation JSON has reloaded, so ngx-translate
// returns the bare keys (MANAGE_CLIENT, BACK_TO_MY_CLIENT, …). Matching both keeps the tests robust
// to that pre-existing post-reload race (the tree is translated because it builds later).
export const RX = {
  /** Top-level Client menu in the main menu bar (NLS MANAGE_CLIENT). */
  clientMenuRoot: /^(Client|Klient|MANAGE_CLIENT)$/,
  createClientItem: /(Create client|Klient erstellen|CREATE_CLIENT)/i,
  switchToClientItem: /(Switch to client|Zu Klient wechseln|SWITCH_TO_CLIENT)/i,
  shareReadAccessItem: /(Share read access|Lesezugriff teilen|SHARE_READ_ACCESS)/i,
  sharedViewersItem: /(Shared viewers|Geteilte Leser|MANAGE_SHARED_VIEWERS)/i,
  backToMyItem: /(Back to my|Zur.ck zu mir|BACK_TO_MY_CLIENT)/i,
  myClientsHeader: /(My clients|Meine Klienten)/i,
  sharedViewersHeader: /(Who can read my portfolio|Wer mein Portfolio lesen kann)/i,
  shareDialogHeader: /(Share read access|Lesezugriff teilen)/i,
  clientCreatedToast: /(client has been created|Klient wurde erstellt)/i,
  sharedToast: /(has been shared|wurde geteilt)/i,
  createPortfolioItem: /(Create\s+Portfolio|Erstellen\s+Portfolio)/i,
  checkExistenceBtn: /(Check e-mail|E-Mail pr.fen)/i,
  clientCreatedMailSubject: /(A portfolio has been created for you|Es wurde ein Portfolio f.r Sie erstellt)/i,
  shareInvitedMailSubject: /(Read access to a portfolio has been shared|Lesezugriff auf ein Portfolio wurde mit Ihnen geteilt)/i,
  viewerKind: /(View-only login|Nur-Lese-Zugang)/i,
  grantKind: /(Registered user|Registrierter Benutzer)/i,
};

/**
 * Success/warning toasts are rendered by ngx-toastr into a body-level `#toast-container`, not a
 * PrimeNG `p-toast`. Asserts a toast whose text matches `messageRx` appears.
 */
export async function expectToast(page: Page, messageRx: RegExp): Promise<void> {
  await expect(page.locator('#toast-container').getByText(messageRx).first())
    .toBeVisible({timeout: 15_000});
}

/** The top-level Client menu entry (label is exactly "Client"/"Klient"). */
export function clientMenuRoot(page: Page): Locator {
  return page.locator('p-menubar').getByText(RX.clientMenuRoot).first();
}

/** Opens the top-level Client menu so its sub-items become visible. */
export async function openClientMenu(page: Page): Promise<void> {
  const root = clientMenuRoot(page);
  await root.waitFor({state: 'visible', timeout: 15_000});
  await root.click();
}

/** Closes any open menu overlay. */
export async function closeMenus(page: Page): Promise<void> {
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);
}

/** Opens the Client menu and clicks the sub-item matching the given bilingual regex. */
export async function clickClientMenuItem(page: Page, itemRx: RegExp): Promise<void> {
  await openClientMenu(page);
  const item = page.locator('p-menubar').getByText(itemRx).first();
  await item.waitFor({state: 'visible', timeout: 5_000});
  await item.click();
}

/**
 * Opens one of the manage-client dialogs via the Client menu and waits for it (plus a settle pause
 * for the dialog's initial data request).
 */
export async function openManageClientDialog(page: Page, itemRx: RegExp, headerRx: RegExp): Promise<Locator> {
  await clickClientMenuItem(page, itemRx);
  const dialog = page.locator('.p-dialog').filter({hasText: headerRx}).first();
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  await page.waitForTimeout(800);
  return dialog;
}

/** Closes a (closable) dialog via ESC and waits until it is gone. */
export async function closeDialog(page: Page, dialog: Locator): Promise<void> {
  await page.keyboard.press('Escape');
  await dialog.waitFor({state: 'hidden', timeout: 5_000});
}

/** Fills a dynamic-form input and dispatches the input event so Angular picks the value up. */
export async function fillInput(dialog: Locator, selector: string, value: string): Promise<void> {
  const input = dialog.locator(selector);
  await input.waitFor({state: 'visible', timeout: 10_000});
  await input.click();
  await input.fill(value);
  await input.dispatchEvent('input');
}

/**
 * The root node of the portfolio tree in the main view. Its label carries the tenant name, so it
 * identifies which tenant the session is currently in (it changes after a tenant switch).
 */
export function tenantRootNode(page: Page): Locator {
  return page.locator('.p-tree-node-content').first();
}

/**
 * Asserts that the main menu bar carries no Client menu at all — the state for a read-only login
 * (client/viewer) that is not inside a managed client tenant.
 */
export async function expectNoClientMenu(page: Page): Promise<void> {
  const menubar = page.locator('p-menubar');
  await menubar.waitFor({state: 'visible', timeout: 15_000});
  await expect(menubar.getByText(RX.clientMenuRoot)).toHaveCount(0);
}

/**
 * Proves the backend hard write-block (TenantReadOnlyFilter): the portfolio tree context menu is
 * not gated by read-only mode, so the create-portfolio dialog can be opened and submitted — but the
 * POST must be rejected and no portfolio node may appear.
 */
export async function attemptCreatePortfolioExpectRejected(page: Page, name: string): Promise<void> {
  await tenantRootNode(page).click({button: 'right'});
  const menuItem = page.locator('p-contextmenu').getByText(RX.createPortfolioItem).first();
  await menuItem.waitFor({state: 'visible', timeout: 5_000});
  await menuItem.click();

  const dialog = page.locator('.p-dialog').first();
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  await fillInput(dialog, '#name', name);
  await dialog.locator('button[type="submit"]').click();
  await page.waitForTimeout(2_500);

  await expect(page.locator('.p-tree-node-content', {hasText: name})).toHaveCount(0);

  if (await dialog.isVisible().catch(() => false)) {
    await page.keyboard.press('Escape');
    await dialog.waitFor({state: 'hidden', timeout: 5_000}).catch(() => undefined);
  }
}
