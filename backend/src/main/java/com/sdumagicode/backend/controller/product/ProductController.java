package com.sdumagicode.backend.controller.product;

import com.sdumagicode.backend.core.result.GlobalResult;
import com.sdumagicode.backend.core.result.GlobalResultGenerator;
import com.sdumagicode.backend.dto.ProductDTO;
import com.sdumagicode.backend.entity.Product;
import com.sdumagicode.backend.service.ProductService;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * Created on 2022/6/21 9:30.
 *
 * @author ronger
 * @email ronger-x@outlook.com
 * @packageName com.rymcu.forest.web.api.product
 */
@RestController
@RequestMapping("/api/v1/product")
@RequiresRoles(value = {"blog_admin", "admin"}, logical = Logical.OR)
public class ProductController {

    @Resource
    private ProductService productService;

    @GetMapping("/detail/{idProduct}")
    public GlobalResult<ProductDTO> detail(@PathVariable Long idProduct, @RequestParam(defaultValue = "2") Integer type) {
        ProductDTO dto = productService.findProductDTOById(idProduct, type);
        return GlobalResultGenerator.genSuccessResult(dto);
    }

    @PostMapping("/post")
    public GlobalResult<Product> add(@RequestBody ProductDTO product) {
        Product newProduct = productService.postProduct(product);
        return GlobalResultGenerator.genSuccessResult(newProduct);
    }

    @PutMapping("/post")
    public GlobalResult<Product> update(@RequestBody ProductDTO product) {
        if (product.getIdProduct() == null || product.getIdProduct() == 0) {
            throw new IllegalArgumentException("产品主键参数异常!");
        }
        Product oldProduct = productService.postProduct(product);
        return GlobalResultGenerator.genSuccessResult(oldProduct);
    }


    @PatchMapping("/update-status")
    @RequiresRoles(value = {"blog_admin", "admin"}, logical = Logical.OR)
    public GlobalResult<Boolean> updateStatus(@RequestBody Product product) {
        boolean flag = productService.updateStatus(product.getIdProduct(), product.getStatus());
        return GlobalResultGenerator.genSuccessResult(flag);
    }

}
