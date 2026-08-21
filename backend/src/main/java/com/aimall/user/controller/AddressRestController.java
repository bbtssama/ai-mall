package com.aimall.user.controller;

import com.aimall.common.api.R;
import com.aimall.user.dto.AddressRequest;
import com.aimall.user.dto.AddressVO;
import com.aimall.user.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口：列表 / 新增 / 修改 / 删除 / 设默认
 */
@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressRestController {

    private final AddressService addressService;

    @GetMapping
    public R<List<AddressVO>> list() {
        return R.ok(addressService.list());
    }

    @PostMapping
    public R<AddressVO> add(@RequestBody @Valid AddressRequest req) {
        return R.ok(addressService.add(req));
    }

    @PutMapping("/{id}")
    public R<AddressVO> update(@PathVariable Long id, @RequestBody @Valid AddressRequest req) {
        return R.ok(addressService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        addressService.remove(id);
        return R.ok();
    }

    @PutMapping("/{id}/default")
    public R<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(id);
        return R.ok();
    }
}