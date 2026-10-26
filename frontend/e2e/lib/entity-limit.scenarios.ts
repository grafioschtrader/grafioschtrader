import { expect, Locator, Page, Route, test } from '@playwright/test';

// This key/role pair is outside spec 190's fixture. Each persistence run removes only its own pair.
const OWNED_KEY = 'DAY_CUD|UserAccessToken|||';
const CREATE = /^(Create|Erstellen)\b/;
const EDIT = /^(Edit|Bearbeiten)\b/;
const DELETE = /^(Delete|Löschen)\b/;
const COL = { entity: 1, type: 3, relation: 5, count: 6, owner: 7, role: 8, user: 9, value: 10 };

interface Limit {
  idEntityLimit: number;
  keyId: string;
  entityName: string;
  limitType: string;
  relationEntityName?: string;
  countScope?: string;
  ownerScope?: string;
  idRole: number | null;
  idUser: number | null;
  limitValue: number;
}

interface Definition {
  keyId: string;
  entityName: string;
  labelKey: string;
  limitType: string;
  mandatoryAllRow: boolean;
}

interface State {
  headers: Record<string, string>;
  definitions: Definition[];
  roles: { key: number | string; value: string }[];
  translations: Record<string, string>[];
}

function nls(name: string): string {
  return name.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toUpperCase();
}

function label(state: State, key: string): RegExp {
  const values = state.translations.map((texts) => texts[key] ?? key);
  return new RegExp(`^\\s*(?:${values.map((v) => v.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})\\s*$`);
}

async function read<T>(page: Page, state: State, url: string): Promise<T> {
  const response = await page.request.get(url, { headers: state.headers });
  expect(response.ok(), `${url}: ${response.status()}`).toBeTruthy();
  return response.json();
}

async function openView(page: Page, route = 'entitylimit'): Promise<Locator> {
  await page.goto(`${page.url().split('/mainview')[0]}/mainview/${route}`);
  const table = page.locator('configurable-table').first();
  await expect(table).toBeVisible();
  return table;
}

async function menu(page: Page, target: Locator): Promise<Locator> {
  if (!(await target.getAttribute('class'))?.split(' ').includes('p-datatable-row-selected')) await target.click();
  await target.click({ button: 'right' });
  const result = page.locator('[role="menu"]:visible');
  await expect(result).toBeVisible();
  return result;
}

async function openDialog(page: Page, target: Locator, action = CREATE): Promise<Locator> {
  await (await menu(page, target)).getByRole('menuitem', { name: action }).first().click();
  const dialog = page.locator('entity-limit-edit .p-dialog');
  await expect(dialog).toBeVisible();
  return dialog;
}

