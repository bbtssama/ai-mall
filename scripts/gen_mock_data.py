#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模拟数据生成器（V2）—— 100 商品 + 500 笔记 + 互动数据 + RAG 索引 SQL

用法：
    python scripts/gen_mock_data.py --out sql/mock_data.sql

生成物：可直接执行的 MySQL 脚本（幂等：INSERT IGNORE / ON DUPLICATE KEY）。
数据特点（贴合"种草商城"的真实分布，而不是均匀假数据）：
    - 8 个品类 × 不等量商品（数码多、家居少——像真实 mall）
    - 笔记正文由「场景开头 + 商品体验 + 优缺点 + 标签」模板库组合生成，
      同一商品多篇笔记的口碑有正面也有吐槽（RAG 语料才有信息量）
    - 互动数走幂律分布（少数爆款 + 长尾），热门榜才像真的
"""
import argparse
import random
import uuid
from datetime import datetime, timedelta

random.seed(20260906)  # 固定种子：可复现

# ---------------------------------------------------------------- 品类与商品库
CATEGORIES = [
    (101, "数码影音", ["真无线降噪耳机", "蓝牙音箱", "智能手表", "电竞耳机", "运动耳机", "头戴式耳机"], 22),
    (102, "手机配件", ["氮化镓充电器", "磁吸无线充", "快充数据线", "手机壳", "车载支架", "移动电源"], 16),
    (103, "美妆个护", ["保湿唇釉", "气垫粉底", "卸妆膏", "精华液", "防晒霜", "香水"], 14),
    (104, "智能穿戴", ["智能手环", "智能手表", "运动腕表", "儿童电话手表"], 10),
    (105, "居家生活", ["香薰机", "收纳盒", "保温杯", "桌面加湿器", "氛围灯"], 12),
    (106, "运动户外", ["瑜伽垫", "筋膜枪", "跳绳", "运动水壶", "骑行手套"], 10),
    (107, "文具手账", ["中性笔套装", "手账本", "桌面收纳架", "阅读架"], 8),
    (108, "宠物用品", ["宠物梳毛器", "猫爬架", "宠物饮水机", "狗狗玩具"], 8),
]

BRANDS = ["AirSound", "闪充宝", "云朵", "轻氧", "悦物", "山海", "白泽", "吱吱", "半糖", "原野"]
COLORS = ["曜石黑", "奶白色", "雾霾蓝", "樱花粉", "薄荷绿", "落日橙", "星光银"]
SUFFIX = ["Pro", "Max", "Mini", "Air", "Ultra", "青春版", "礼盒版", "2代", "3代", ""]

SCENE_OPEN = [
    "通勤地铁上每天两小时，", "入了半个月，几乎天天用，", "纠结了好久终于入手，",
    "朋友种草的，用了一周来还愿，", "对比了三家最后选了这个，", "生日礼物自己挑的，",
    "搬进新家第一批购入，", "健身房常客亲测，", "学生党预算有限认真做了功课，", "打工人下班治愈好物，",
]
EXPERIENCE_POS = [
    "降噪一开世界都安静了，地铁轰鸣声直接消失",
    "续航是真的顶，一周一充完全没焦虑",
    "上手比想象中轻，戴一天耳朵也不累",
    "颜值绝了，放桌面就是个摆件",
    "充电速度肉眼可见地快，洗个澡的功夫就满电",
    "皮肤敏感肌用着完全不闷痘",
    "音质对得起这个价位，低音下潜够深",
    "做工细节在线，缝隙均匀没有廉价感",
    "防水名不虚传，出汗淋雨都没出过问题",
    "连接速度秒连，切换设备也很顺滑",
]
EXPERIENCE_NEG = [
    "但是戴久了耳道有点胀，超过三小时要摘下来歇歇",
    "冬天金属外壳有点冰手",
    "包装过于朴素，送人得自己再包一下",
    "app 偶尔断连，重启就好，不算大毛病",
    "颜色比图片稍深一点，介意的注意下",
    "没有附送收纳袋，出门只能裸塞包里",
]
ENDING = [
    "总体这个价位很难找到对手，推荐给同需求的朋友～",
    "已经安利给两个同事了，都说真香。",
    "后续用久了再来追评，目前是满意的。",
    "不算完美但值回票价，理性种草！",
    "蹲一个活动的姐妹可以冲，早买早享受。",
    "个人体验供参考，适合自己的才是最好的。",
]
TAGS = ["数码好物", "平价好物", "通勤必备", "学生党", "真实测评", "自用推荐",
        "颜值党", "性价比", "开箱", "好物分享", "精致生活", "打工人日常"]
NICKNAMES = ["芝士奶盖", "山间清风", "小熊软糖", "晚风轻拾", "柠檬汽水", "阿远同学",
             "半夏微凉", "球球酱", "北岛信使", "酥梨", "喵呜不吃鱼", "木子李",
             "南山南", "糯米团子", "一只小笨蛋", "汽水味的风", "早起的虫儿", "鹿与森",
             "奶茶三分甜", "向日葵的日常", "柚子茶", "风里有诗", "发发呆", "躺平小能手",
             "斜杠青年", "爱跑步的胖子", "极简主义喵", "攒钱买相机", "宿舍好物研究员", "通勤两小时选手"]

USERS = 30           # 模拟用户数
NOTE_COUNT = 500     # 笔记数
PRODUCT_COUNT = 100  # 商品数


def gen_products():
    """生成商品 + SKU。返回 (product_sql_lines, products_info)"""
    products = []
    idx = 1000  # 商品 id 从 1000 起，避开 V1 种子数据的 1~4
    for cat_id, cat_name, type_names, count in CATEGORIES:
        for i in range(count):
            t = type_names[i % len(type_names)]
            brand = random.choice(BRANDS)
            suffix = random.choice(SUFFIX)
            name = f"{brand} {t} {suffix}".strip()
            sub = random.choice([
                "轻巧便携，日常通勤首选", "高性价比之选，闭眼入",
                "细节控狂喜，做工在线", "人气复购款，新手友好",
                "设计感拉满，送礼自用两相宜",
            ])
            detail = (f"{name}：{sub}。本款{t}采用{random.choice(['全新升级方案', '经典成熟方案', '旗舰同源方案'])}，"
                      f"支持{random.choice(['蓝牙5.3', '蓝牙5.4', 'Type-C快充', '磁吸无线充'])}，"
                      f"续航{random.choice(['8小时', '12小时', '24小时', '7天'])}，"
                      f"防水等级{random.choice(['IPX4', 'IPX5', 'IPX7'])}，"
                      f"机身重量约{random.randint(15, 300)}g。"
                      f"包装清单：主机x1、说明书x1、{random.choice(['收纳袋', '替换装', '充电线', '保修卡'])}。")
            price = round(random.uniform(29, 1299), 0) - 0.01 if random.random() > 0.1 else round(random.uniform(19, 99), 0) - 0.01
            products.append({
                "id": idx, "cat": cat_id, "name": name, "sub": sub,
                "detail": detail, "price": price,
                "img": f"https://picsum.photos/seed/p{idx}/480/480",
            })
            idx += 1
    # 截到目标数量
    return products[:PRODUCT_COUNT]


def gen_skus(products):
    """每个商品 1~3 个 SKU（颜色规格）"""
    sku_id = 5000
    skus = []
    for p in products:
        n_sku = random.choices([1, 2, 3], weights=[30, 50, 20])[0]
        colors = random.sample(COLORS, n_sku)
        for c in colors:
            skus.append({
                "id": sku_id, "product_id": p["id"], "name": c,
                "price": round(p["price"] + random.choice([0, 0, 20, 50]), 2),
                "stock": random.randint(50, 2000),
                "sales": random.randint(10, 3000),
            })
            sku_id += 1
    return skus


def gen_note_body(product):
    """模板组合生成笔记正文：场景 + 正面体验(2) + 负面体验(0~1) + 结尾"""
    pos = random.sample(EXPERIENCE_POS, 2)
    neg = random.choice(EXPERIENCE_NEG) if random.random() < 0.55 else ""
    body = (random.choice(SCENE_OPEN) + f"说说我手上这款{product['name']}的真实感受。\n\n"
            + f"先说优点：{pos[0]}；而且{pos[1]}。\n\n")
    if neg:
        body += f"也说说缺点：{neg}。\n\n"
    body += random.choice(ENDING)
    return body


def gen_notes(products):
    """生成 500 篇笔记。口碑分布：70% 单商品种草 / 30% 泛话题（也关联商品）"""
    notes = []
    note_id = 90000
    base_time = datetime(2026, 8, 20, 9, 0, 0)
    for i in range(NOTE_COUNT):
        p = random.choice(products)
        single = random.random() < 0.7
        title_pool = [
            f"{p['name']}两周真实体验，优缺点全说",
            f"被问爆了的{p['name'].split()[1]}，来交作业了",
            f"预算{int(p['price'])}内怎么选？我选了它",
            f"打工人的第一支{p['name'].split()[1]}，不后悔",
            f"关于{p['name'].split()[1]}，这篇讲全了",
        ]
        title = random.choice(title_pool)
        content = gen_note_body(p)
        # 时间：分布在过去 30 天，越近越密
        days_ago = int(random.triangular(0, 30, 2))
        created = base_time + timedelta(days=30 - days_ago,
                                        hours=random.randint(0, 23),
                                        minutes=random.randint(0, 59))
        # 互动数幂律分布：10% 爆款
        if random.random() < 0.10:
            like = random.randint(800, 5000)
            collect = random.randint(200, 1500)
            view = random.randint(5000, 30000)
        else:
            like = random.randint(3, 300)
            collect = random.randint(1, 80)
            view = random.randint(30, 2000)
        hot = like * 3 + collect * 5 + view
        notes.append({
            "id": note_id, "user_id": 200 + random.randint(1, USERS),
            "title": title, "content": content,
            "cover": f"https://picsum.photos/seed/n{note_id}/640/400",
            "like": like, "collect": collect, "view": view, "hot": hot,
            "created": created.strftime("%Y-%m-%d %H:%M:%S"),
            "product_id": p["id"],
            "tags": random.sample(TAGS, random.randint(2, 4)),
        })
        note_id += 1
    return notes


def esc(s):
    return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="sql/mock_data.sql")
    args = ap.parse_args()

    products = gen_products()
    skus = gen_skus(products)
    notes = gen_notes(products)

    lines = ["-- =====================================================",
             "-- ai-mall V2 模拟数据（生成器：scripts/gen_mock_data.py，种子 20260906 可复现）",
             f"-- 商品 {len(products)} / SKU {len(skus)} / 用户 {USERS} / 笔记 {len(notes)}",
             "-- 幂等：INSERT IGNORE，可重复执行",
             "-- 注意：笔记直接置为 PUBLISHED（模拟已审核通过的存量内容）；",
             "--       t_knowledge_* 由后端 /api/v1/ai/index-product 或启动后批量接口建索引。",
             "-- =====================================================",
             ""]

    # 用户（密码哈希为占位值：模拟用户仅用于内容归属展示，密码不可登录）
    lines.append("-- 模拟用户（密码哈希为占位，不可真实登录）")
    lines.append("INSERT IGNORE INTO t_user (id, username, password_hash, nickname, avatar, role, status) VALUES")
    rows = []
    for i in range(1, USERS + 1):
        uid = 200 + i
        nick = NICKNAMES[(i - 1) % len(NICKNAMES)]
        rows.append(f"({uid}, 'mock{uid}', '$2a$10$mockhashvalue{uid:03d}', '{nick}', "
                    f"'https://picsum.photos/seed/u{uid}/100/100', 0, 1)")
    lines.append(",\n".join(rows) + ";")
    lines.append("")

    # 商品
    lines.append(f"-- 商品 {len(products)} 件")
    lines.append("INSERT IGNORE INTO t_product (id, spu_name, sub_title, category_id, main_img, detail, status) VALUES")
    rows = [f"({p['id']}, '{esc(p['name'])}', '{esc(p['sub'])}', {p['cat']}, '{p['img']}', '{esc(p['detail'])}', 1)"
            for p in products]
    lines.append(",\n".join(rows) + ";")
    lines.append("")

    # SKU
    lines.append(f"-- SKU {len(skus)} 条")
    lines.append("INSERT IGNORE INTO t_product_sku (id, product_id, sku_name, price, stock, sales, version) VALUES")
    rows = [f"({s['id']}, {s['product_id']}, '{s['name']}', {s['price']}, {s['stock']}, {s['sales']}, 0)"
            for s in skus]
    lines.append(",\n".join(rows) + ";")
    lines.append("")

    # 笔记
    lines.append(f"-- 笔记 {len(notes)} 篇（直接 PUBLISHED：模拟已审核存量）")
    lines.append("INSERT IGNORE INTO t_note (id, user_id, title, cover, content, status, hot_score, like_count, collect_count, view_count, created_at) VALUES")
    rows = [f"({n['id']}, {n['user_id']}, '{esc(n['title'])}', '{n['cover']}', '{esc(n['content'])}', "
            f"'PUBLISHED', {n['hot']}, {n['like']}, {n['collect']}, {n['view']}, '{n['created']}')"
            for n in notes]
    lines.append(",\n".join(rows) + ";")
    lines.append("")

    # 笔记标签
    lines.append("-- 笔记标签")
    lines.append("INSERT IGNORE INTO t_note_tag (note_id, tag) VALUES")
    rows = [f"({n['id']}, '{t}')" for n in notes for t in n["tags"]]
    lines.append(",\n".join(rows) + ";")
    lines.append("")

    # 笔记关联商品
    lines.append("-- 笔记关联商品（种草清单）")
    lines.append("INSERT IGNORE INTO t_note_product (note_id, product_id, remark) VALUES")
    remarks = ["就是这个，冲", "自用同款", "性价比之选", "文中同款"]
    rows = [f"({n['id']}, {n['product_id']}, '{random.choice(remarks)}')" for n in notes]
    lines.append(",\n".join(rows) + ";")
    lines.append("")

    # 模拟点赞（每篇笔记 5~40 个模拟用户点过，只插关系不动计数——计数已冗余在 t_note）
    lines.append("-- 模拟点赞关系（抽样，仅让'红心回显'有数据）")
    lines.append("INSERT IGNORE INTO t_note_like (note_id, user_id) VALUES")
    rows = []
    for n in notes:
        k = min(random.randint(5, 40), USERS)
        for uid in random.sample(range(201, 201 + USERS), k):
            rows.append(f"({n['id']}, {uid})")
    lines.append(",\n".join(rows) + ";")
    lines.append("")

    # 审核流水（让存量笔记有"已通过"留痕）
    lines.append("-- 存量笔记审核流水（PASS 留痕）")
    lines.append("INSERT IGNORE INTO t_audit_record (biz_type, biz_id, biz_version, ai_result_json, status) VALUES")
    rows = [f"('NOTE', {n['id']}, 1, '{{\"pass\":true,\"reason\":\"模拟存量数据\",\"categories\":[]}}', 'PASS')"
            for n in notes]
    lines.append(",\n".join(rows) + ";")
    lines.append("")

    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print(f"已生成 {args.out}")
    print(f"  商品 {len(products)} / SKU {len(skus)} / 笔记 {len(notes)} / 用户 {USERS}")
    print("下一步：")
    print("  1) mysql -h<host> -uroot -p ai_mall < sql/mock_data.sql")
    print("  2) 启动后端后，POST /api/v1/ai/index-all 批量建 RAG 索引（或逐个 index-product）")


if __name__ == "__main__":
    main()
