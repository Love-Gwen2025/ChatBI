-- pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- ==============================
-- 用户系统
-- ==============================

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64) NOT NULL UNIQUE,
    password    VARCHAR(256) NOT NULL,
    nickname    VARCHAR(64),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==============================
-- 项目系统
-- ==============================

CREATE TABLE IF NOT EXISTS project (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    table_prefix  VARCHAR(64),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_project (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, project_id)
);

-- ==============================
-- 应用表
-- ==============================

-- 表元数据
CREATE TABLE IF NOT EXISTS table_meta (
    id          BIGSERIAL PRIMARY KEY,
    table_name  VARCHAR(128) NOT NULL,
    table_comment VARCHAR(512),
    schema_name VARCHAR(64) DEFAULT 'public',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 字段元数据
CREATE TABLE IF NOT EXISTS column_meta (
    id             BIGSERIAL PRIMARY KEY,
    table_id       BIGINT NOT NULL REFERENCES table_meta(id) ON DELETE CASCADE,
    column_name    VARCHAR(128) NOT NULL,
    column_type    VARCHAR(64),
    column_comment VARCHAR(512),
    is_primary_key BOOLEAN DEFAULT FALSE,
    ordinal        INT DEFAULT 0
);

-- 会话
CREATE TABLE IF NOT EXISTS conversation (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(256) DEFAULT '新对话',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==============================
-- 示例业务表（Text2SQL 演示用）
-- ==============================

-- 产品表
CREATE TABLE IF NOT EXISTS demo_products (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    category    VARCHAR(64),
    price       NUMERIC(10, 2),
    stock       INT DEFAULT 0,
    created_at  DATE DEFAULT CURRENT_DATE
);
COMMENT ON TABLE demo_products IS '产品信息表';
COMMENT ON COLUMN demo_products.id IS '产品ID';
COMMENT ON COLUMN demo_products.name IS '产品名称';
COMMENT ON COLUMN demo_products.category IS '产品分类';
COMMENT ON COLUMN demo_products.price IS '单价（元）';
COMMENT ON COLUMN demo_products.stock IS '库存数量';

-- 客户表
CREATE TABLE IF NOT EXISTS demo_customers (
    id        SERIAL PRIMARY KEY,
    name      VARCHAR(64) NOT NULL,
    region    VARCHAR(64),
    level     VARCHAR(16) DEFAULT '普通',
    join_date DATE DEFAULT CURRENT_DATE
);
COMMENT ON TABLE demo_customers IS '客户信息表';
COMMENT ON COLUMN demo_customers.id IS '客户ID';
COMMENT ON COLUMN demo_customers.name IS '客户姓名';
COMMENT ON COLUMN demo_customers.region IS '所在地区';
COMMENT ON COLUMN demo_customers.level IS '客户等级（普通/VIP/SVIP）';

-- 订单表
CREATE TABLE IF NOT EXISTS demo_orders (
    id          SERIAL PRIMARY KEY,
    customer_id INT REFERENCES demo_customers(id),
    product_id  INT REFERENCES demo_products(id),
    quantity    INT NOT NULL,
    amount      NUMERIC(12, 2),
    order_date  DATE NOT NULL,
    status      VARCHAR(16) DEFAULT '已完成'
);
COMMENT ON TABLE demo_orders IS '订单表';
COMMENT ON COLUMN demo_orders.id IS '订单ID';
COMMENT ON COLUMN demo_orders.customer_id IS '客户ID';
COMMENT ON COLUMN demo_orders.product_id IS '产品ID';
COMMENT ON COLUMN demo_orders.quantity IS '购买数量';
COMMENT ON COLUMN demo_orders.amount IS '订单金额（元）';
COMMENT ON COLUMN demo_orders.order_date IS '下单日期';
COMMENT ON COLUMN demo_orders.status IS '订单状态（已完成/已取消/进行中）';

-- ==============================
-- 示例数据
-- ==============================

-- 产品
INSERT INTO demo_products (name, category, price, stock) VALUES
('笔记本电脑 Pro', '电子产品', 8999.00, 150),
('无线鼠标', '电子产品', 129.00, 500),
('机械键盘', '电子产品', 599.00, 300),
('办公椅', '办公家具', 1299.00, 80),
('显示器 27寸', '电子产品', 2499.00, 200),
('打印机', '办公设备', 1599.00, 60),
('投影仪', '办公设备', 3999.00, 40),
('文件柜', '办公家具', 899.00, 120),
('台灯', '办公用品', 199.00, 400),
('白板', '办公用品', 299.00, 150)
ON CONFLICT DO NOTHING;

-- 客户
INSERT INTO demo_customers (name, region, level, join_date) VALUES
('张三', '华东', 'VIP', '2024-01-15'),
('李四', '华北', '普通', '2024-03-20'),
('王五', '华南', 'SVIP', '2023-11-08'),
('赵六', '华东', '普通', '2024-06-01'),
('孙七', '西南', 'VIP', '2024-02-14'),
('周八', '华北', '普通', '2024-07-22'),
('吴九', '华南', 'VIP', '2024-04-10'),
('郑十', '西北', '普通', '2024-08-05')
ON CONFLICT DO NOTHING;

-- 订单（2024年7月-2025年2月，模拟半年数据）
INSERT INTO demo_orders (customer_id, product_id, quantity, amount, order_date, status) VALUES
(1, 1, 2, 17998.00, '2024-07-05', '已完成'),
(2, 3, 1, 599.00, '2024-07-12', '已完成'),
(3, 5, 3, 7497.00, '2024-07-20', '已完成'),
(1, 2, 5, 645.00, '2024-08-03', '已完成'),
(4, 4, 1, 1299.00, '2024-08-15', '已完成'),
(5, 1, 1, 8999.00, '2024-08-22', '已完成'),
(3, 6, 2, 3198.00, '2024-09-01', '已完成'),
(6, 9, 3, 597.00, '2024-09-10', '已完成'),
(2, 7, 1, 3999.00, '2024-09-18', '已取消'),
(7, 10, 2, 598.00, '2024-10-05', '已完成'),
(1, 3, 3, 1797.00, '2024-10-15', '已完成'),
(8, 8, 1, 899.00, '2024-10-25', '已完成'),
(5, 5, 2, 4998.00, '2024-11-03', '已完成'),
(3, 1, 1, 8999.00, '2024-11-12', '已完成'),
(4, 2, 10, 1290.00, '2024-11-20', '已完成'),
(6, 4, 2, 2598.00, '2024-12-01', '已完成'),
(7, 6, 1, 1599.00, '2024-12-10', '已完成'),
(2, 1, 1, 8999.00, '2024-12-18', '已完成'),
(1, 7, 1, 3999.00, '2025-01-05', '已完成'),
(8, 3, 2, 1198.00, '2025-01-15', '已完成'),
(5, 9, 5, 995.00, '2025-01-22', '已完成'),
(3, 5, 1, 2499.00, '2025-02-01', '进行中'),
(4, 10, 3, 897.00, '2025-02-10', '进行中')
ON CONFLICT DO NOTHING;

-- ==============================
-- 扩展现有表（添加项目/用户关联）
-- ==============================
ALTER TABLE table_meta ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES project(id) ON DELETE CASCADE;
ALTER TABLE table_meta ADD COLUMN IF NOT EXISTS schema_text TEXT;
ALTER TABLE table_meta ADD COLUMN IF NOT EXISTS embedding vector(1024);
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES project(id) ON DELETE CASCADE;
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES sys_user(id) ON DELETE SET NULL;
