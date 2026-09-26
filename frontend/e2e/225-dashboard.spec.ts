import { randomUUID } from 'node:crypto';
import { expect } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import {
  card,
  cardOrder,
  closeConfiguration,
  COMMON_TYPES,
  configureWidget,
  dashboardHeaders,
  DashboardUser,
  editDashboard,
  expectReloadedLayout,
  openConfiguration,
  readDashboard,
  resetDashboard,
  RX,
  saveDashboard,
  SETTINGS,
  test,
  verifyDashboardTestTarget
} from './dashboard.helpers';

/** Real role filtering, dynamic-form configuration and personal-layout persistence. No market data is mocked. */
for (const nickname of ['admin', 'alledit', 'user', 'limit1'] as DashboardUser[]) {
  test.describe(`Dashboard for ${nickname}`, () => {
    test.use({ dashboardUser: nickname });

    test('role catalogue, numeric configuration and reopened dialogs survive saving', async ({ page, dashboard }) => {
      test.setTimeout(180_000);
      const expectedTypes = [...COMMON_TYPES, ...(nickname === 'admin' ? ['USER_LIMIT_REQUESTS'] : [])].sort();
      expect(dashboard.widgets.map((widget) => widget.type).sort()).toEqual(expectedTypes);
      expect((await cardOrder(page)).sort()).toEqual(expectedTypes);
      const catalogue = await editDashboard(page);
      expect(catalogue.descriptors.map((descriptor) => descriptor.type).sort()).toEqual(expectedTypes);
      expect(catalogue.defaults.map((widget) => widget.type).sort()).toEqual(expectedTypes);

      await test.step('closing one dialog must not strand later configuration dialogs', async () => {
        await closeConfiguration(page, await openConfiguration(page, 'UNREAD_MAIL'));
        await closeConfiguration(page, await openConfiguration(page, 'PERFORMANCE_LAST_SESSIONS'));
        await closeConfiguration(page, await openConfiguration(page, 'PERFORMANCE_LAST_SESSIONS'));
      });

      for (const widget of dashboard.widgets) {
        await test.step(`validate and edit ${widget.type}`, async () => {
          const setting = SETTINGS[widget.type];
          if (!setting) {
            await expect(card(page, widget.type).getByRole('button', { name: RX.configure })).toHaveCount(0);
            return;
          }
          const dialog = await openConfiguration(page, widget.type);
          const input = dialog.locator(`#${setting.field}`);
          const submit = dialog.getByRole('button', { name: RX.submit });
          for (const invalid of ['', String(setting.min - 1), String(setting.max + 1)]) {
            await input.fill(invalid);
            await input.blur();
            await expect(submit).toBeDisabled();
          }
          for (const valid of [setting.min, setting.max]) {
            await input.fill(String(valid));
            await expect(submit).toBeEnabled();
          }
          await input.fill(String(setting.edited));
          await submit.click();
          await expect(page.locator('dashboard-config')).toHaveCount(0);
        });
      }

      const saved = await saveDashboard(page);
      expect(saved.widgets).toEqual(
        dashboard.widgets.map((widget) => ({
          ...widget,
          config: SETTINGS[widget.type] ? { [SETTINGS[widget.type].field]: SETTINGS[widget.type].edited } : {}
        }))
      );
      await expectReloadedLayout(page, saved.widgets);
      await editDashboard(page);
      for (const widget of saved.widgets.filter((w) => SETTINGS[w.type])) {
        const dialog = await openConfiguration(page, widget.type);
        await expect(dialog.locator(`#${SETTINGS[widget.type].field}`)).toHaveValue(
          String(SETTINGS[widget.type].edited)
        );
        await closeConfiguration(page, dialog);
      }
      await page.getByRole('button', { name: RX.cancel }).click();

      if (nickname !== 'admin') {
        await test.step('the server also rejects a forged Admin-only widget', async () => {
          const previous = await readDashboard(page);
          const response = await page.request.put('/api/userdashboard', {
            headers: await dashboardHeaders(page),
            data: {
              expectedRevision: previous.revision,
              schemaVersion: 1,
              resetToDefault: false,
              widgets: [
                ...previous.widgets,
                {
                  instanceId: randomUUID(),
                  type: 'USER_LIMIT_REQUESTS',
                  width: 'HALF',
                  config: { maxRows: 3 }
                }
              ]
            }
          });
          expect(response.status(), await response.text()).toBe(403);
          expect(await response.json()).toEqual({ errorCode: 'DASHBOARD_UNAVAILABLE' });
          const after = await readDashboard(page);
          expect(after.widgets).toEqual(previous.widgets);
          expect(after.revision).toBe(previous.revision);
        });
      }
    });

    test('layout changes, cancel and reset preserve the intended saved layout', async ({ page, dashboard }) => {
      test.setTimeout(180_000);
      const catalogue = await editDashboard(page);
      await configureWidget(page, 'UNREAD_MAIL', 3);
      await card(page, 'UNREAD_MAIL').locator('select').selectOption('FULL');
      await card(page, 'UNREAD_MAIL').getByRole('button', { name: RX.remove }).click();
      await expect(card(page, 'UNREAD_MAIL')).toHaveCount(0);
      const available = page.locator('#dashboardTray .dashboard-tray-item');
      await expect(available).toHaveCount(1);
      await available.getByRole('button', { name: RX.add }).click();
      await expect(available).toHaveCount(0);
      await expect(card(page, 'UNREAD_MAIL')).toHaveCount(1);
      await expect(card(page, 'UNREAD_MAIL').locator('select')).toHaveValue('FULL');
      await card(page, 'UNREAD_MAIL').getByRole('button', { name: RX.earlier }).click();
      const expected = structuredClone(dashboard.widgets);
      const moved = expected.shift()!;
      moved.width = 'FULL';
      moved.config = { maxRows: 3 };
      expected.splice(expected.length - 1, 0, moved);
      await expect.poll(() => cardOrder(page)).toEqual(expected.map((widget) => widget.type));
      const saved = await saveDashboard(page);
      expect(saved.widgets).toEqual(expected);
      await expectReloadedLayout(page, expected);

      await test.step('cancel discards configuration, width, order and removal changes', async () => {
        await editDashboard(page);
        await configureWidget(page, 'UNREAD_MAIL', 4);
        await card(page, 'UNREAD_MAIL').locator('select').selectOption('THIRD');
        await card(page, 'UNREAD_MAIL').getByRole('button', { name: RX.later }).click();
        await card(page, 'HOLDING_LOSERS').getByRole('button', { name: RX.remove }).click();
        await page.getByRole('button', { name: RX.cancel }).click();
        const after = await readDashboard(page);
        expect(after.revision).toBe(saved.revision);
        await expectReloadedLayout(page, expected);
      });

      await test.step('reset is a draft until saved and can itself be cancelled', async () => {
        await editDashboard(page);
        await page.getByRole('button', { name: RX.reset }).click();
        await expect.poll(() => cardOrder(page)).toEqual(catalogue.defaults.map((widget) => widget.type));
        expect((await readDashboard(page)).widgets).toEqual(expected);
        await page.getByRole('button', { name: RX.cancel }).click();
        await expectReloadedLayout(page, expected);
        await editDashboard(page);
        await page.getByRole('button', { name: RX.reset }).click();
        const reset = await saveDashboard(page);
        expect(reset.widgets).toEqual(catalogue.defaults);
        await expectReloadedLayout(page, catalogue.defaults);
      });
    });
  });
}

