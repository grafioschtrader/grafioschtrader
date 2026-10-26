import { expect, Locator, Page, Route, test } from '@playwright/test';

const BASE = '/api/taskdatachange';
const MAIN = 'E2E main queue';
const SECOND = 'E2E secondary queue';
const CREATE = /^(Create|Erstellen)\b/;
const COPY = /^(Copy|Kopiere)\b/;
const DELETE = /^(Delete|Löschen)\b/;
const TASKS = {
  TOKEN_USER_REGISTRATION_PURGE: 15,
  PURGE_EXPIRED_ACCESS_TOKEN: 16,
  GTNET_SERVER_STATUS_CHECK: 20,
  GTNET_EXCHANGE_LOG_AGGREGATION: 22,
  MOVE_CREATED_BY_USER_TO_OTHER_USER: 80
};
const CONSTRAINTS = {
  maxUserCreateTask: 79,
  maxDaysInFuture: 60,
  queues: [
    { name: 'MAIN', parallel: 1, labelKey: MAIN },
    { name: 'SECOND', parallel: 2, labelKey: SECOND }
  ],
  taskTypeQueue: {
    TOKEN_USER_REGISTRATION_PURGE: 'MAIN',
    GTNET_SERVER_STATUS_CHECK: 'MAIN',
    MOVE_CREATED_BY_USER_TO_OTHER_USER: 'MAIN',
    PURGE_EXPIRED_ACCESS_TOKEN: 'SECOND',
    GTNET_EXCHANGE_LOG_AGGREGATION: 'SECOND'
  },
  taskTypeConfig: {
    TOKEN_USER_REGISTRATION_PURGE: ['', 'User'],
    GTNET_SERVER_STATUS_CHECK: ['GTNet'],
    PURGE_EXPIRED_ACCESS_TOKEN: [],
    GTNET_EXCHANGE_LOG_AGGREGATION: ['GTNet']
  },
  entityIdOptions: {
    GTNET_SERVER_STATUS_CHECK: { GTNet: [{ key: '101', value: 'Main test peer' }] },
    GTNET_EXCHANGE_LOG_AGGREGATION: { GTNet: [{ key: '202', value: 'Secondary test peer' }] }
  },
  canBeInterruptedList: ['GTNET_EXCHANGE_LOG_AGGREGATION']
};

function task(id: number, name: keyof typeof TASKS, progress = 'PROG_WAITING') {
  return {
    idTaskDataChange: id,
    idTask: name,
    taskAsId: TASKS[name],
    creationTime: '2026-01-01T12:00:00',
    earliestStartTime: '2026-01-02T12:00:00',
    progressStateType: progress,
    executionPriority: 'PRIO_NORMAL',
    entity: null,
    idEntity: null,
    failedMessageCode: progress === 'PROG_FAILED' ? 'E2E failed task' : null,
    failedStackTrace: progress === 'PROG_FAILED' ? 'E2E stack line 1\nE2E stack line 2' : null
  };
}

/** Page-local transport: no job is enqueued, interrupted or deleted in the database. */
async function fixture(page: Page) {
  const state = {
    rows: [
      task(91001, 'TOKEN_USER_REGISTRATION_PURGE', 'PROG_FAILED'),
      task(91002, 'GTNET_SERVER_STATUS_CHECK'),
      task(91003, 'MOVE_CREATED_BY_USER_TO_OTHER_USER'),
      task(92001, 'PURGE_EXPIRED_ACCESS_TOKEN'),
      task(92002, 'GTNET_EXCHANGE_LOG_AGGREGATION', 'PROG_RUNNING')
    ],
    lists: [] as number[][],
    writes: [] as { method: string; body: Record<string, unknown> | number }[],
    pending: null as { path: string; arrived: boolean; gate: Promise<void> } | null
  };
  async function handler(route: Route): Promise<void> {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    let json: unknown;
    let status = 200;
    if (url.pathname === `${BASE}/taskdatachangeconstraints`) {
      json = CONSTRAINTS;
    } else if (url.pathname === '/api/security/tooltip') {
      json = {};
    } else if (method === 'GET' && url.pathname === BASE) {
      const ids = (url.searchParams.get('idTasks') ?? '').split(',').filter(Boolean).map(Number);
      expect(ids.length, 'A queue load must always narrow its task types').toBeGreaterThan(0);
      state.lists.push(ids);
      json = state.rows.filter((row) => ids.includes(row.taskAsId)).map((row) => ({ ...row }));
    } else if (method === 'POST' && url.pathname === BASE) {
      const body = request.postDataJSON();
      state.writes.push({ method, body });
      const saved = { ...task(93000, body.idTask), ...body };
      state.rows = [...state.rows, saved];
      json = saved;
    } else if (method === 'DELETE' && url.pathname.startsWith(`${BASE}/`)) {
      const id = Number(url.pathname.split('/').pop());
      state.writes.push({ method, body: id });
      state.rows = state.rows.filter((row) => row.idTaskDataChange !== id);
      status = 204;
    } else {
      throw new Error(`Unexpected task request: ${method} ${url.pathname}`);
    }
    const pending = state.pending;
    if (method === 'GET' && pending?.path === url.pathname) {
      state.pending = null;
      pending.arrived = true;
      await pending.gate;
    }
    try {
      await route.fulfill(status === 204 ? { status } : { status, json });
    } catch (error) {
      if (request.failure()?.errorText !== 'net::ERR_ABORTED') throw error;
    }
  }
  await page.route(/\/api\/taskdatachange(?:\/[^?]*)?(?:\?.*)?$/, handler);
  await page.route('**/api/security/tooltip', handler);
  return {
    state,
    hold(path: string) {
      let release!: () => void;
      const gate = new Promise<void>((resolve) => (release = resolve));
      const pending = { path, arrived: false, gate };
      state.pending = pending;
      return { pending, release };
    }
  };
}

