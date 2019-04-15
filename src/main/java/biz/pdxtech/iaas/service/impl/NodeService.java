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

package biz.pdxtech.iaas.service.impl;

import biz.pdxtech.iaas.cluster.Partitioner;
import biz.pdxtech.iaas.common.exception.NodeServiceException;
import biz.pdxtech.iaas.dao.ChainDao;
import biz.pdxtech.iaas.entity.*;
import biz.pdxtech.iaas.proto.dto.TrustTxAddressUpdTxDTO;
import biz.pdxtech.iaas.proto.vo.NodeRegisterVO;
import biz.pdxtech.iaas.hazelcast.CacheManager;
import biz.pdxtech.iaas.repository.*;
import biz.pdxtech.iaas.service.common.ChainManager;
import biz.pdxtech.iaas.service.common.EmergencyService;
import biz.pdxtech.iaas.util.JsonUtil;
import biz.pdxtech.iaas.util.SnowflakeIdUtil;
import biz.pdxtech.iaas.util.ThreadUtil;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NodeService {


    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private TopologyService topologyService;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private ChainNodeRepository chainNodeRepository;
    @Autowired
    private ChainRepository chainRepository;
    @Autowired
    private EmergencyService emergencyService;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private TrustTreeService trustTreeService;
    @Autowired
    private ChainService chainService;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private DeployNodeRepository deployNodeRepository;
    @Autowired
    private ChainDao chainDao;
    @Autowired
    private CacheManager cacheManager;


    @Value("${imgsuffix}")
    private String imgsuffix;
    @Value("${chainImgPath}")
    private String chainImgPath;
    @Value("${tokenImgPath}")
    private String tokenImgPath;


    @Value("${pdx.iaas.basic-service-chain.name}")
    private String basicServiceChainName;
    @Value("${pdx.iaas.basic-service-chain.address}")
    private String basicServiceChainAddress;
    @Value("${pdx.iaas.basic-service-chain.node-size}")
    private String basicServiceChainNodeSize;
    @Value("${pdx.iaas.basic-service-chain.node-factor}")
    private String basicServiceChainNodeFactor;
    @Value("${pdx.iaas.root-trust-chain.name}")
    private String rootTrustChainName;
    @Value("${pdx.iaas.chain.cfd.default}")
    private int defaultCfd;
    @Value("${pdx.iaas.chain.block-delay.default}")
    private int defaultBlockDelay;


    /**
     * 节点注册
     *
     * @param vo 注册参数
     * @param ip IP地址
     * @return
     */
    public HashMap<String, String> register(NodeRegisterVO vo, String ip) {
        Node node = processNodeInfo(vo, ip);
        Chain chain = getChainForRegister();
        saveChainNode(vo, node, chain);

        if (chain.getType() == Chain.Type.TRUST_CHAIN && !rootTrustChainName.equals(chain.getName())) {
            trustTreeService.updateTrustTxAddressOnLowerChain(chain, node.getAddr().getAddr(), TrustTxAddressUpdTxDTO.Type.ADD);
        }

        if (null != chain.getIslandState() && chain.getIslandState()) {
            emergencyService.onIslandState(chain, chain, node);
        }

        return bulidMeta(chain, node);
    }


    /**
     * 处理节点信息
     *
     * @param vo 注册参数
     * @param ip IP地址
     * @return
     */
    private Node processNodeInfo(NodeRegisterVO vo, String ip) {
        Node exsitNode = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);
        if (Objects.isNull(exsitNode)) {
            cacheManager.update(ip);
            return saveNode(vo, ip);
        } else {
            log.info("Node register again, delete old node data ... \n");
            cacheManager.update(ip);
            deleteNode(exsitNode);
            return saveNode(vo, ip);
        }
    }


    /**
     * 保存节点信息
     *
     * @param vo 注册参数
     * @param ip IP地址
     * @return
     */
    private Node saveNode(NodeRegisterVO vo, String ip) {
        Address addr = addressRepository.save(Address.builder().addr(vo.getAddress()).createdAt(new Date()).build());
        Node entity = Node.builder()
                .ip(ip)
                .nodeKey(vo.getNodeKey())
                .minerKey(vo.getMinerKey())
                .enode(vo.getEnode())
                .stat(Node.Stat.NOT_READY)
                .sizeService(0)
                .addr(addr)
                .part(Partitioner.name2part(addr.getAddr())).build();
        return nodeRepository.save(entity);
    }


    /**
     * 获取链信息
     *
     * @return
     */
    private Chain getChainForRegister() {
        if (chainService.isFirstNodeOnZookeeper(basicServiceChainName)) {
            Address address = addressRepository.findAddressByAddr(basicServiceChainAddress);
            long id = SnowflakeIdUtil.getID();
            String genesisJson = chainManager.getBasicServiceChainGenesisJson(String.valueOf(id), defaultCfd, defaultBlockDelay);

            Chain basicServiceChain = Chain.builder()
                    .chainId(id)
                    .stack(Chain.Stack.PDX)
                    .stat(Chain.Stat.NOT_READY)
                    .sizeService(1)
                    .cfd(defaultCfd)
                    .blockDelay(defaultBlockDelay)
                    .genesis("")
                    .name(basicServiceChainName)
                    .owner(address)
                    .type(Chain.Type.SERVICE_CHAIN)
                    .part(Partitioner.name2part(basicServiceChainName))
                    .type(Chain.Type.SERVICE_CHAIN)
                    .imgUrl(chainImgPath + "/pdx_chain.jpg")
                    .sizeDesired(Integer.valueOf(basicServiceChainNodeSize) * Integer.valueOf(basicServiceChainNodeFactor))
                    .genesis(genesisJson)
                    .createdAt(new Date()).build();
            basicServiceChain = chainRepository.save(basicServiceChain);
            Asset asset = Asset.builder()
                    .chainId(basicServiceChain.getChainId())
                    .name("PDX")
                    .fullName("PDX")
                    .note("")
                    .imgUrl(tokenImgPath + "/pdx_token.jpg").build();
            assetRepository.save(asset);
            log.info("Create basic service chain >> chainId:{} ",id);
            return basicServiceChain;
        }
        return topologyService.onNodeAdditionEvent();
    }


    /**
     * 保存链节点
     *
     * @param vo    注册参数
     * @param node  节点
     * @param chain 链
     */
    private void saveChainNode(NodeRegisterVO vo, Node node, Chain chain) {
        Chain_Node chainNode = chainNodeRepository.findChain_NodeByChain_ChainIdAndNode_IdAndStatIsNot(chain.getChainId(), node.getId(), Chain_Node.Stat.REMOVED);
        if (chainNode != null) {
            log.info("Node register again, update chain node data ... \n");
            chainNode.setRpcPort(vo.getRpcPort());
            chainNode.setP2pPort(vo.getP2pPort());
            chainNode.setCreatedAt(new Date());
            chainNode.setStat(Chain_Node.Stat.NOT_READY);
            chainNode.setUpdatedAt(null);
        } else {
            chainNode = Chain_Node.builder()
                    .chain(chain).node(node)
                    .rpcPort(vo.getRpcPort())
                    .p2pPort(vo.getP2pPort())
                    .createdAt(new Date())
                    .stat(Chain_Node.Stat.NOT_READY).build();
        }
        chainNodeRepository.save(chainNode);
    }


    /**
     * 构建返回值
     *
     * @param chain 链
     * @param node  节点
     * @return
     */
    private HashMap<String, String> bulidMeta(Chain chain, Node node) {
        List<Chain_Node> chainNodes = chainManager.getActiveChainNodes(chain, 5);
        boolean first = chainService.isFirstNodeOnZookeeper(chain.getChainId().toString());
        boolean initializing = chainService.isInitializing(chain);
        String staticNodes;
        if (first && initializing) {
            staticNodes = "[]";
        } else {
            int time = 30;
            while (chainNodes.isEmpty() && time > 0) {
                ThreadUtil.sleep(1);
                time--;
                chainNodes = chainManager.getActiveChainNodes(chain, 5);
            }
            if (initializing && chainNodes.isEmpty()){
                chainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndNode_IpIsNotAndStatIsNot(chain.getChainId(),node.getIp(),Chain_Node.Stat.REMOVED);
            }
            List<String> enodeList = chainNodes.stream()
                    .filter(cn -> !cn.getNode().getId().equals(node.getId()))
                    .map(cn -> cn.getNode().getEnode() + "@" + cn.getNode().getIp() + ":" + cn.getP2pPort())
                    .collect(Collectors.toList());
            staticNodes = JsonUtil.objToJson(enodeList);
        }

        HashMap<String, String> meta = new HashMap<>();
        meta.put("chainId", String.valueOf(chain.getChainId()));
        meta.put("chainType", chain.getType().name());
        meta.put("engineType", chain.getStack().name().toLowerCase());
        meta.put("genesis", chain.getGenesis());
        meta.put("staticNodes", staticNodes);

        log.info("Node Register Meta >> chainId:{}, ip:{}, meta:{} \n", chain.getChainId(), node.getIp(), meta);
        return meta;
    }


    /**
     * 删除节点
     *
     * @param ip IP地址
     */
    public void deleteNode(String ip) {
        Node node = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);
        if (Objects.isNull(node)) {
            log.warn("Node Not Found!");
            throw new NodeServiceException("Delete Node >> Node Not Found!");
        }
        deleteNode(node);
    }


    /**
     * 删除节点
     *
     * @param node 节点
     */
    public void deleteNode(Node node) {

        // update node status
        node.setStat(Node.Stat.REMOVED);
        nodeRepository.save(node);

        // update node-chain status & chain service size
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByNodeAndStatIsNot(node, Chain_Node.Stat.REMOVED);
        for (Chain_Node chainNode : chainNodes) {
            chainNode.setStat(Chain_Node.Stat.REMOVED);
            chainNode.setRemovedAt(new Date());
            chainNodeRepository.save(chainNode);

            chainDao.updateSizeService(chainNode.getChain(), -1);
        }

        // update node-chaincode status
        List<Deploy_Node> deployNodes = deployNodeRepository.findDeploy_NodesByNode_IdAndStatIsNot(node.getId(), Deploy_Node.Stat.REMOVED);
        for (Deploy_Node deployNode : deployNodes) {
            deployNode.setStat(Deploy_Node.Stat.REMOVED);
            deployNodeRepository.save(deployNode);
        }
    }


}
