-- ============================================================
-- 商品图集种子（京东模式）
--   · 每 SKU 一组图集（多张，不同角度/实拍）
--   · product.main_img(封面) = 该商品第一个 SKU 的第一张图（列表快取）
--   · 详情页默认展示第一个 SKU 的图集；切 SKU 换整套图集
--   · 不再维护"商品级图"（sku_id NULL）—— 图都挂在 SKU 下
-- 幂等：先清空重建。
-- ============================================================
USE ai_mall;

-- 1) 每 SKU 4 张图（规格图集）
DELETE FROM t_product_image WHERE sku_id IS NOT NULL;
INSERT INTO t_product_image (product_id, sku_id, url, sort)
SELECT s.product_id, s.id,
       CONCAT('https://picsum.photos/seed/s', s.id, '-', n.n, '/480/480'),
       n.n
FROM t_product_sku s
JOIN (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) n;

-- 2) 删除商品级图（图全归 SKU）
DELETE FROM t_product_image WHERE sku_id IS NULL;

-- 3) SKU 首图冗余列（选中规格的默认展示 / 选择器缩略）
UPDATE t_product_sku s
SET image = CONCAT('https://picsum.photos/seed/s', s.id, '-1/480/480');

-- 4) 商品封面 = 第一个 SKU（id 最小）的第一张图
UPDATE t_product p
JOIN (
    SELECT s.product_id, MIN(s.id) AS first_sku_id
    FROM t_product_sku s
    GROUP BY s.product_id
) f ON f.product_id = p.id
SET p.main_img = CONCAT('https://picsum.photos/seed/s', f.first_sku_id, '-1/480/480');

-- 校验：SKU 图分布 + 封面示例
SELECT (SELECT COUNT(*) FROM t_product_image) AS images;
SELECT p.id, p.spu_name, p.main_img FROM t_product p ORDER BY p.id LIMIT 3;