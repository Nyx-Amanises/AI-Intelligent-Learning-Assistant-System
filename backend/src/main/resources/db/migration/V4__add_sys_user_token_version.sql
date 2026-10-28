-- 令牌版本号：修改密码时加一。登录令牌里带有签发时的版本号，
-- 拦截器发现与数据库不一致就拒绝访问，从而让改密前签发的令牌全部失效。
ALTER TABLE sys_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT '令牌版本，修改密码后递增，用于使旧令牌失效' AFTER status;
