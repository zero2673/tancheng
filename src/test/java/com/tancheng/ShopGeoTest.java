package com.tancheng;

import com.tancheng.entity.Shop;
import com.tancheng.service.IShopService;
import com.tancheng.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
public class ShopGeoTest {

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void loadShopGeoData() {
        // 1. 查询所有店铺
        List<Shop> shops = shopService.list();
        System.out.println("店铺总数：" + shops.size());

        // 2. 按 typeId 分组
        Map<Long, List<Shop>> map = shops.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));

        // 3. 分批写入 Redis GEO
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;  // 注意常量名
            List<Shop> value = entry.getValue();

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                // 注意：GeoLocation 的参数是 (name, Point(经度, 纬度))
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
            System.out.println("写入 key=" + key + "，条数=" + locations.size());
        }
        System.out.println("导入完成");
    }
}