# Some Version of mariadb do not work with drop contraint
#--------------------------------------------------------
# ALTER TABLE transaction DROP CONSTRAINT `s_quotation`;
# ALTER TABLE transaction ADD CONSTRAINT `s_quotation` CHECK (`quotation` is not null and (`quotation` > 0 or `quotation` <> 0 and `transaction_type` BETWEEN 6 AND 7 ) and `id_securitycurrency` is not null or `quotation` is null and `id_securitycurrency` is null);

ALTER TABLE `imp_trans_pos` DROP COLUMN IF EXISTS `transaction_error`;
ALTER TABLE `imp_trans_pos` ADD `transaction_error` VARCHAR(1024) NULL AFTER `known_other_flags`; 