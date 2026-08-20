package com.aimall.goods.service.impl;

import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.common.page.PageQuery;
import com.aimall.common.page.PageResult;
import com.aimall.goods.dto.ProductVO;
import com.aimall.goods.dto.SkuVO;
import com.aimall.goods.bean.Product;
import com.aimall.goods.bean.ProductSku;
import com.aimall.goods.mapper.ProductMapper;
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

    @Override
    public PageResult<ProductVO> pageOnSale(PageQuery query) {
        long total = productMapper.countOnSale();
        List<ProductVO> records = productMapper
                .selectOnSalePage(query.getOffset(), query.getPageSize())
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
        vo.setSkus(skuMapper.selectByProductId(id).stream().map(this::toSkuVO).toList());
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