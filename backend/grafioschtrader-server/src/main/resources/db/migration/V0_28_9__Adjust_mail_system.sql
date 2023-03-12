SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

ALTER TABLE user DROP COLUMN IF EXISTS last_role_modified_time;
ALTER TABLE user ADD last_role_modified_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER limit_request_exceed_count; 
UPDATE user SET last_role_modified_time = creation_time;

DELETE FROM mail_in_out;
DELETE FROM mail_inbox;
DELETE FROM mail_sendbox;
ALTER TABLE mail_sendbox DROP FOREIGN KEY IF EXISTS FK_Sendbox_MailInOut; 
ALTER TABLE `mail_sendbox` DROP INDEX IF EXISTS `FK_Sendbox_MailInOut`;
DROP TABLE IF EXISTS mail_inbox_read;
DROP TABLE IF EXISTS mail_inbox;
DROP TABLE IF EXISTS mail_sendbox;
DROP TABLE IF EXISTS mail_in_out;


DELETE FROM globalparameters WHERE property_name IN ("gt.limit.day.MailSendbox", "gt.limit.day.MailSendRecv");
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.limit.day.MailSendRecv', '200', NULL, NULL, NULL, '0');


DROP TABLE IF EXISTS mail_send_recv_read_del;
DROP TABLE IF EXISTS mail_send_recv;

--
--  `mail_send_recv`
--

CREATE TABLE `mail_send_recv` (
  `id_mail_send_recv` int(11) NOT NULL,
  `send_recv` char(1) NOT NULL,
  `id_user_from` int(11) NOT NULL,
  `id_user_to` int(11) DEFAULT NULL,
  `id_role_to` int(11) DEFAULT NULL,
  `id_reply_to_remote` int(11) DEFAULT NULL,
  `id_reply_to_local` int(11) DEFAULT NULL,
  `reply_to_role_private` tinyint(1) NOT NULL DEFAULT 0,
  `id_gt_net` int(11) DEFAULT NULL,
  `id_entity` varchar(20) DEFAULT NULL,
  `message_com_type` tinyint(4) NOT NULL DEFAULT 0,
  `subject` varchar(96) NOT NULL,
  `message` varchar(1024) NOT NULL,
  `send_recv_time` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- `mail_send_recv_read_del`
--

CREATE TABLE `mail_send_recv_read_del` (
  `id_mail_send_recv` int(11) NOT NULL,
  `id_user` int(11) NOT NULL,
  `has_been_read` tinyint(1) NOT NULL DEFAULT 0,
  `mark_hide_del` tinyint(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


--
-- Indizes für die Tabelle `mail_send_recv`
--
ALTER TABLE `mail_send_recv`
  ADD PRIMARY KEY (`id_mail_send_recv`),
  ADD KEY `FK_MailInOut_Role` (`id_role_to`),
  ADD KEY `FK_MailInOut_GtNet` (`id_gt_net`),
  ADD KEY `id_reply_to_local` (`id_reply_to_local`);

--
-- Indizes für die Tabelle `mail_send_recv_read_del`
--
ALTER TABLE `mail_send_recv_read_del`
  ADD PRIMARY KEY (`id_mail_send_recv`,`id_user`),
  ADD KEY `FK_MailInBoxRead_User` (`id_user`);


ALTER TABLE `mail_send_recv`
  MODIFY `id_mail_send_recv` int(11) NOT NULL AUTO_INCREMENT;

--
-- Constraints der Tabelle `mail_send_recv`
--
ALTER TABLE `mail_send_recv`
  ADD CONSTRAINT `FK_MailSendRecv_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`),
  ADD CONSTRAINT `FK_MailSendRecv_Role` FOREIGN KEY (`id_role_to`) REFERENCES `role` (`id_role`);

--
-- Constraints der Tabelle `mail_send_recv_read_del`
--
ALTER TABLE `mail_send_recv_read_del`
  ADD CONSTRAINT `FK_MailSendRecvReadDel_MailSendRecv` FOREIGN KEY (`id_mail_send_recv`) REFERENCES `mail_send_recv` (`id_mail_send_recv`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_MailSendRecvReadDel_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;