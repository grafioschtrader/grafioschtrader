DELETE FROM globalparameters WHERE property_name = 'gt.udf.general.recreate';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.udf.general.recreate', 1, NULL, NULL, NULL, '1');
 
ALTER TABLE mic_provider_map DROP COLUMN IF EXISTS symbol_suffix;
ALTER TABLE `mic_provider_map` ADD `symbol_suffix` CHAR(4) NULL AFTER `code_provider`; 

-- Update für .AX (Australian Securities Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.AX' WHERE id_provider = 'yahoo' AND mic = 'XASX';

-- Update für .BA (Bolsa de Comercio de Buenos Aires)
UPDATE mic_provider_map SET symbol_suffix = '.BA' WHERE id_provider = 'yahoo' AND mic = 'XBUE';

-- Update für .BC (Bolsa de Comercio de Santiago)
UPDATE mic_provider_map SET symbol_suffix = '.SN' WHERE id_provider = 'yahoo' AND mic = 'XSGO';

-- Update für .BD (Budapest Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.BD' WHERE id_provider = 'yahoo' AND mic = 'XBUD';

-- Update für .BE (Börse Berlin)
UPDATE mic_provider_map SET symbol_suffix = '.BE' WHERE id_provider = 'yahoo' AND mic = 'XBER';

-- Update für .BK (Stock Exchange of Thailand)
UPDATE mic_provider_map SET symbol_suffix = '.BK' WHERE id_provider = 'yahoo' AND mic = 'XBKK';

-- Update für .BO (Bombay Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.BO' WHERE id_provider = 'yahoo' AND mic = 'XBOM';

-- Update für .BR (Euronext Brussels)
UPDATE mic_provider_map SET symbol_suffix = '.BR' WHERE id_provider = 'yahoo' AND mic = 'XBRU';

-- Update für .CA (Egyptian Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.CA' WHERE id_provider = 'yahoo' AND mic = 'XCAI';

-- Update für .CN (Canadian Securities Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.CN' WHERE id_provider = 'yahoo' AND mic = 'XCNQ';

-- Update für .CO (Nasdaq OMX Copenhagen)
UPDATE mic_provider_map SET symbol_suffix = '.CO' WHERE id_provider = 'yahoo' AND mic = 'XCSE';

-- Update für .CR (Bolsa de Valores de Caracas)
UPDATE mic_provider_map SET symbol_suffix = '.CR' WHERE id_provider = 'yahoo' AND mic = 'XCVE';

-- Update für .DE (Deutsche Börse)
UPDATE mic_provider_map SET symbol_suffix = '.DE' WHERE id_provider = 'yahoo' AND mic = 'XETR';

-- Update für .DU (Börse Düsseldorf)
UPDATE mic_provider_map SET symbol_suffix = '.DU' WHERE id_provider = 'yahoo' AND mic = 'XDUS';

-- Update für .F (Börse Frankfurt)
UPDATE mic_provider_map SET symbol_suffix = '.F' WHERE id_provider = 'yahoo' AND mic = 'XFRA';

-- Update für .HE (Nasdaq OMX Helsinki)
UPDATE mic_provider_map SET symbol_suffix = '.HE' WHERE id_provider = 'yahoo' AND mic = 'XHEL';

-- Update für .HK (Hong Kong Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.HK' WHERE id_provider = 'yahoo' AND mic = 'XHKG';

-- Update für .SW (SIX Swiss Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.SW' WHERE id_provider = 'yahoo' AND mic = 'XSWX';

-- Update für .T (Tokyo Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.T' WHERE id_provider = 'yahoo' AND mic = 'XTKS';

-- Update für .TO (Toronto Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.TO' WHERE id_provider = 'yahoo' AND mic = 'XTSE';

-- Update für .TW (Taiwan Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.TW'WHERE id_provider = 'yahoo' AND mic = 'XTAI';

-- Update für .IR (Euronext Dublin)
UPDATE mic_provider_map SET symbol_suffix = '.IR' WHERE id_provider = 'yahoo' AND mic = 'XDUB';

-- Update für .IS (Borsa İstanbul)
UPDATE mic_provider_map SET symbol_suffix = '.IS' WHERE id_provider = 'yahoo' AND mic = 'XIST';

-- Update für .JK (Indonesia Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.JK' WHERE id_provider = 'yahoo' AND mic = 'XIDX';

-- Update für .JO (Johannesburg Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.JO' WHERE id_provider = 'yahoo' AND mic = 'XJSE';

-- Update für .KL (Bursa Malaysia)
UPDATE mic_provider_map SET symbol_suffix = '.KL' WHERE id_provider = 'yahoo' AND mic = 'XKLS'; 

-- Update für .KS (Korea Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.KS' WHERE id_provider = 'yahoo' AND mic = 'XKRX';

-- Update für .KQ (KOSDAQ)
UPDATE mic_provider_map SET symbol_suffix = '.KQ' WHERE id_provider = 'yahoo' AND mic = 'XKOS';

-- Update für .L (London Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.L' WHERE id_provider = 'yahoo' AND mic = 'XLON';

-- Update für .LS (Euronext Lisbon)
UPDATE mic_provider_map SET symbol_suffix = '.LS' WHERE id_provider = 'yahoo' AND mic = 'XLIS';

-- Update für .MC (Bolsa de Madrid)
UPDATE mic_provider_map SET symbol_suffix = '.MC' WHERE id_provider = 'yahoo' AND mic = 'XMAD';

-- Update für .MI (Borsa Italiana)
UPDATE mic_provider_map SET symbol_suffix = '.MI' WHERE id_provider = 'yahoo' AND mic = 'XMIL';

-- Update für .MU (Börse München)
UPDATE mic_provider_map SET symbol_suffix = '.MU' WHERE id_provider = 'yahoo' AND mic = 'XMUN';

-- Update für .NL (Euronext Amsterdam)
UPDATE mic_provider_map SET symbol_suffix = '.AS' WHERE id_provider = 'yahoo' AND mic = 'XAMS';

-- Update für .NS (National Stock Exchange of India)
UPDATE mic_provider_map SET symbol_suffix = '.NS' WHERE id_provider = 'yahoo' AND mic = 'XNSE';

-- Update für .NZ (New Zealand Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.NZ' WHERE id_provider = 'yahoo' AND mic = 'XNZE';

-- Update für .OL (Oslo Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.OL' WHERE id_provider = 'yahoo' AND mic = 'XOSL';

-- Update für .PA (Euronext Paris)
UPDATE mic_provider_map SET symbol_suffix = '.PA' WHERE id_provider = 'yahoo' AND mic = 'XPAR';

-- Update für .RO (Bucharest Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.RO' WHERE id_provider = 'yahoo' AND mic = 'XBSE';

-- Update für .SA (B3 - Brasil Bolsa Balcão)
UPDATE mic_provider_map SET symbol_suffix = '.SA' WHERE id_provider = 'yahoo' AND mic = 'BVMF';

-- Update für .SG (Börse Stuttgart)
UPDATE mic_provider_map SET symbol_suffix = '.SG' WHERE id_provider = 'yahoo' AND mic = 'XSTU';

-- Update für .ST (Nasdaq OMX Stockholm)
UPDATE mic_provider_map SET symbol_suffix = '.ST' WHERE id_provider = 'yahoo' AND mic = 'XSTO';

-- Update für .SZ (Shenzhen Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.SZ' WHERE id_provider = 'yahoo' AND mic = 'XSHE';

-- Update für .TA (Tel Aviv Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.TA' WHERE id_provider = 'yahoo' AND mic = 'XTAE';

-- Update für .V (TSX Venture Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.V' WHERE id_provider = 'yahoo' AND mic = 'XTSX';

-- Update für .VI (Wiener Börse)
UPDATE mic_provider_map SET symbol_suffix = '.VI' WHERE id_provider = 'yahoo' AND mic = 'XWBO';

-- Update für .WA (Warsaw Stock Exchange)
UPDATE mic_provider_map SET symbol_suffix = '.WA' WHERE id_provider = 'yahoo' AND mic = 'XWAR';

