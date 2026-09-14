-- 修复「UTF-8 中文被 Latin-1 客户端写入 utf8mb4」造成的乱码（æŸ…å… 这类）
-- 已含汉字的字段不会改动。导入：
--   mysql --default-character-set=utf8mb4 ... < fix_utf8_mojibake.sql

SET NAMES utf8mb4;

UPDATE `proj_info` SET
  `name` = IF(`name` IS NULL OR `name` = '' OR `name` REGEXP '[一-龥]', `name`, IF(`name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`name` USING latin1) USING utf8mb4), `name`)),
  `goal` = IF(`goal` IS NULL OR `goal` = '' OR `goal` REGEXP '[一-龥]', `goal`, IF(`goal` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`goal` USING latin1) USING utf8mb4), `goal`)),
  `main_work` = IF(`main_work` IS NULL OR `main_work` = '' OR `main_work` REGEXP '[一-龥]', `main_work`, IF(`main_work` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`main_work` USING latin1) USING utf8mb4), `main_work`)),
  `lead_org_name` = IF(`lead_org_name` IS NULL OR `lead_org_name` = '' OR `lead_org_name` REGEXP '[一-龥]', `lead_org_name`, IF(`lead_org_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`lead_org_name` USING latin1) USING utf8mb4), `lead_org_name`)),
  `manage_org_name` = IF(`manage_org_name` IS NULL OR `manage_org_name` = '' OR `manage_org_name` REGEXP '[一-龥]', `manage_org_name`, IF(`manage_org_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`manage_org_name` USING latin1) USING utf8mb4), `manage_org_name`)),
  `owner_name` = IF(`owner_name` IS NULL OR `owner_name` = '' OR `owner_name` REGEXP '[一-龥]', `owner_name`, IF(`owner_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`owner_name` USING latin1) USING utf8mb4), `owner_name`)),
  `org_name` = IF(`org_name` IS NULL OR `org_name` = '' OR `org_name` REGEXP '[一-龥]', `org_name`, IF(`org_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`org_name` USING latin1) USING utf8mb4), `org_name`)),
  `create_by_name` = IF(`create_by_name` IS NULL OR `create_by_name` = '' OR `create_by_name` REGEXP '[一-龥]', `create_by_name`, IF(`create_by_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`create_by_name` USING latin1) USING utf8mb4), `create_by_name`)),
  `channel_name` = IF(`channel_name` IS NULL OR `channel_name` = '' OR `channel_name` REGEXP '[一-龥]', `channel_name`, IF(`channel_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`channel_name` USING latin1) USING utf8mb4), `channel_name`));

UPDATE `proj_team_member` SET
  `role_name` = IF(`role_name` IS NULL OR `role_name` = '' OR `role_name` REGEXP '[一-龥]', `role_name`, IF(`role_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`role_name` USING latin1) USING utf8mb4), `role_name`)),
  `user_name` = IF(`user_name` IS NULL OR `user_name` = '' OR `user_name` REGEXP '[一-龥]', `user_name`, IF(`user_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`user_name` USING latin1) USING utf8mb4), `user_name`));

UPDATE `sys_user` SET
  `real_name` = IF(`real_name` IS NULL OR `real_name` = '' OR `real_name` REGEXP '[一-龥]', `real_name`, IF(`real_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`real_name` USING latin1) USING utf8mb4), `real_name`)),
  `org_name` = IF(`org_name` IS NULL OR `org_name` = '' OR `org_name` REGEXP '[一-龥]', `org_name`, IF(`org_name` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`org_name` USING latin1) USING utf8mb4), `org_name`)),
  `identity` = IF(`identity` IS NULL OR `identity` = '' OR `identity` REGEXP '[一-龥]', `identity`, IF(`identity` REGEXP 'æ|å|ç|Â|Ã', CONVERT(BINARY CONVERT(`identity` USING latin1) USING utf8mb4), `identity`));
