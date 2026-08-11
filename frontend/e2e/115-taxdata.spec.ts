import {expect, test} from '@playwright/test';
import {loginAsFixtureUser} from './helpers';
import {
  COUNTRY_CODE,
  createTaxCountry,
  createTaxYear,
  deleteNode,
  deleteTaxCountryIfPresent,
  DIFF_ZIP,
  fixturesPresent,
  FULL_ZIP,
  nodeRow,
  openTaxDataView,
  RX,
  TAX_YEAR,
  uploadTaxData,
  zipName
} from './taxdata.helpers';

const ADMIN = 'admin'; // hg@hugograf.com in users.json

/**
 * Covers the whole lifecycle of the tax data administration view: create the tax country, create the
 * tax year, upload both ICTax Kursliste zips, delete everything again and recreate it. The three
 * tests mirror that sequence, because the create part is executed twice.
 *
 * Every context menu is opened with a plain right-click on the row, without selecting it first, so
 * the spec also guards the selection fix in ConfigurableTreeTableComponent.onRowContextMenu.
 *
 * The Kursliste zips are git-ignored, so the whole file skips when they are missing.
 */
test.describe.serial('tax data - create, upload, delete and recreate', () => {
  test.skip(!fixturesPresent(),
    'Kursliste fixtures backend/grafioschtrader-server/src/test/resources/ictax/kursliste_2025*.zip not found');

  test('creates tax country, tax year 2025 and uploads both kursliste files', async ({page}) => {
    // The full Kursliste is parsed completely in memory before it is filtered by the ISINs held in GT.
    test.setTimeout(900_000);
    await loginAsFixtureUser(page, ADMIN);
    const container = await openTaxDataView(page);

    // Repeatable: remove leftovers from a previous failed run before creating.
    await deleteTaxCountryIfPresent(page, container);

    await createTaxCountry(page, container, COUNTRY_CODE);
    await createTaxYear(page, container, nodeRow(container, RX.country), TAX_YEAR);

    const fullCount = await uploadTaxData(page, container, nodeRow(container, String(TAX_YEAR)), FULL_ZIP);
    expect(fullCount, 'the full Kursliste must match at least one ISIN held in GT').toBeGreaterThan(0);

    const diffCount = await uploadTaxData(page, container, nodeRow(container, String(TAX_YEAR)), DIFF_ZIP);
    expect(diffCount, 'the diff Kursliste holds only changed securities and may match none').toBeGreaterThanOrEqual(0);
  });

  test('deletes the uploaded tax data, the tax year and the tax country', async ({page}) => {
    await loginAsFixtureUser(page, ADMIN);
    const container = await openTaxDataView(page);

    await deleteNode(page, nodeRow(container, zipName(DIFF_ZIP)));
    await deleteNode(page, nodeRow(container, zipName(FULL_ZIP)));
    await deleteNode(page, nodeRow(container, String(TAX_YEAR)));
    await deleteNode(page, nodeRow(container, RX.country));

    await expect(container.locator('.p-treetable-tbody tr')).toHaveCount(0, {timeout: 10_000});
  });

  test('recreates tax country, tax year 2025 and re-uploads both kursliste files', async ({page}) => {
    test.setTimeout(900_000);
    await loginAsFixtureUser(page, ADMIN);
    const container = await openTaxDataView(page);

    await createTaxCountry(page, container, COUNTRY_CODE);
    await createTaxYear(page, container, nodeRow(container, RX.country), TAX_YEAR);

    const fullCount = await uploadTaxData(page, container, nodeRow(container, String(TAX_YEAR)), FULL_ZIP);
    expect(fullCount).toBeGreaterThan(0);

    const diffCount = await uploadTaxData(page, container, nodeRow(container, String(TAX_YEAR)), DIFF_ZIP);
    expect(diffCount).toBeGreaterThanOrEqual(0);
  });
});
