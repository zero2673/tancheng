package com.tancheng.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.tancheng.dto.Result;
import com.tancheng.entity.ShopType;
import com.tancheng.mapper.ShopTypeMapper;
import com.tancheng.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key="shop:types";
        //1.从redis查询店铺类型
        List<String> list = stringRedisTemplate.opsForList().range(key, 0, -1);
        //2.判断是否存在
if (list!=null && !list.isEmpty()){
    //3.存在，直接返回
    List<ShopType> typeList = list.stream().map(json -> JSONUtil.toBean(json, ShopType.class)).collect(Collectors.toList());
    return  Result.ok(typeList);
}
        //4.不存在，查询数据库，并存入redis
        List<ShopType> typeList = this.lambdaQuery()
                .orderByAsc(ShopType::getSort)
                .list();
        List<String> list1 = typeList.stream().map(json -> JSONUtil.toJsonStr(json)).collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key,list1);
        //5.返回
return Result.ok(list1);
    }
}
