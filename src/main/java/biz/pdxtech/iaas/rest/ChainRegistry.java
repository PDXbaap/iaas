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

package biz.pdxtech.iaas.rest;

import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.ChainDeletion;
import biz.pdxtech.iaas.entity.ChainUpdate;
import biz.pdxtech.iaas.proto.vo.NodePortVO;
import biz.pdxtech.iaas.proto.vo.TrustChainHostVO;
import biz.pdxtech.iaas.hazelcast.CacheManager;
import biz.pdxtech.iaas.proto.resp.Result;
import biz.pdxtech.iaas.proto.block.Block;
import biz.pdxtech.iaas.repository.*;
import biz.pdxtech.iaas.service.impl.ChainService;
import biz.pdxtech.iaas.service.common.ChainManager;
import biz.pdxtech.iaas.util.HttpUtil;
import biz.pdxtech.iaas.util.JsonUtil;
import biz.pdxtech.iaas.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Path("/chain")
public class ChainRegistry {

    @Autowired
    private ChainRepository chainRepository;
    @Autowired
    private ChainService chainService;
    @Autowired
    ChainDeletionRepository chainDeletionRepository;
    @Autowired
    AddressRepository addressRepository;
    @Autowired
    ChainCreationRepository chainCreationRepository;
    @Autowired
    ChainManager chainManager;
    @Autowired
    CacheManager cacheManager;

    @Value("${pdx.iaas.root-trust-chain.name}")
    private String rootTrustChainName;


    @POST
    @Path("/block")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response block(@QueryParam("chainId") Long chainId, Block block, @Context HttpServletRequest request) {

        //token: miner address, chain-id, blockhash, timestamp, signed by miner.
        String ip = HttpUtil.getIpByRequest(request);
        if (block.getHeader() == null) {
            Result result = Result.builder().status(500).data("block header is null").build();
            return Response.status(500).entity(result).build();
        }

        ThreadUtil.execute(() -> {
            chainService.processBlock(ip, chainId, block);
        });

        Result result = Result.builder().status(200).data("done").build();
        return Response.status(200).entity(result).build();

    }


    @POST
    @Path("/staticNodes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response staticNodes(NodePortVO model, @Context HttpServletRequest request) {

        String ip = HttpUtil.getIpByRequest(request);
        boolean exist = cacheManager.checkCacheForStaticNode(ip, model.getChainId(), "staticNodes");
        if (!exist) {
            return null;
        }
        log.info("Request StaticNodes >> IP:{}, ChainId:{} >> P2pPort:{}, RpcPort:{} \n", HttpUtil.getIpByRequest(request), model.getChainId(), model.getP2pPort(), model.getRpcPort());
        List<String> collect = chainService.getStaticNodes(model, ip);
        log.info("Request StaticNodes >> IP:{}, ChainId:{} >> data:{} \n", ip, model.getChainId(), JsonUtil.objToJson(collect));
        Result result = Result.builder().status(200).data(collect).build();
        return Response.status(200).entity(result).build();

    }


    @PUT
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@QueryParam("chain") String chain, Chain entity) {

        Result result;
        try {
            chainService.submit(ChainUpdate.builder().chain(entity).type(ChainUpdate.Type.SUBMITTED).time(System.currentTimeMillis()).build());
            result = Result.builder().status(200).data("done").build();
        } catch (Exception e) {
            result = Result.builder().status(500).data("fail").build();
        }

        return Response.status(200).entity(result).build();
    }


    @DELETE
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@QueryParam("chainName") String chainName) {

        Chain chain = chainRepository.findChainByNameAndStatIsNot(chainName, Chain.Stat.REMOVED);
        //save request status
        ChainDeletion request = ChainDeletion.builder().chain(chain).type(ChainDeletion.Type.SUBMITTED).time(System.currentTimeMillis()).build();
        chainDeletionRepository.save(request);

        chainService.submit(request);
        Result result = Result.builder().status(200).data("done").build();
        return Response.status(200).entity(result).build();

    }


    @GET
    @Path("/hosts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response hosts(@QueryParam("chainId") long chainId, @Context HttpServletRequest request) {

        String ip = HttpUtil.getIpByRequest(request);

        boolean exist = cacheManager.checkCache(ip, chainId, "hosts");
        if (!exist){
            Result result = Result.builder().status(200).data(new ArrayList<>()).build();
            return Response.status(200).entity(result).build();
        }

        log.info("Request Hosts >> chainid:{}, ip:{} \n", chainId, ip);
        List<String> hosts = chainService.getHosts(chainId, ip);
        log.info("Request Hosts >> chainid:{}, ip:{}, result:{}\n", chainId, ip, JsonUtil.objToJson(hosts));
        Result result = Result.builder().status(200).data(hosts).build();
        return Response.status(200).entity(result).build();

    }


    @GET
    @Path("/tchosts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response tchosts(@QueryParam("chainId") long chainId, @Context HttpServletRequest request) {

        String ip = HttpUtil.getIpByRequest(request);

        boolean exist = cacheManager.checkCache(ip, chainId, "tchosts");
        if (!exist) {
            Result result = Result.builder().status(300).data(null).build();
            return Response.status(200).entity(result).build();
        }

        log.info("Request Trust Chain Hosts >> chainid:{}, ip:{} \n", chainId, ip);

        TrustChainHostVO vo = chainService.getTrustChainHosts(chainId, ip);
        if (vo.getChainId().equals("")) {
            Result result = Result.builder().status(300).data(vo).build();
            log.info("Request Trust Chain Hosts >> chainid:{}, ip:{}, status:{}, result:{} \n", chainId, ip, "300", JsonUtil.objToJson(vo));
            return Response.status(200).entity(result).build();
        }

//        TrustChainHostVO vo = TrustChainHostVO.builder().build();
//
//        Chain upperChain = chainRepository.findChainByChainId(chainId);
//
//        if (rootTrustChainName.equals(upperChain.getName())) {
//            vo.setChainId("");
//            vo.setHosts(new ArrayList<>());
//            Result result = Result.builder().status(300).data(vo).build();
//            log.info("Request Trust Chain Hosts >> chainid:{}, ip:{}, status:{}, result:{} \n", chainId, ip, "300", JsonUtil.objToJson(vo));
//            return Response.status(200).entity(result).build();
//        }
//
//        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndStatIsNot(upperChain.getLowerChainId(), Chain_Node.Stat.REMOVED);
//        List<Chain_Node> activeChainNodes = chainManager.getActiveChainNodes(chainNodes, 10);
//        List<String> hosts = activeChainNodes.stream().map(chainNode -> "http://" + chainNode.getNode().getIp() + ":" + chainNode.getRpcPort()).collect(Collectors.toList());
//        vo.setChainId(upperChain.getLowerChainId().toString());
//        vo.setHosts(hosts);
        log.info("Request Trust Chain Hosts >> chainid:{}, ip:{}, result:{} \n", chainId, ip, JsonUtil.objToJson(vo));
        Result result = Result.builder().status(200).data(vo).build();
        return Response.status(200).entity(result).build();

    }


}
