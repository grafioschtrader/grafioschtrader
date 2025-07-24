SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;


DROP TABLE IF EXISTS `release_note`;
CREATE TABLE `release_note` (
  `id_release_note` int(11) NOT NULL,
  `version` varchar(12) NOT NULL,
  `language` char(2) NOT NULL,
  `note` varchar(1024) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


INSERT INTO `release_note` (`id_release_note`, `version`, `language`, `note`) VALUES
(1, '0.33.0', 'DE', 'Zusätzliche globale benutzerdefinierte Zusatzfelder'),
(2, '0.33.0', 'EN', 'Additional global user-defined additional fields'),
(3, '0.32.0', 'DE', 'Benutzerdefinierte Zusatzfelder'),
(4, '0.32.0', 'EN', 'User-defined additional fields'),
(5, '0.31.0', 'DE', 'Regulärer Ausdruck für Passwortstärke'),
(6, '0.31.0', 'EN', 'Regular expression for password strength');

ALTER TABLE `release_note`
  ADD PRIMARY KEY (`id_release_note`),
  ADD UNIQUE KEY `uk_version_language` (`version`,`language`);


ALTER TABLE `release_note`
  MODIFY `id_release_note` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;
COMMIT;

INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time, creation_time, exec_start_time, exec_end_time, old_value_varchar, old_value_number, progress_state, failed_message_code) 
VALUES (19, 40, NULL, NULL, UTC_TIMESTAMP() + INTERVAL 3 MINUTE, UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, 0, NULL);



/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