test('dashboard drag handle changes and persists the reading order', async ({ page, dashboard }) => {
  await editDashboard(page);
  // A single-column layout gives an unambiguous vertical drop target independently of the worker's viewport.
  await page.setViewportSize({ width: 1000, height: 1200 });
  for (const widget of dashboard.widgets) await card(page, widget.type).locator('select').selectOption('FULL');
  const source = card(page, 'UNREAD_MAIL');
  const target = card(page, 'PROPOSE_CHANGE_OPEN');
  const handle = source.locator('[cdkdraghandle]');
  await handle.scrollIntoViewIfNeeded();
  const start = await handle.boundingBox();
  const end = await target.boundingBox();
  if (!start || !end) throw new Error('Both dashboard drag targets must be visible');
  await page.mouse.move(start.x + start.width / 2, start.y + start.height / 2);
  await page.mouse.down();
  await page.mouse.move(start.x + start.width / 2, start.y + start.height / 2 + 12, { steps: 5 });
  await page.mouse.move(end.x + end.width / 2, end.y + end.height / 2, { steps: 25 });
  await page.mouse.up();
  const expected = structuredClone(dashboard.widgets);
  expected.splice(1, 0, expected.shift()!);
  expected.forEach((widget) => (widget.width = 'FULL'));
  await expect.poll(() => cardOrder(page)).toEqual(expected.map((widget) => widget.type));
  expect((await saveDashboard(page)).widgets).toEqual(expected);
  await expectReloadedLayout(page, expected);
});