async function open(page: Page, queue = ''): Promise<Locator> {
  await page.goto(
    `${page.url().split('/mainview')[0]}/mainview/taskdatachangemonitor${queue ? `/queue-${queue}` : ''}`
  );
  return page.locator('configurable-table').first();
}

function row(table: Locator, id: number): Locator {
  return table.locator('tr[data-p-selectable-row]').filter({
    has: table
      .page()
      .locator('td')
      .filter({ hasText: new RegExp(`^\\s*${String(id).split('').join('\\D*')}\\s*$`) })
  });
}

async function menu(page: Page, target: Locator): Promise<Locator> {
  if (!(await target.getAttribute('class'))?.includes('p-datatable-row-selected')) await target.click();
  await target.click({ button: 'right' });
  const actions = page.locator('[role="menu"]:visible');
  await expect(actions).toBeVisible();
  return actions;
}

async function create(
  page: Page,
  table: Locator,
  target = table.locator('h4').first(),
  action = CREATE
): Promise<Locator> {
  await (await menu(page, target)).getByRole('menuitem', { name: action }).first().click();
  const dialog = page.locator('task-data-change-edit .p-dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('#earliestStartTime input')).not.toHaveValue('');
  return dialog;
}

async function close(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

async function selectTask(page: Page, dialog: Locator, id: number): Promise<void> {
  const dropdown = dialog.locator('p-select#idTask');
  const overlay = page.locator('.p-select-overlay:visible');
  // Repositioning a dialog after dependent fields change can close an overlay on scroll.
  await expect(async () => {
    if (!(await overlay.isVisible())) await dropdown.getByRole('button', { name: 'dropdown trigger' }).click();
    await expect(overlay).toBeVisible({ timeout: 500 });
  }).toPass({ timeout: 5_000 });
  await overlay.getByRole('option', { name: new RegExp(` - ${id}$`) }).click();
  await expect(overlay).toHaveCount(0);
  await expect(dropdown).toContainText(new RegExp(` - ${id}\\s*$`));
}

async function filter(page: Page, table: Locator): Promise<Locator> {
  await table.locator('h4').first().click();
  await page.getByRole('menuitem', { name: /^(View|Ansicht)$/ }).click();
  await page.getByRole('menuitem', { name: /^(Task filter|Job-Filter|TASK_FILTER)/i }).click();
  const dialog = page.locator('task-filter-dialog .p-dialog');
  await expect(dialog).toBeVisible();
  return dialog;
}

export function taskMonitorScenarios(
  login: (page: Page, nickname: string) => Promise<unknown>,
  tooltips = false
): void {
  test.describe('task monitor signals with isolated job transport', () => {
    test.use({ viewport: { width: 1700, height: 1200 } });
    let jobs: Awaited<ReturnType<typeof fixture>>;
    test.beforeEach(async ({ page }) => {
      page.setDefaultTimeout(15_000);
      jobs = await fixture(page);
      await login(page, 'admin');
    });

    test('delayed constraints and rows publish tabs, translated headers and failed-task details', async ({ page }) => {
      const metadata = jobs.hold(`${BASE}/taskdatachangeconstraints`);
      let pendingRows: ReturnType<typeof jobs.hold> | undefined;
      try {
        const table = await open(page);
        await expect.poll(() => metadata.pending.arrived).toBe(true);
        await expect(page.getByRole('tab')).toHaveCount(0);
        pendingRows = jobs.hold(BASE);
        metadata.release();
        await expect.poll(() => pendingRows!.pending.arrived).toBe(true);
        await expect(page.getByRole('tab', { name: MAIN })).toHaveAttribute('aria-selected', 'true');
        await expect(row(table, 91001)).toHaveCount(0);
        pendingRows.release();
        await expect(row(table, 91001)).toHaveCount(1);
        await expect(table.locator('thead')).not.toContainText('CREATION_TIME');
        await row(table, 91001).getByRole('link').click();
        await expect(table).toContainText('E2E failed task');
        await expect(table.locator('textarea')).toHaveValue('E2E stack line 1\nE2E stack line 2');
        const actions = await menu(page, row(table, 91003));
        await expect(actions.getByRole('menuitem', { name: COPY })).toBeDisabled();
      } finally {
        metadata.release();
        pendingRows?.release();
      }
    });

    for (const path of [`${BASE}/taskdatachangeconstraints`, BASE]) {
      test(`leaving the monitor cancels pending ${path === BASE ? 'rows' : 'constraints'} before reopening`, async ({
        page
      }) => {
        const cancelled: string[] = [];
        page.on('requestfailed', (request) => {
          if (new URL(request.url()).pathname === path) cancelled.push(request.failure()?.errorText ?? '');
        });
        const pending = jobs.hold(path);
        try {
          await open(page);
          await expect.poll(() => pending.pending.arrived).toBe(true);
          await page
            .locator('.p-tree-node-content')
            .filter({ hasText: /(Global Settings|Globale Einstellungen)/i })
            .click();
          await expect(page).toHaveURL(/\/mainview\/globalsettings$/);
          await expect.poll(() => cancelled).toContain('net::ERR_ABORTED');
          jobs.state.rows = [task(94000, 'TOKEN_USER_REGISTRATION_PURGE')];
          await page
            .locator('.p-tree-node-content')
            .filter({ hasText: /(Batch processing monitor|Stapelverarbeitungs Monitor)/i })
            .click();
          const table = page.locator('configurable-table').first();
          await expect(row(table, 94000)).toHaveCount(1);
          pending.release();
          await expect(row(table, 91001)).toHaveCount(0);
        } finally {
          pending.release();
        }
      });
    }

    for (const path of [BASE, ...(tooltips ? ['/api/security/tooltip'] : [])]) {
      test(`switching queues cancels a pending ${path === BASE ? 'list' : 'tooltip'} response`, async ({ page }) => {
        const cancelled: string[] = [];
        page.on('requestfailed', (request) => {
          if (new URL(request.url()).pathname === path) cancelled.push(request.failure()?.errorText ?? '');
        });
        const pending = jobs.hold(path);
        try {
          const table = await open(page);
          await expect.poll(() => pending.pending.arrived).toBe(true);
          await page.getByRole('tab', { name: SECOND }).click();
          await expect(row(table, 92001)).toHaveCount(1);
          await expect.poll(() => cancelled).toContain('net::ERR_ABORTED');
          pending.release();
          await expect(row(table, 91001)).toHaveCount(0);
          await expect(page.getByRole('tab', { name: SECOND })).toHaveAttribute('aria-selected', 'true');
          await page.goBack();
          await expect(row(table, 91001)).toHaveCount(1);
          await expect(page.getByRole('tab', { name: MAIN })).toHaveAttribute('aria-selected', 'true');
          await page.goForward();
          await expect(row(table, 92001)).toHaveCount(1);
        } finally {
          pending.release();
        }
      });
    }

    test('filter selection is restored, cancellation keeps it and an empty queue intersection makes no request', async ({
      page
    }) => {
      const table = await open(page);
      await expect(row(table, 91001)).toHaveCount(1);
      let dialog = await filter(page, table);
      await dialog.locator('thead').getByRole('checkbox').click();
      await expect(dialog.getByRole('button', { name: /^(Apply|Übernehmen)$/ })).toBeDisabled();
      const choice = dialog
        .locator('tbody > tr')
        .filter({ has: page.locator('td:nth-child(2)').filter({ hasText: /^\s*15\s*$/ }) });
      await choice.getByRole('checkbox').click();
      await dialog.getByRole('button', { name: /^(Apply|Übernehmen)$/ }).click();
      await expect(dialog).toHaveCount(0);
      await expect(table.locator('tr[data-p-selectable-row]')).toHaveCount(1);
      await expect(row(table, 91001)).toHaveCount(1);
      dialog = await filter(page, table);
      await expect(choice.getByRole('checkbox')).toBeChecked();
      await choice.getByRole('checkbox').click();
      await close(dialog);
      expect(jobs.state.lists.at(-1)).toEqual([15]);
      const reads = jobs.state.lists.length;
      await page.getByRole('tab', { name: SECOND }).click();
      await expect(table.locator('tr[data-p-selectable-row]')).toHaveCount(0);
      expect(jobs.state.lists).toHaveLength(reads);
      await page.getByRole('tab', { name: MAIN }).click();
      await expect(row(table, 91001)).toHaveCount(1);
      expect(jobs.state.lists).toEqual([[15, 20, 80], [15], [15]]);
      expect(jobs.state.writes).toEqual([]);
    });

    test('reopened create dialogs follow optional entities, numeric ids and task-scoped select options', async ({
      page
    }) => {
      const table = await open(page);
      await expect(row(table, 91001)).toHaveCount(1);
      for (let opening = 0; opening < 2; opening++) {
        const dialog = await create(page, table);
        const save = dialog.locator('button[type="submit"]');
        await expect(save).toBeDisabled();
        await selectTask(page, dialog, 15);
        await expect(dialog.locator('select#entity')).toBeVisible();
        await expect(save).toBeEnabled();
        await dialog.locator('select#entity').selectOption('User');
        await expect(dialog.locator('#idEntityNum input, input#idEntityNum').first()).toBeVisible();
        await expect(save).toBeDisabled();
        await dialog.locator('#idEntityNum input, input#idEntityNum').first().fill('77');
        await dialog.locator('#idEntityNum input, input#idEntityNum').first().blur();
        await expect(save).toBeEnabled();
        await selectTask(page, dialog, 20);
        await expect(save).toBeDisabled();
        await expect(dialog.locator('#idEntityNum')).toBeHidden();
        await dialog.locator('select#entity').selectOption('GTNet');
        await expect(dialog.locator('select#idEntity option[value="101"]')).toHaveText('Main test peer');
        await dialog.locator('select#idEntity').selectOption('101');
        await expect(save).toBeEnabled();
        await close(dialog);
      }
      await page.getByRole('tab', { name: SECOND }).click();
      let dialog = await create(page, table);
      await selectTask(page, dialog, 22);
      await dialog.locator('select#entity').selectOption('GTNet');
      await expect(dialog.locator('select#idEntity option[value="202"]')).toHaveText('Secondary test peer');
      await expect(dialog.locator('select#idEntity option[value="101"]')).toHaveCount(0);
      await selectTask(page, dialog, 16);
      await expect(dialog.locator('select#entity')).toBeHidden();
      await expect(dialog.locator('select#idEntity')).toBeHidden();
      await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
      await close(dialog);
      expect(jobs.state.writes).toEqual([]);
      dialog = await create(page, table, row(table, 92001), COPY);
      await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
      await close(dialog);
      const actions = await menu(page, row(table, 92002));
      await expect(actions.getByRole('menuitem', { name: DELETE })).toBeDisabled();
    });

    test('creating and deleting a task reloads rows and a copied task uses the latest entity id', async ({ page }) => {
      const table = await open(page);
      await expect(row(table, 91001)).toHaveCount(1);
      let dialog = await create(page, table);
      await selectTask(page, dialog, 20);
      await dialog.locator('select#entity').selectOption('GTNet');
      await dialog.locator('select#idEntity').selectOption('101');
      await dialog.locator('button[type="submit"]').click();
      await expect(dialog).toHaveCount(0);
      await expect(row(table, 93000)).toHaveCount(1);
      expect(jobs.state.writes[0]).toMatchObject({
        method: 'POST',
        body: { idTask: 'GTNET_SERVER_STATUS_CHECK', entity: 'GTNet', idEntity: 101 }
      });
      dialog = await create(page, table, row(table, 93000), COPY);
      await expect(dialog.locator('select#idEntity')).toHaveValue('101');
      await close(dialog);
      await (await menu(page, row(table, 93000))).getByRole('menuitem', { name: DELETE }).click();
      await page
        .locator('[role="alertdialog"]:visible')
        .getByRole('button', { name: /^\s*(yes|ja)\s*$/i })
        .click();
      await expect(row(table, 93000)).toHaveCount(0);
      expect(jobs.state.writes[1]).toEqual({ method: 'DELETE', body: 93000 });
      await expect(row(table, 91001)).toHaveCount(1);
    });
  });
}
