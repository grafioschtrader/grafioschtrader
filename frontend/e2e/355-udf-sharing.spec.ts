import { expect, Locator, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

interface Field {
  idUDFMetadata: number;
  idUDFMetadataSubscription: number | null;
  idUser: number | null;
  description: string;
  entity?: string;
  uiOrder: number;
  active?: boolean;
}

async function headers(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token).toBeTruthy();
  return { 'x-auth-token': token! };
}

async function fields(page: Page, endpoint: string): Promise<Field[]> {
  const response = await page.request.get(endpoint, { headers: await headers(page) });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}

async function menu(page: Page, row: Locator, label: RegExp): Promise<void> {
  await row.click();
  await row.click({ button: 'right' });
  await page.locator('[role="menu"]:visible').getByRole('menuitem', { name: label }).click();
}

async function openFields(page: Page): Promise<void> {
  await page.goto('/mainview/udfmetadatageneral');
  await page.getByRole('tab', { name: /My additional fields|Meine Zusatzfelder/ }).click();
}

async function clearFillTasks(owner: Page, fieldIds: Set<number>): Promise<void> {
  if (fieldIds.size === 0) return;
  interface FillTask {
    idTaskDataChange: number;
    idEntity: number | null;
    entity: string | null;
    progressStateType: string;
  }
  const ownTasks = async (): Promise<FillTask[]> => {
    const response = await owner.request.get('/api/taskdatachange', { headers: await headers(owner) });
    expect(response.ok()).toBeTruthy();
    return ((await response.json()) as FillTask[]).filter(
      (t) => t.entity === 'UDFMetadataSecurity' && t.idEntity != null && fieldIds.has(t.idEntity)
    );
  };
  for (const task of await ownTasks()) {
    if (task.progressStateType === 'PROG_RUNNING') {
      const response = await owner.request.patch(
        `/api/taskdatachange/interruptingrunningjob/${task.idTaskDataChange}`,
        { headers: await headers(owner) }
      );
      expect(response.ok()).toBeTruthy();
    }
  }
  await expect
    .poll(async () => (await ownTasks()).filter((t) => t.progressStateType === 'PROG_RUNNING').length)
    .toBe(0);
  for (const task of await ownTasks()) {
    const response = await owner.request.delete(`/api/taskdatachange/${task.idTaskDataChange}`, {
      headers: await headers(owner)
    });
    expect(response.ok()).toBeTruthy();
  }
}