test('concurrent dashboard saves retain the rejected draft and reload the winner', async ({
  page,
  dashboard,
  browser,
  baseURL
}) => {
  const otherContext = await browser.newContext({ baseURL });
  const other = await otherContext.newPage();
  try {
    await verifyDashboardTestTarget(other);
    await loginAsFixtureUser(other, 'admin');
    await other.goto('/mainview/dashboard');
    await editDashboard(page);
    await editDashboard(other);
    await configureWidget(page, 'UNREAD_MAIL', 3);
    await configureWidget(other, 'UNREAD_MAIL', 4);
    const winner = await saveDashboard(page);
    expect(winner.revision).toBeGreaterThan(dashboard.revision!);
    await saveDashboard(other, 409);
    await expect(other.locator('[role="alert"]')).toContainText(RX.conflict);
    await expect(other.getByRole('button', { name: RX.save })).toBeEnabled();
    await expect(card(other, 'UNREAD_MAIL').locator('header')).toContainText('4');
    expect((await readDashboard(other)).widgets).toEqual(winner.widgets);
    await other.getByRole('button', { name: RX.reload }).click();
    await expect(other.getByRole('button', { name: RX.edit })).toBeEnabled();
    const preview = other.locator('details');
    await expect(preview.locator('summary')).toHaveText(RX.previous);
    await preview.locator('summary').click();
    await expect(preview.locator('li').first()).toContainText('4');
    await expect(card(other, 'UNREAD_MAIL').locator('header')).toContainText('3');
    await expectReloadedLayout(other, winner.widgets);
  } finally {
    await otherContext.close();
  }
});

test('personal layouts are isolated and string-valued settings are rejected atomically', async ({
  page,
  dashboard,
  browser,
  baseURL
}) => {
  const otherContext = await browser.newContext({ baseURL });
  const other = await otherContext.newPage();
  let otherReady = false;
  try {
    await verifyDashboardTestTarget(other);
    await loginAsFixtureUser(other, 'alledit');
    const previous = await resetDashboard(other);
    otherReady = true;
    await editDashboard(page);
    await configureWidget(page, 'UNREAD_MAIL', 3);
    const saved = await saveDashboard(page);
    expect(saved.widgets).not.toEqual(dashboard.widgets);
    const unchanged = await readDashboard(other);
    expect(unchanged.widgets).toEqual(previous.widgets);
    expect(unchanged.revision).toBe(previous.revision);

    const invalid = structuredClone(saved.widgets);
    invalid.find((widget) => widget.type === 'UNREAD_MAIL')!.config.maxRows = '3';
    const response = await page.request.put('/api/userdashboard', {
      headers: await dashboardHeaders(page),
      data: { expectedRevision: saved.revision, schemaVersion: 1, widgets: invalid, resetToDefault: false }
    });
    expect(response.status(), await response.text()).toBe(400);
    expect(await response.json()).toEqual({ errorCode: 'DASHBOARD_INVALID' });
    const after = await readDashboard(page);
    expect(after.widgets).toEqual(saved.widgets);
    expect(after.revision).toBe(saved.revision);
  } finally {
    try {
      if (otherReady) await resetDashboard(other);
    } finally {
      await otherContext.close();
    }
  }
});
