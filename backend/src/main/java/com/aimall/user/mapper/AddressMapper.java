package com.aimall.user.mapper;

import com.aimall.user.bean.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收货地址 Mapper（SQL 见 resources/mapper/AddressMapper.xml）
 */
@Mapper
public interface AddressMapper {

    /** 用户地址列表（默认在前） */
    List<Address> selectByUserId(@Param("userId") Long userId);

    /** 用户地址数量 */
    long countByUserId(@Param("userId") Long userId);

    Address selectById(@Param("id") Long id);

    /** 带 userId 条件，天然校验归属 */
    Address selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 返回受影响行数，主键回填 address.id */
    int insert(Address address);

    /** 更新非默认字段（receiver/phone/province/city/detail），带归属校验 */
    int update(Address address);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    /** 把该用户所有地址置为非默认（设置默认前调用） */
    int unsetDefault(@Param("userId") Long userId);

    /** 把指定地址设为默认（带归属校验） */
    int updateDefault(@Param("id") Long id, @Param("userId") Long userId);
}