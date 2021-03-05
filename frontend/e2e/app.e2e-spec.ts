import {GrafioschtraderClientPage} from './app.po';

describe('grafioschtrader-client App', () => {
  let page: GrafioschtraderClientPage;

  beforeEach(() => {
    page = new GrafioschtraderClientPage();
  });

  it('should display message saying app works', () => {
    page.navigateTo();
    expect(page.getParagraphText()).toEqual('app works!');
  });
});
