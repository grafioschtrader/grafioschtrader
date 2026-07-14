-- A user does not yet exist in the security context during self-registration,
-- so the auditing listener legitimately leaves these two user references empty.
ALTER TABLE `user`
  MODIFY COLUMN `created_by` INT(11) NULL,
  MODIFY COLUMN `last_modified_by` INT(11) NULL;
