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


import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.Chain_Node;
import biz.pdxtech.iaas.proto.resp.Result;
import biz.pdxtech.iaas.proto.vo.ChainInfoVo;
import biz.pdxtech.iaas.proto.vo.ChainNodeInfoVO;
import biz.pdxtech.iaas.repository.ChainNodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@RestController
@RequestMapping(value = "/webui/monitor")
public class MonitorWebUI {

    @Autowired
    private ChainNodeRepository chainNodeRepository;

    @GetMapping(value = "/chains")
    public Response chains() {

        Iterable<Chain_Node> all = chainNodeRepository.findAll();
        List<ChainInfoVo> chainInfoList = new ArrayList<>();

        Stream<Chain_Node> stream = StreamSupport.stream(all.spliterator(), false);
        Map<Chain, List<Chain_Node>> collect = stream.collect(Collectors.groupingBy(Chain_Node::getChain));

        Iterator<Map.Entry<Chain, List<Chain_Node>>> iterator = collect.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Chain, List<Chain_Node>> next = iterator.next();
            Chain key = next.getKey();
            List<Chain_Node> value = next.getValue();

            List<ChainNodeInfoVO> chainNodeInfoVOList = new ArrayList<>();
            for (Chain_Node cn : value) {
                ChainNodeInfoVO chainNodeInfo = ChainNodeInfoVO.builder().address(cn.getNode().getAddr().getAddr()).ip(cn.getNode().getIp()).rpcPort(Optional.ofNullable(cn.getRpcPort()).orElse(0)).status(cn.getStat().name()).build();
                chainNodeInfoVOList.add(chainNodeInfo);
            }
            ChainInfoVo chainInfo = ChainInfoVo.builder().chainId(key.getChainId().toString()).chainType(key.getType().name()).chainNodes(chainNodeInfoVOList).build();
            chainInfoList.add(chainInfo);
        }

        Result result = Result.builder().status(200).data(chainInfoList).build();
        return Response.status(200).entity(result).build();
    }




}
