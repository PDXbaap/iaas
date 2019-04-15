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

import biz.pdxtech.iaas.common.exception.IaasServiceException;
import biz.pdxtech.iaas.entity.*;
import biz.pdxtech.iaas.proto.dto.TrustTxAddressUpdTxDTO;
import biz.pdxtech.iaas.proto.vo.ChainNodeUpdateVO;
import biz.pdxtech.iaas.hazelcast.CacheManager;
import biz.pdxtech.iaas.proto.Invoice;
import biz.pdxtech.iaas.proto.resp.Result;
import biz.pdxtech.iaas.repository.ChainDeletionRepository;
import biz.pdxtech.iaas.repository.ChainNodeRepository;
import biz.pdxtech.iaas.repository.ChainRepository;
import biz.pdxtech.iaas.repository.NodeRepository;
import biz.pdxtech.iaas.service.impl.TopologyService;
import biz.pdxtech.iaas.service.impl.ChainService;
import biz.pdxtech.iaas.service.impl.TrustTreeService;
import biz.pdxtech.iaas.service.common.ChainManager;
import biz.pdxtech.iaas.util.HttpUtil;
import biz.pdxtech.iaas.util.ThreadUtil;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@Path("/iaas")
public class IaasService {

    @Autowired
    private ChainService chainService;
    @Autowired
    ChainRepository chainRepository;
    @Autowired
    NodeRepository nodeRepository;
    @Autowired
    ChainNodeRepository chainNodeRepository;
    @Autowired
    ChainDeletionRepository chainDeletionRepository;
    @Autowired
    TrustTreeService trustTreeService;
    @Autowired
    TopologyService topologyService;
    @Autowired
    ChainManager chainManager;
    @Autowired
    CacheManager cacheManager;

    @Value("${pdx.iaas.root-trust-chain.name}")
    private String rootTrustChainName;
    @Value("${pdx.iaas.basic-service-chain.name}")
    private String basicServiceChainName;


    @POST
    @Path("/orchestrate_chain_creation_request")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orchestrateChainCreation(Chain entity) throws IOException {

        ChainCreation req = ChainCreation.builder().chain(entity).time(System.currentTimeMillis()).build();
        this.chainService.submit(req);
        Result result = Result.builder().status(102).data("processing").build();
        return Response.status(102).entity(result).build();

    }

    @POST
    @Path("/orchestrate_chain_update_request")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orchestrateChainUpdate(Chain entity) throws IOException {

        ChainUpdate req = ChainUpdate.builder().chain(entity).time(System.currentTimeMillis()).build();
        this.chainService.submit(req);
        Result result = Result.builder().status(102).data("processing").build();
        return Response.status(102).entity(result).build();

    }

    @POST
    @Path("/orchestrate_chain_deletion_request")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response orchestrateChainDeletion(Chain entity) throws IOException {

        ChainDeletion req = ChainDeletion.builder().chain(entity).time(System.currentTimeMillis()).build();
        this.chainService.submit(req);
        Result result = Result.builder().status(102).data("processing").build();
        return Response.status(102).entity(result).build();
    }

    @POST
    @Path("/forward_iaas_payment")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response forwardIaasPayment(@QueryParam("ref") String ref, byte[] tx) throws IOException {
        return null;
    }

    @GET
    @Path("/check_chain_creation_progress")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkChainCreationStatus(@QueryParam("chain") String chain) throws IOException {

        Chain findChain = chainRepository.findChainByName(chain);
        Result result = Result.builder().status(200).data(findChain).build();
        return Response.status(200).entity(result).build();

    }


    @POST
    @Path("/update_chain_node_status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateChainNodeStatus(ChainNodeUpdateVO model, @Context HttpServletRequest request) {

        Result result;
        String ip = HttpUtil.getIpByRequest(request);
        log.info("Update ChainNode Status >> chainId:{}, nodeIP:{}, status:{} \n ", model.getChainId(), ip, model.getStatus());


        boolean flag = cacheManager.checkCacheForUpdate(ip, Long.valueOf(model.getChainId()), model.getStatus());
        if (!flag){
            return null;
        }

        // check chain & node
        Chain chain = chainRepository.findChainByChainId(Long.valueOf(model.getChainId()));
        Node node = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);

        if (Objects.isNull(node)) {
            log.warn("Not Found Node!");
            throw new IaasServiceException("Not Found Node!");
        }
        if (Objects.isNull(chain)) {
            log.warn("Not Found Chain!");
            throw new IaasServiceException("Not Found Chain!");
        }

        ThreadUtil.execute(() -> {
            processChainNodeUpdateEvent(model, chain, node);
        });

