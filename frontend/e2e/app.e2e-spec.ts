import { browser, logging } from 'protractor';
import {GrafioschtraderClientPage} from './app.po';

describe('grafioschtrader-client App', () => {
  let page: GrafioschtraderClientPage;

  beforeEach(() => {
    page = new GrafioschtraderClientPage();
  });

  it('should display welcome message', async () => {
    await page.navigateTo();
    expect(await page.getTitleText()).toEqual('my-first-project app is running!');
  });

  afterEach(async () => {
    // Assert that there are no errors emitted from the browser
    const logs = await browser.manage().logs().get(logging.Type.BROWSER);
    expect(logs).not.toContain(jasmine.objectContaining({
      level: logging.Level.SEVERE,
    } as logging.Entry));
  });
});

