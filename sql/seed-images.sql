-- ============================================================
-- 商品图集种子：基于现有商品/SKU 生成多图外链（picsum seed）
-- 幂等：先清空图集与 sku.image，再生成。
-- ============================================================
USE ai_mall;

-- 1) 商品级图集（每商品 4 张，含用作默认主图的第一张）
DELETE FROM t_product_image WHERE sku_id IS NULL;
INSERT INTO t_product_image (product_id, sku_id, url, sort)
SELECT p.id, NULL,
       CONCAT('https://picsum.photos/seed/p', p.id, '-', n.n, '/480/480'),
       n.n
FROM t_product p
JOIN (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) n
WHERE p.status = 1;

-- 2) SKU 规格图：t_product_sku.image 冗余首图；t_product_image 存 sku 专属图（每 sku 1 张；实际可再扩展多张）
UPDATE t_product_sku s
JOIN t_product p ON p.id = s.product_id
SET s.image = CONCAT('https://picsum.photos/seed/s', s.id, '/480/480');

DELETE FROM t_product_image WHERE sku_id IS NOT NULL;
INSERT INTO t_product_image (product_id, sku_id, url, sort)
SELECT s.product_id, s.id, s.image, 1
FROM t_product_sku s;

-- 校验
SELECT COUNT(*) AS product_images FROM t_product_image WHERE sku_id IS NULL;
SELECT COUNT(*) AS sku_images FROM t_product_image WHERE sku_id IS NOT NULL;