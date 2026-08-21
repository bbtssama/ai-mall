package com.aimall.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.user.bean.Address;
import com.aimall.user.dto.AddressRequest;
import com.aimall.user.dto.AddressVO;
import com.aimall.user.mapper.AddressMapper;
import com.aimall.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    @Override
    public List<AddressVO> list() {
        return addressMapper.selectByUserId(currentUserId()).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO add(AddressRequest req) {
        Long userId = currentUserId();
        boolean wantDefault = req.isDefault() || addressMapper.countByUserId(userId) == 0;
        if (wantDefault) {
            // 若设为默认（或首条地址），先把已有地址默认位清零
            addressMapper.unsetDefault(userId);
        }
        Address addr = new Address();
        addr.setUserId(userId);
        addr.setReceiver(req.getReceiver().trim());
        addr.setPhone(req.getPhone().trim());
        addr.setProvince(req.getProvince() == null ? null : req.getProvince().trim());
        addr.setCity(req.getCity() == null ? null : req.getCity().trim());
        addr.setDetail(req.getDetail().trim());
        addr.setIsDefault(wantDefault ? 1 : 0);
        addressMapper.insert(addr);
        return toVO(addr);
    }

    @Override
    public AddressVO update(Long id, AddressRequest req) {
        ensureOwned(id);
        Address addr = new Address();
        addr.setId(id);
        addr.setUserId(currentUserId());
        addr.setReceiver(req.getReceiver().trim());
        addr.setPhone(req.getPhone().trim());
        addr.setProvince(req.getProvince() == null ? null : req.getProvince().trim());
        addr.setCity(req.getCity() == null ? null : req.getCity().trim());
        addr.setDetail(req.getDetail().trim());
        addressMapper.update(addr);
        return toVO(addressMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        Long userId = currentUserId();
        Address addr = addressMapper.selectByIdAndUserId(id, userId);
        if (addr == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        boolean wasDefault = addr.getIsDefault() != null && addr.getIsDefault() == 1;
        if (addressMapper.deleteById(id, userId) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        // 删除的是默认地址时，把剩余首条自动设为默认
        if (wasDefault) {
            List<Address> rest = addressMapper.selectByUserId(userId);
            if (!rest.isEmpty()) {
                addressMapper.unsetDefault(userId);
                addressMapper.updateDefault(rest.get(0).getId(), userId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        Long userId = currentUserId();
        if (addressMapper.selectByIdAndUserId(id, userId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        addressMapper.unsetDefault(userId);
        addressMapper.updateDefault(id, userId);
    }

    private void ensureOwned(Long id) {
        if (addressMapper.selectByIdAndUserId(id, currentUserId()) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
    }

    private AddressVO toVO(Address a) {
        AddressVO vo = new AddressVO();
        vo.setId(a.getId());
        vo.setReceiver(a.getReceiver());
        vo.setPhone(a.getPhone());
        vo.setProvince(a.getProvince());
        vo.setCity(a.getCity());
        vo.setDetail(a.getDetail());
        vo.setIsDefault(a.getIsDefault() != null && a.getIsDefault() == 1);
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }
}