async function close(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

async function number(dialog: Locator, value: number): Promise<void> {
  await dialog.locator('#limitValue input, input#limitValue').first().fill(String(value));
  await dialog.locator('#limitValue input, input#limitValue').first().blur();
}

function row(table: Locator, state: State, limit: Limit): Locator {
  let result = table.locator('tbody > tr');
  const parts: [number, string | RegExp][] = [
    [COL.entity, label(state, nls(limit.entityName))],
    [COL.type, label(state, `LIMIT_TYPE_${limit.limitType}`)],
    [COL.relation, limit.relationEntityName ? label(state, nls(limit.relationEntityName)) : /^\s*$/],
    [COL.count, limit.countScope ? label(state, `COUNT_SCOPE_${limit.countScope}`) : /^\s*$/],
    [COL.owner, limit.ownerScope ? label(state, `OWNER_SCOPE_${limit.ownerScope}`) : /^\s*$/],
    [COL.role, limit.idRole == null ? /^\s*$/ : label(state, state.roles.find((r) => +r.key === limit.idRole)!.value)],
    [COL.user, limit.idUser == null ? /^\s*$/ : new RegExp(`^\\s*${limit.idUser}\\s*$`)]
  ];
  for (const [column, text] of parts) {
    result = result.filter({ has: table.page().locator(`td:nth-child(${column})`).filter({ hasText: text }) });
  }
  return result;
}

async function selectKey(page: Page, dialog: Locator, state: State, definition: Definition): Promise<void> {
  await dialog.locator('#keyId').click();
  const entity = label(state, definition.labelKey)
    .source.replace(/^\^\\s\*/, '')
    .replace(/\\s\*\$$/, '');
  const type = label(state, `LIMIT_TYPE_${definition.limitType}`)
    .source.replace(/^\^\\s\*/, '')
    .replace(/\\s\*\$$/, '');
  const option = page
    .locator('.p-select-overlay:visible')
    .getByRole('option', { name: new RegExp(`${entity}.*${type}`) });
  await expect(option).toHaveCount(1);
  await option.click();
  await expect(dialog.locator('#limitTypeReadable')).not.toHaveValue('');
}

/** Holds a real response, including one whose browser request is cancelled when its owner closes. */
async function hold(page: Page, url: string) {
  let release!: () => void;
  let arrived!: () => void;
  let done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const received = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  let holding = false;
  const handler = async (route: Route): Promise<void> => {
    if (holding || route.request().method() !== 'GET') return route.fallback();
    holding = true;
    try {
      const response = await route.fetch();
      expect(response.ok()).toBeTruthy();
      arrived();
      await gate;
      try {
        await route.fulfill({ response });
      } catch (error) {
        if (route.request().failure()?.errorText !== 'net::ERR_ABORTED') throw error;
      }
    } finally {
      await page.unroute(url, handler);
      done();
    }
  };
  await page.route(url, handler);
  return { release, arrived: received, done: completed };
}

export function entityLimitScenarios(login: (page: Page, nickname: string) => Promise<unknown>): void {
  test.describe('entity-limit notification', () => {
    test.use({ viewport: { width: 1600, height: 1200 } });
    let state: State;
    test.beforeEach(async ({ page }) => {
      page.setDefaultTimeout(15_000);
      await login(page, 'admin');
      state = {
        headers: { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))! },
        definitions: [],
        roles: [],
        translations: []
      };
      [state.definitions, state.roles, ...state.translations] = await Promise.all([
        read<Definition[]>(page, state, '/api/entitylimit/keys'),
        read<State['roles']>(page, state, '/api/entitylimit/roles'),
        read<Record<string, string>>(page, state, '/api/globalparameters/properties/en'),
        read<Record<string, string>>(page, state, '/api/globalparameters/properties/de')
      ]);
    });

    test('delayed rows publish filters and keep mandatory defaults protected', async ({ page }) => {
      const limits = await read<Limit[]>(page, state, '/api/entitylimit');
      const mandatory = limits.find(
        (l) =>
          l.idRole == null &&
          l.idUser == null &&
          state.definitions.some((d) => d.keyId === l.keyId && d.mandatoryAllRow)
      )!;
      expect(mandatory).toBeTruthy();
      const pending = await hold(page, '**/api/entitylimit');
      try {
        const table = await openView(page);
        await pending.arrived;
        pending.release();
        await pending.done;
        const target = row(table, state, mandatory);
        await expect(target).toHaveCount(1);
        const actions = await menu(page, target);
        await expect(actions.getByRole('menuitem', { name: EDIT })).toHaveCount(1);
        await expect(actions.getByRole('menuitem', { name: DELETE })).toBeDisabled();
        await page.keyboard.press('Escape');
        // Select one entity in the filter, then hide the filter row through the main menu.
        const entityFilter = table.locator('thead p-multiselect').first();
        await entityFilter.click();
        await page.getByRole('option', { name: label(state, nls(mandatory.entityName)) }).click();
        await page.keyboard.press('Escape');
        await expect(table.locator('tbody > tr')).toHaveCount(
          limits.filter((l) => l.entityName === mandatory.entityName).length
        );
        await page.getByRole('menuitem', { name: /^(View|Ansicht)$/ }).click();
        await page.getByRole('menuitem', { name: label(state, 'ON_OFF_FILTER') }).click();
        await expect(entityFilter).toHaveCount(0);
        await expect(table.locator('tbody > tr')).toHaveCount(limits.length);
      } finally {
        pending.release();
      }
    });

    test('creates, cancels, edits and deletes its own role override after delayed reloads', async ({ page }) => {
      const role = state.roles.find((r) => r.value === 'ROLE_LIMITEDIT')!;
      const definition = state.definitions.find((d) => d.keyId === OWNED_KEY)!;
      expect(role).toBeTruthy();
      expect(definition).toBeTruthy();
      for (const old of await read<Limit[]>(page, state, '/api/entitylimit')) {
        if (old.keyId === OWNED_KEY && old.idRole === +role.key && old.idUser == null) {
          expect(
            (await page.request.delete(`/api/entitylimit/${old.idEntityLimit}`, { headers: state.headers })).ok()
          ).toBeTruthy();
        }
      }
      const table = await openView(page);
      let dialog = await openDialog(page, table.locator('h4'));
      await selectKey(page, dialog, state, definition);
      await dialog.locator('select#idRole').selectOption(String(role.key));
      await number(dialog, 761);
      const saved = page.waitForResponse(
        (r) => new URL(r.url()).pathname === '/api/entitylimit' && r.request().method() === 'POST'
      );
      await dialog.locator('button[type="submit"]').click();
      const response = await saved;
      expect(response.ok()).toBeTruthy();
      const created = (await response.json()) as Limit;
      await expect(dialog).toHaveCount(0);
      const target = row(table, state, created);
      await expect(target.locator(`td:nth-child(${COL.value})`)).toHaveText('761');
      dialog = await openDialog(page, table.locator('h4'));
      await selectKey(page, dialog, state, definition);
      await expect(dialog.locator(`select#idRole option[value="${role.key}"]`)).toHaveCount(0);
      await close(dialog);
      dialog = await openDialog(page, target, EDIT);
      await expect(dialog.locator('#keyId')).toHaveClass(/p-disabled/);
      await expect(dialog.locator('#limitValue input, input#limitValue').first()).toHaveValue('761');
      await number(dialog, 762);
      await close(dialog);
      await expect(target.locator(`td:nth-child(${COL.value})`)).toHaveText('761');
      dialog = await openDialog(page, target, EDIT);
      await expect(dialog.locator('#limitValue input, input#limitValue').first()).toHaveValue('761');
      await number(dialog, 762);
      const pending = await hold(page, '**/api/entitylimit');
      try {
        await dialog.locator('button[type="submit"]').click();
        await pending.arrived;
        await expect(target.locator(`td:nth-child(${COL.value})`)).toHaveText('761');
        pending.release();
        await pending.done;
        await expect(target.locator(`td:nth-child(${COL.value})`)).toHaveText('762');
        // The selected row and its menu must point to the reloaded entity.
        await (await menu(page, target)).getByRole('menuitem', { name: DELETE }).click();
        await page
          .getByRole('alertdialog')
          .getByRole('button', { name: /^(Yes|Ja)$/i })
          .click();
        await expect(target).toHaveCount(0);
        expect(
          (await read<Limit[]>(page, state, '/api/entitylimit')).some((l) => l.idEntityLimit === created.idEntityLimit)
        ).toBe(false);
      } finally {
        pending.release();
      }
    });

    test('closing during option loading cannot reset the next dialog session', async ({ page }) => {
      const table = await openView(page);
      const pending = await hold(page, '**/api/entitylimit/keys');
      try {
        let dialog = await openDialog(page, table.locator('h4'));
        await pending.arrived;
        await close(dialog);
        dialog = await openDialog(page, table.locator('h4'));
        await selectKey(
          page,
          dialog,
          state,
          state.definitions.find((d) => d.keyId === OWNED_KEY)!
        );
        await number(dialog, 763);
        pending.release();
        await pending.done;
        await expect(dialog.locator('#limitValue input, input#limitValue').first()).toHaveValue('763');
        await expect(dialog.locator('#limitTypeReadable')).not.toHaveValue('');
        await close(dialog);
      } finally {
        pending.release();
      }
    });

    test('user administration opens the same dialog with a user scope and no role field', async ({ page }) => {
      const table = await openView(page, 'useradmin');
      const users = await read<{ idUser: number; nickname: string; mostPrivilegedRole: string }[]>(
        page,
        state,
        '/api/useradmin'
      );
      const user = users.find((u) => u.mostPrivilegedRole === 'ROLE_LIMITEDIT')!;
      expect(user).toBeTruthy();
      const target = table
        .locator('tbody > tr')
        .filter({ has: page.getByRole('cell', { name: user.nickname, exact: true }) });
      const pending = await hold(page, '**/api/entitylimit/keys?idUser=*');
      try {
        const actions = await menu(page, target);
        await actions.getByRole('menuitem', { name: /^(Create|Erstellen).*Limit/i }).click();
        const dialog = page.locator('entity-limit-edit .p-dialog');
        await pending.arrived;
        await expect(dialog.locator('select#idRole')).toHaveCount(0);
        pending.release();
        await pending.done;
        const definitions = await read<Definition[]>(page, state, `/api/entitylimit/keys?idUser=${user.idUser}`);
        await selectKey(
          page,
          dialog,
          state,
          definitions.find((d) => d.keyId === OWNED_KEY)!
        );
        await number(dialog, 764);
        await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
        await close(dialog);
      } finally {
        pending.release();
      }
    });
  });
}