for (const { kind, automatic } of [
  { kind: 'general', automatic: false },
  { kind: 'security', automatic: false },
  { kind: 'security', automatic: true }
] as const) {
  test(`${automatic ? 'template' : kind} field can be adopted, orphaned and claimed through the additional-field tabs`, async ({
    browser
  }) => {
    const ownerContext = await browser.newContext();
    const readerContext = await browser.newContext();
    const owner = await ownerContext.newPage();
    const reader = await readerContext.newPage();
    owner.setDefaultTimeout(15_000);
    reader.setDefaultTimeout(15_000);
    const endpoint = `/api/udfmetadata${kind}`;
    const name = automatic ? 'E2E share template' : `E2E share ${kind}`;
    const fieldIds = new Set<number>();
    await loginAsFixtureUser(owner, 'admin');
    await loginAsFixtureUser(reader, 'alledit');
    const cleanup = async () => {
      // Remove only this test's subscription and field, including an orphan or a field claimed on a previous run.
      for (const page of [reader, owner]) {
        for (const f of (await fields(page, endpoint)).filter(
          (f) => f.description === name || (!automatic && f.description === `E2E shared ${kind} field`)
        )) {
          fieldIds.add(f.idUDFMetadata);
          if (automatic) await clearFillTasks(owner, fieldIds);
          const subscription = f.idUDFMetadataSubscription;
          if (subscription != null) {
            const response = await page.request.delete(`/api/udfmetadatasubscription/${subscription}`, {
              headers: await headers(page)
            });
            expect(response.ok()).toBeTruthy();
          }
        }
      }
      for (const page of [reader, owner]) {
        for (const f of (await fields(page, endpoint)).filter(
          (f) =>
            (f.description === name || (!automatic && f.description === `E2E shared ${kind} field`)) &&
            f.idUser != null &&
            f.idUDFMetadataSubscription == null
        )) {
          fieldIds.add(f.idUDFMetadata);
          if (automatic) await clearFillTasks(owner, fieldIds);
          const response = await page.request.delete(`${endpoint}/${f.idUDFMetadata}`, {
            headers: await headers(page)
          });
          expect(response.ok()).toBeTruthy();
        }
      }
      if (automatic) await clearFillTasks(owner, fieldIds);
    };
    try {
      await cleanup();
      const occupied = (await fields(owner, endpoint))
        .filter((f) => kind === 'security' || f.entity === 'Currencypair')
        .map((f) => f.uiOrder);
      const order = Array.from({ length: 99 }, (_, i) => i + 1).find((i) => !occupied.includes(i))!;
      const created = await owner.request.post(endpoint, {
        headers: await headers(owner),
        data: {
          description: name,
          udfDataType: 'UDF_String',
          fieldSize: '1,80',
          uiOrder: order,
          shareMode: 'UDF_SHARE_NONE',
          ...(kind === 'general'
            ? { entity: 'Currencypair' }
            : {
                fillSource: 'MANUAL',
                categoryTypeEnums: ['EQUITIES'],
                specialInvestmentInstrumentEnums: ['DIRECT_INVESTMENT']
              })
        }
      });
      expect(created.ok(), await created.text()).toBeTruthy();
      const field = (await created.json()) as Field;
      fieldIds.add(field.idUDFMetadata);
      await openFields(owner);
      const ownTable = owner.locator(`udf-metadata-${kind}-table`);
      await menu(owner, ownTable.locator('tbody tr').filter({ hasText: name }), /^(Edit|Bearbeiten)\b/);
      const editor = owner.locator(`udf-metadata-${kind}-edit .p-dialog`);
      if (automatic) {
        await editor.locator('select#fillSource').selectOption('TEMPLATE');
        await editor.locator('textarea#fillRule').fill('https://example.com/{ticker}');
      }
      await editor.locator('select#shareMode').selectOption('UDF_SHARE_READ');
      await editor.locator('button[type="submit"]').click();
      await expect(editor).toBeHidden();
      await openFields(reader);
      await reader.getByRole('tab', { name: /Shared additional fields|Freigegebene Zusatzfelder/ }).click();
      const sharedTable = reader.locator(`udf-metadata-${kind}-shared-table`);
      await menu(
        reader,
        sharedTable.locator('tbody tr').filter({ hasText: name }),
        /Adopt additional field|Zusatzfeld .bernehmen/
      );
      const subscription = reader.locator('udf-subscription-edit .p-dialog');
      if (automatic)
        await expect(subscription.getByText('https://example.com/{ticker}', { exact: true })).toBeVisible();
      const option = subscription.locator('select#uiOrder option').filter({ hasText: /\d+/ }).first();
      await subscription.locator('select#uiOrder').selectOption((await option.getAttribute('value'))!);
      await subscription.locator('button[type="submit"]').click();
      await expect(subscription).toBeHidden();
      await expect(sharedTable.locator('tbody tr').filter({ hasText: name })).toHaveCount(0);
      await reader.getByRole('tab', { name: /My additional fields|Meine Zusatzfelder/ }).click();
      const readerTable = reader.locator(`udf-metadata-${kind}-table`);
      await expect(readerTable.locator('tbody tr').filter({ hasText: name })).toBeVisible();
      const deleted = await owner.request.delete(`${endpoint}/${field.idUDFMetadata}`, {
        headers: await headers(owner)
      });
      expect(deleted.ok()).toBeTruthy();
      await reader.reload();
      const orphan = (await fields(reader, endpoint)).find((f) => f.idUDFMetadata === field.idUDFMetadata)!;
      expect(orphan.idUser).toBeNull();
      if (kind === 'security') expect(orphan.active).toBe(false);
      await menu(reader, readerTable.locator('tbody tr').filter({ hasText: name }), /Become owner|Eigent.mer werden/);
      await expect
        .poll(
          async () =>
            (await fields(reader, endpoint)).find((f) => f.idUDFMetadata === field.idUDFMetadata)
              ?.idUDFMetadataSubscription
        )
        .toBeNull();
      expect(
        (await fields(reader, endpoint)).find((f) => f.idUDFMetadata === field.idUDFMetadata)?.idUser
      ).toBeGreaterThan(0);
    } finally {
      await cleanup();
      await ownerContext.close();
      await readerContext.close();
    }
  });
}
