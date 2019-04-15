/*************************************************************************
 * Copyright (C) 2016-2019 PDX Technologies, Inc. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *************************************************************************/

package biz.pdxtech.iaas.web;

import biz.pdxtech.iaas.entity.ChainOrder;
import biz.pdxtech.iaas.entity.User;
import biz.pdxtech.iaas.proto.resp.RespCode;
import biz.pdxtech.iaas.repository.ChainOrderRepository;
import biz.pdxtech.iaas.proto.resp.RespEntity;
import biz.pdxtech.iaas.rest.ChainRegistry;
import biz.pdxtech.iaas.rest.UserRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping(value = "/webui/order")
public class OrderWebUI {

    @Autowired
    ChainOrderRepository chainOrderRepository;

    @Autowired
    ChainRegistry chainRegistry;

    @Autowired
    UserRegistry userRegistry;

    @ResponseBody
    @RequestMapping("/confirm")
    public RespEntity confirm(@RequestParam(value = "token") String token,
                              @RequestParam(value = "orderId") String orderId,
                              @RequestParam(value = "orderType") int orderType) {

        User user = userRegistry.getTokenUser(token);

        if (user == null) {
            return RespEntity.error(RespCode.TOKEN_IS_ERROR); // token验证失败
        }

        ChainOrder chainOrder = chainOrderRepository.findByUuidAndUserId(orderId, user.getId());

        if (chainOrder == null || chainOrder.getType().ordinal() != orderType) {
            return RespEntity.error(RespCode.ORDER_NOT_USER); // 订单用户不匹配
        }

        // TODO 查询是否已经支付费用
//        double patFee = 12.1;
//        if (true) {
//            chainOrder.setStat(ChainOrder.Stat.DEF_SURE);
//            chainOrderRepository.save(chainOrder);
//            log.info("Order is sure success ,send task to chainserver for create");
//
//            // 发送开始创建链
//            chainRegistry.create(chainOrder.getChain());
//        }

        return RespEntity.success();
    }

}