        result = Result.builder().status(200).data("done").build();
        return Response.status(200).entity(result).build();

    }


    /**
     * 处理链节点更新事件
     *
     * @param vo    参数
     * @param chain 链
     * @param node  节点
     */
    private void processChainNodeUpdateEvent(ChainNodeUpdateVO vo, Chain chain, Node node) {
        String ip = node.getIp();

        if (basicServiceChainFirstCall(chain)) {
            log.info("Basic Service Chain First Update >> ChainId:{}, NodeIP:{}, Status:{} \n ", vo.getChainId(), ip, vo.getStatus());
            chainService.onChainNodeEvent(chain, node, Chain_Node.Stat.IN_SERVICE);

            boolean active = chainManager.existActiveNode(chain);
            while (!active) {
                ThreadUtil.sleep(2);
                active = chainManager.existActiveNode(chain);
            }

            Chain trustChain = topologyService.onNodeAdditionEvent();
            chainService.createSingleTrustChainNodeForBasicServiceChain(chain, trustChain, node);
            return;
        }

        switch (vo.getStatus()) {
            case "1":

                chainService.onChainNodeEvent(chain, node, Chain_Node.Stat.IN_SERVICE);

                if (chainFirstCall(chain)) {
                    log.info("Chain first node update, start assign lower trust chain >> chainId:{}, nodeIP:{}, status:{} \n ", vo.getChainId(), ip, vo.getStatus());
                    trustTreeService.sendAssignTrustChainTx(chain);
                } else {
                    log.info("Chain another node update, update assign lower trust chain >> chainId:{}, nodeIP:{}, status:{} \n ", vo.getChainId(), ip, vo.getStatus());
                    trustTreeService.sendUpdateAssignTrustChainTxToNewNode(chain, node);
                }

                Chain replacedChain = replaceTrustChainFirstCall(chain);
                if (Objects.nonNull(replacedChain)) {
                    log.info("Trust chain replaced biz chain >> trustChainId:{}, bizChainId:{}, nodeIP:{}, status:{} \n ", vo.getChainId(), replacedChain.getChainId(), ip, vo.getStatus());
                    trustTreeService.updateTrustTxAddressOnLowerChain(replacedChain, TrustTxAddressUpdTxDTO.Type.ADD);
                    trustTreeService.sendAssignTrustChainTx(replacedChain);

                    Chain lowerChain = chainRepository.findChainByChainId(chain.getLowerChainId());
                    List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(replacedChain, Chain_Node.Stat.REMOVED);
                    List<String> addressList = chainNodes.stream().map(Chain_Node::getNode).map(Node::getAddr).map(Address::getAddr).collect(Collectors.toList());
                    trustTreeService.updateTrustTxAddressOnLowerChain(addressList, lowerChain, TrustTxAddressUpdTxDTO.Type.DELETE);
                }

                //update node stat if its trust-chain
                if (chain.getType() == Chain.Type.TRUST_CHAIN && node.getStat() == Node.Stat.NOT_READY) {
                    log.info("Trust chain ready, node in-service >> chainId:{}, nodeIP:{}, status:{} \n ", vo.getChainId(), ip, vo.getStatus());
                    node.setStat(Node.Stat.IN_SERVICE);
                    nodeRepository.save(node);
                }

                if (rootTrustChainFirstCall(chain)) {
                    log.info("Root trust chain first update, start match basic service chain >> trustChainId:{}, nodeIP:{}, status:{} \n ", vo.getChainId(), ip, vo.getStatus());

                    node.setSizeService(1);
                    nodeRepository.save(node);

                    Chain basicServiceChain = chainRepository.findChainByName(basicServiceChainName);
                    trustTreeService.matchLowerTrustChain(basicServiceChain, chain);
                    trustTreeService.updateTrustTxAddressOnLowerChain(basicServiceChain, node.getAddr().getAddr(), TrustTxAddressUpdTxDTO.Type.ADD);
                    trustTreeService.sendAssignTrustChainTx(basicServiceChain);
                }
                break;
            case "2":
                chainService.onChainNodeEvent(chain, node, Chain_Node.Stat.REMOVED);
                trustTreeService.updateTrustTxAddressOnLowerChain(chain, node.getAddr().getAddr(), TrustTxAddressUpdTxDTO.Type.DELETE);
                //update node stat if its trust-chain
                if (chain.getType() == Chain.Type.TRUST_CHAIN) {
                    node.setStat(Node.Stat.UNCONTROLLED);
                    nodeRepository.save(node);
                }
                break;
            default:
                //Just For Test
                chainService.onChainNodeEvent(chain, node, Chain_Node.Stat.IN_SERVICE);
                break;
        }
    }

    private boolean rootTrustChainFirstCall_bak(Chain chain) {
        if (chain.getType() == Chain.Type.TRUST_CHAIN && chain.getName().equals(rootTrustChainName) && chain.getSizeService() == 1 && chain.getSizeChain() == 1) {
            return true;
        } else {
            return false;
        }
    }

    @Synchronized
    private boolean rootTrustChainFirstCall(Chain chain) {
        if (chain.getType() == Chain.Type.TRUST_CHAIN && chain.getName().equals(rootTrustChainName)) {
            Chain basicServiceChain = chainRepository.findChainByName(basicServiceChainName);
            if (Objects.isNull(basicServiceChain.getLowerChainId())) {
                return true;
            }else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean basicServiceChainFirstCall(Chain chain) {
        if (chain.getType() == Chain.Type.SERVICE_CHAIN && chain.getSizeService() == 1) {
            return true;
        } else {
            return false;
        }
    }

    private Chain replaceTrustChainFirstCall(Chain chain) {

        if (chain.getType() == Chain.Type.TRUST_CHAIN && !chain.getName().equals(rootTrustChainName) && chain.getSizeService() == 1 && chain.getSizeChain() == 1) {
            List<Chain> chains = chainRepository.findChainsByLowerChainIdIsAndStatIsNot(chain.getChainId(), Chain.Stat.REMOVED);
            if (!chains.isEmpty()) {
                return chains.get(0);
            } else {
                return null;
            }
        }
        return null;
    }

    @Synchronized
    private boolean chainFirstCall(Chain chain) {
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);
        chainNodes = chainNodes.stream().filter(chainNode -> chainNode.getStat() == Chain_Node.Stat.IN_SERVICE).collect(Collectors.toList());
        if (chainNodes.size() == 1) {
            return true;
        } else {
            return false;
        }
    }


}
