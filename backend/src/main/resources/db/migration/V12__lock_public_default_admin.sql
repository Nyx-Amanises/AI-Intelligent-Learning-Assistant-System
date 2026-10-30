-- 保留已应用的 V3 校验和。先锁定其公开凭据，并撤销旧令牌；
-- 启动初始化器可用部署者提供的私人密码恢复初始 admin，未配置时保持锁定。
-- -1 为凭据安全锁定，0 为主动禁用。已改密及主动禁用的账号不受影响。
UPDATE sys_user
SET status = -1, token_version = token_version + 1
WHERE UPPER(TRIM(role_code)) = 'ADMIN'
  AND BINARY password_hash = '$2a$10$kkQN2HTakw8VlHKRJmx7rujZkYZbmMbLShHG6shrcLTsjew/8YkYu'
  AND status = 1
  AND deleted = 0;
