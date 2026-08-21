package com.aimall.goods.service.impl;

import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.common.page.PageResult;
import com.aimall.goods.dto.ProductQuery;
import com.aimall.goods.dto.ProductVO;
import com.aimall.goods.dto.SkuVO;
import com.aimall.goods.bean.Product;
import com.aimall.goods.bean.ProductSku;
import com.aimall.goods.mapper.ProductMapper;
import com.aimall.goods.mapper.ProductImageMapper;
import com.aimall.goods.mapper.ProductSkuMapper;
import com.aimall.goods.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductImageMapper productImageMapper;

    @Override
    public PageResult<ProductVO> pageOnSale(ProductQuery query) {
        // 关键词去空白，避免误传空格
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        long total = productMapper.countOnSale(keyword, query.getCategoryId());
        List<ProductVO> records = productMapper
                .selectOnSalePage(query.getOffset(), query.getPageSize(), keyword, query.getCategoryId())
                .stream()
                .map(this::toListVO)
                .toList();
        return PageResult.of(records, total, query.getPage(), query.getPageSize());
    }

    @Override
    public ProductVO detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        ProductVO vo = toListVO(product);
        vo.setDetail(product.getDetail());
        // 商品图集（sku_id IS NULL，含主图本身）；若图集为空则回退单张主图
        List<String> productImages = productImageMapper.selectProductImages(id);
        vo.setImages(productImages.isEmpty() ? List.of(product.getMainImg()) : productImages);
        // SKU 列表：规格专属图集（可能为空）
        vo.setSkus(skuMapper.selectByProductId(id).stream().map(sku -> {
            SkuVO sv = toSkuVO(sku);
            sv.setImage(sku.getImage());
            List<String> skuImages = productImageMapper.selectSkuImages(id, sku.getId());
            sv.setImages(skuImages);
            return sv;
        }).toList());
        return vo;
    }

    private ProductVO toListVO(Product p) {
        ProductVO vo = new ProductVO();
        vo.setId(p.getId());
        vo.setSpuName(p.getSpuName());
        vo.setSubTitle(p.getSubTitle());
        vo.setCategoryId(p.getCategoryId());
        vo.setMainImg(p.getMainImg());
        vo.setStatus(p.getStatus());
        vo.setMinPrice(p.getMinPrice());
        return vo;
    }

    private SkuVO toSkuVO(ProductSku sku) {
        SkuVO vo = new SkuVO();
        vo.setId(sku.getId());
        vo.setProductId(sku.getProductId());
        vo.setSkuName(sku.getSkuName());
        vo.setPrice(sku.getPrice());
        vo.setStock(sku.getStock());
        vo.setSales(sku.getSales());
        return vo;
    }
}