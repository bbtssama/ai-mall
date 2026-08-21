package com.aimall.user.service;

import com.aimall.user.dto.AddressRequest;
import com.aimall.user.dto.AddressVO;

import java.util.List;

/**
 * 收货地址服务：列表 / 新增 / 修改 / 删除 / 设默认
 */
public interface AddressService {

    List<AddressVO> list();

    AddressVO add(AddressRequest req);

    AddressVO update(Long id, AddressRequest req);

    void remove(Long id);

    void setDefault(Long id);
}