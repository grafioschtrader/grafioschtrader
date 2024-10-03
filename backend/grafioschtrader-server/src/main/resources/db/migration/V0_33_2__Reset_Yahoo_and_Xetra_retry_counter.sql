UPDATE securitycurrency sc JOIN security s ON sc.id_securitycurrency = s.id_securitycurrency SET sc.retry_intra_load = 0 WHERE id_connector_intra = "gt.datafeed.xetra" AND s.active_to_date >= "2024-09-30" AND sc.retry_intra_load > 0; 
UPDATE security SET retry_dividend_load = 0 WHERE id_connector_dividend = "gt.datafeed.yahoo" AND active_to_date >= "2024-09-01" AND retry_dividend_load > 0;
UPDATE security SET retry_split_load = 0 WHERE id_connector_split = "gt.datafeed.yahoo" AND active_to_date >= "2024-09-01" AND retry_split_load > 0;
