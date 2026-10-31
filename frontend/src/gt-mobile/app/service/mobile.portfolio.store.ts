import { Service } from '@angular/core';
import { MobileAccountGrandSummary, MobilePortfolioGrouping } from '../portfolio/mobile.portfolio.summary';
import { MobileSessionCache } from './mobile.session.cache';

/** The account report last read, per grouping, shared by the overview and the page of one group. */
@Service()
export class MobilePortfolioStore extends MobileSessionCache<MobilePortfolioGrouping, MobileAccountGrandSummary> {}
