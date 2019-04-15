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

import biz.pdxtech.baap.driver.BlockchainDriverException;
import biz.pdxtech.iaas.cluster.ClusterRunner;
import biz.pdxtech.iaas.dao.ChainDao;
import biz.pdxtech.iaas.dao.NodeDao;
import biz.pdxtech.iaas.entity.*;
import biz.pdxtech.iaas.proto.dto.TokenChainDTO;
import biz.pdxtech.iaas.proto.dto.TrustTxAddressUpdTxDTO;
import biz.pdxtech.iaas.proto.vo.NodePortVO;
import biz.pdxtech.iaas.hazelcast.CacheManager;
import biz.pdxtech.iaas.hazelcast.CacheData;
import biz.pdxtech.iaas.proto.block.CommitExtra;
import biz.pdxtech.iaas.proto.block.Evidence;
import biz.pdxtech.iaas.proto.block.Block;
import biz.pdxtech.iaas.proto.vo.TrustChainHostVO;
import biz.pdxtech.iaas.repository.*;
import biz.pdxtech.iaas.service.ChainNodeEventListener;
import biz.pdxtech.iaas.service.common.ChainManager;
import biz.pdxtech.iaas.service.common.TxService;
import biz.pdxtech.iaas.util.JsonUtil;
import biz.pdxtech.iaas.util.RadixUtil;
import biz.pdxtech.iaas.util.ThreadUtil;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChainService implements CommandLineRunner, ChainNodeEventListener {

    @Autowired
    private ChainNodeRepository chainNodeRepository;
    @Autowired
    private ChainRepository chainRepository;
    @Autowired
    private ChainCreationRepository chainCreationRepository;
    @Autowired
    private ChainUpdateRepository chainUpdateRepository;
    @Autowired
    private ChainDeletionRepository chainDeletionRepository;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private ChainService chainService;
    @Autowired
    private TxService txService;
    @Autowired
    private TrustTreeService trustTreeService;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private ClusterRunner clusterRunner;
    @Autowired
    private ChainDao chainDao;
    @Autowired
    private NodeDao nodeDao;
    @Autowired
    private CacheManager cacheManager;

    @Value("${pdx.iaas.tc.node-size}")
    private int tcNodeSize;
    @Value("${pdx.iaas.tc.service-size}")
    private int tcServiceSize;
    @Value("${pdx.iaas.tc.layer}")
    private int tcLayer;
    @Value("${pdx.iaas.basic-service-chain.name}")
    private String basicServiceChainName;
    @Value("${pdx.iaas.root-trust-chain.name}")
    private String rootTrustChainName;
    @Value("${pdx.iaas.zk.znode.bizChain.firstNode}")
    private String bizChainFirstNode;
    @Value("${pdx.iaas.zk.znode.chain.firstNode}")
    private String chainFirstNode;
    @Value("${pdx.iaas.zk.znode.chain.secondNodes}")
    private String chainSecondNodes;

    ExecutorService executor = Executors.newFixedThreadPool(1);

    public void submit(ChainCreation req) {
        ChainCreation request = ChainCreation.builder().chain(req.getChain()).type(ChainCreation.Type.PROVISIONING).time(System.currentTimeMillis()).build();
        chainCreationRepository.save(request);
        executor.submit(() -> chainService.createChain(req));
        log.info("chain creation request submitted");
    }

    public void submit(ChainUpdate req) {
        executor.submit(() -> this.updateChain(req));
        log.info("chain update request submitted");
    }

    public void submit(ChainDeletion req) {
        ChainDeletion request = ChainDeletion.builder().chain(req.getChain()).type(ChainDeletion.Type.DE_PROVISIONING).time(System.currentTimeMillis()).build();
        chainDeletionRepository.save(request);
        executor.submit(() -> chainService.deleteChain(req));
        log.info("chain deletion request submitted");
    }


    /**
     * 创建链
     *
     * @param req 请求
     */
    private void createChain(ChainCreation req) {

        Chain chain = req.getChain();

        // select nodes by requirement
        int sizeDesiredNode = req.getChain().getSizeDesired();

        // normal select mode
        //List<Node> availableNodeList = nodeRepository.findNodesByStatIsNotOrderBySizeServiceAsc(Node.Stat.REMOVED);

        // filter trust-chain not evidence node mode
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChain_TypeIsAndStatIsNot(Chain.Type.TRUST_CHAIN, Chain_Node.Stat.REMOVED);
        List<Node> availableNodeList = chainNodes.stream().filter(Chain_Node::isEvidenced).map(Chain_Node::getNode).collect(Collectors.toList());
        availableNodeList.sort(Comparator.comparing(Node::getSizeService));

        List<Node> gainedNodeList = new ArrayList<>();

        try {
            for (Node node : availableNodeList) {
                if (sizeDesiredNode > 0) {
                    log.info("Select Nodes >> Chian got a new node >> chainId:{}, nodeId:{} ", chain.getChainId(), node.getId());

                    //update chain-node status
                    Chain_Node chain_node = Chain_Node.builder().chain(chain).node(node).createdAt(new Date()).stat(Chain_Node.Stat.NOT_READY).build();
                    chainNodeRepository.save(chain_node);

                    //update node sizeService
                    nodeDao.updateSizeService(node, 1);

                    //save gained nodes for sending tx
                    gainedNodeList.add(node);
                    sizeDesiredNode--;
                }
            }
        } catch (Exception e) {
            log.error("Error >> Create Chain >> ChainId:{}, error:{} \n", req.getChain().getChainId(), e);
        }

        //udpate chain
        chain.setSizeService(chain.getSizeService() + gainedNodeList.size());

        if (null == chain.getShareChainId()) {
            chain.setGenesis(chainManager.getCommonGenesisJson(String.valueOf(chain.getChainId()), chain.getCfd(), chain.getBlockDelay()));
        } else {
            Chain shareChain = chainRepository.findChainByChainId(chain.getShareChainId());
            TokenChainDTO dto = getTokenChainDTO(shareChain);
            chain.setGenesis(chainManager.getGenesisJsonWithTokenChain(String.valueOf(chain.getChainId()), JsonUtil.objToJson(Collections.singletonList(dto)), chain.getCfd(), chain.getBlockDelay()));
        }
        chainRepository.save(chain);

        //save create-chain-event
        if (sizeDesiredNode == 0) {
            ChainCreation request = ChainCreation.builder().chain(req.getChain()).type(ChainCreation.Type.NODES_MATCHED).time(System.currentTimeMillis()).build();
            chainCreationRepository.save(request);
        }

        // match lower chain & send white-list-tx
        log.info("Start assign chain & update white list ... \n");
        Chain bizLowerTC = trustTreeService.getLowerTrustChainForBizChain();
        log.info("Got Lower Trust Chain For Biz Chain >> ChainId:{}, LowerChainId:{} \n", chain.getChainId(), bizLowerTC.getChainId());
        trustTreeService.matchLowerTrustChain(chain, bizLowerTC);
        trustTreeService.updateTrustTxAddressOnLowerChain(chain, TrustTxAddressUpdTxDTO.Type.ADD);

        // send create-tx to create chain on every node
        log.info("Start send create chain tx ... \n");
        for (Node node : gainedNodeList) {
            try {
                txService.sendCreateChainTx(node, chain);
            } catch (BlockchainDriverException e) {
                log.error("Error >> Send Tx >> Create Chain >>  ChainId:{}, NodeId:{}, error:{} \n", req.getChain().getChainId(), node.getId(), e);
            } catch (Exception e) {
                log.error("Error >> Create Chain >> ChainId:{}, NodeId:{}, error:{} \n", req.getChain().getChainId(), node.getId(), e);
            }
        }
    }


    /**
     * 为服务链节点创建信任链
     *
     * @param serviceChain 服务链
     * @param trustChain   信任链
     * @param node         节点
     */
    public void createSingleTrustChainNodeForBasicServiceChain(Chain serviceChain, Chain trustChain, Node node) {

        log.info("Create trust chain for basic service chian >> serviceChainId:{}, trustChianId:{}, nodeIp:{} \n", serviceChain.getChainId(), trustChain.getChainId(), node.getIp());

        // save trust-chain-node
        Chain_Node chainNode = Chain_Node.builder().chain(trustChain).node(node).createdAt(new Date()).stat(Chain_Node.Stat.NOT_READY).build();
        chainNodeRepository.save(chainNode);

        // save trust-chain
        trustChain.setShareChainId(serviceChain.getChainId());
        Chain newChain = chainRepository.save(trustChain);

        Chain_Node oldChainNode = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(serviceChain, node, Chain_Node.Stat.REMOVED);

        try {
            txService.sendCreateTrustChainTx(oldChainNode, newChain);
        } catch (BlockchainDriverException e) {
            log.error("Error >> Send Tx >> Create Chain >> ChainId:{}, error:{} \n", trustChain.getId(), e);
        } catch (Exception e) {
            log.error("Error >> Create Chain >> ChainId:{}, error:{} \n", trustChain.getId(), e);
        }
    }


    /**
     * 处理区块
     *
     * @param ip      ip
     * @param chainId 链id
     * @param block   区块
     */
    public void processBlock(String ip, Long chainId, Block block) {

        boolean exsit = cacheManager.checkCache(ip, chainId, "block");
        if (!exsit){
            return;
        }

        boolean frequency = cacheManager.checkConfirmFrequency(ip, chainId);
        if (!frequency){
            return;
        }

        Chain chain = chainRepository.findChainByChainId(chainId);
        if (Objects.isNull(chain)) {
            return;
        }

        String commitHeight = block.getHeader().getNumber();
        CommitExtra commitExtra = block.getHeader().getBlockExtra().getCommitExtra();
        Boolean island = commitExtra.getIsland();
        Object[] evidences = Arrays.stream(commitExtra.getEvidences()).map(Evidence::getAddress).toArray();
        if (chain.getSizeService().equals(evidences.length)) {
            log.info("confirm block >> chainId:{}, ip:{} >> island:{}, height:{}, amount:{} ", chainId, ip, island, RadixUtil.hexToBigDecimal(commitHeight), evidences.length);
        } else {
            log.info("confirm block >> chainId:{}, ip:{} >> island:{}, height:{}, amount:{}, evidenceNodes:{}", chainId, ip, island, RadixUtil.hexToBigDecimal(commitHeight), evidences.length, evidences);
        }

        // consensus-node-status,observatory-nodes-status,condensed-evidence,block-high
        List<Evidence> evidenceList = Arrays.asList(block.getHeader().getBlockExtra().getCommitExtra().getEvidences());

        try {
            chain.setCommitHeight(Long.valueOf(RadixUtil.hexToBigDecimal(commitHeight).toString()));
            chain.setIslandState(island);
            chainRepository.save(chain);

            // update all condense-node update time
            List<Chain_Node> updChainNodes = new ArrayList<>();
            for (Evidence evidence : evidenceList) {
                Chain_Node chainNode = chainNodeRepository.findChain_NodeByChain_ChainIdAndNode_Addr_AddrAndStatIsNot(chainId, evidence.getAddress(), Chain_Node.Stat.REMOVED);
                if (Objects.isNull(chainNode)) {
                    return;
                }
                chainNode.setEvidenced(true);
                chainNode.setConnected(true);
                chainNode.setUpdatedAt(new Date());
                updChainNodes.add(chainNode);
            }
            if (!updChainNodes.isEmpty()) {
                chainNodeRepository.saveAll(updChainNodes);
            }
        } catch (Exception e) {
            log.error("commit block error : {}", e);
        }
    }


    /**
     * 更新链
     *
     * @param req 请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateChain(ChainUpdate req) {
        try {
            chainRepository.save(req.getChain());
            chainUpdateRepository.save(req);
            req.setType(ChainUpdate.Type.SUBMITTED);
        } catch (Exception e) {
            log.error("Error >> update chain >> chainId:{}, chainOwner:{} \n", req.getChain().getChainId(), req.getChain().getOwner());
        }
    }


    /**
     * 删除链
     *
     * @param req 请求
     */
    private void deleteChain(ChainDeletion req) {

        Chain chain = req.getChain();
        List<Chain_Node> chainNodeList = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);

        try {
            // update chain status
            chain.setStat(Chain.Stat.REMOVED);
            chainRepository.save(chain);

            // update lower-trust-chain
            Chain lowerTrustChain = chainRepository.findChainByChainId(chain.getLowerChainId());
            chainDao.updateSizeChain(lowerTrustChain, -1);
            chainRepository.save(lowerTrustChain);

            // save deletion request
            ChainDeletion chainDeletion = ChainDeletion.builder().chain(chain).type(ChainDeletion.Type.DE_PROVISIONING).time(System.currentTimeMillis()).build();
            chainDeletionRepository.save(chainDeletion);

            //update nodes sizeService if its biz-chain
            for (Chain_Node chainNode : chainNodeList) {
                if (chain.getType().equals(Chain.Type.BIZ_CHAIN)) {
                    Node node = chainNode.getNode();
                    nodeDao.updateSizeService(node, -1);
                }

            }

        } catch (Exception e) {
            log.error("Error >> Delete Chain >> Chain_Id:{}, Chain_Owner:{}, error:{} \n", chain.getChainId(), chain.getOwner(), e);
            //TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }

        // get trust-chain to send TX
        for (Chain_Node chainNode : chainNodeList) {
            try {
                txService.sendDeleteChainTx(chainNode.getNode(), chain);
            } catch (Exception e) {
                log.error("Error >> Delete Chain Send-Tx >> ChainId:{}, ChainOwner:{}, Error{} \n", chain.getChainId(), chain.getOwner(), e);

            }
        }

    }


    /**
     * 处理链节点状态事件
     *
     * @param chain 链
     * @param node  节点
     * @param stat  状态
     */
    @Override
    public void onChainNodeEvent(String chain, String node, Chain_Node.Stat stat) {
        Chain chainEntity = chainRepository.findChainByName(chain);
        Node nodeEntity = nodeRepository.findNodeByAddr_Addr(node);
        onChainNodeEvent(chainEntity, nodeEntity, stat);
    }


    /**
     * 处理链节点状态事件
     *
     * @param chain 链
     * @param node  节点
     * @param stat  状态
     */
    public void onChainNodeEvent(Chain chain, Node node, Chain_Node.Stat stat) {

        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);
        List<Chain_Node> collect = chainNodes.stream().filter(cn -> cn.getNode().getId().equals(node.getId())).limit(1).collect(Collectors.toList());

        if (collect.isEmpty()) {
            log.warn("Not Found Chain-Node >> Chain:{}, Node:{} \n", chain, node);
            return;
        }

        Chain_Node chainNode = collect.get(0);

        switch (stat) {
            case IN_SERVICE:
                //update chain-node status
                chainNode.setUpdatedAt(new Date());
                chainNode.setConnected(true);
                chainNode.setStat(Chain_Node.Stat.IN_SERVICE);
                chainNodeRepository.save(chainNode);

                log.info("Chain Started On Node, ChainId:{}, NodeId:{} ", chain.getChainId(), node.getId());

                //udpate chain status
                boolean isFinished = true;
                int inServiceNodeAmout = 0;

                for (Chain_Node cn : chainNodes) {
                    if (cn.getStat() != Chain_Node.Stat.IN_SERVICE) {
                        isFinished = false;
                    } else {
                        inServiceNodeAmout++;
                    }
                }

                if (isFinished && chain.getSizeService() >= chain.getSizeDesired()) {
                    //save create-event status
                    chainCreationRepository.save(ChainCreation.builder().chain(chain).type(ChainCreation.Type.COMPLETED).time(System.currentTimeMillis()).build());
                    log.info("Chain-Start Finished ! >> ChainId:{} \n", chain.getChainId());
                } else {
                    log.info("Chain is Starting on Nodes, ChainId:{}, DesiredNodeAmount:{}, InServiceNodeAmount:{} \n", chain.getChainId(), chain.getSizeDesired(), inServiceNodeAmout);
                }

                if (inServiceNodeAmout >= (chain.getSizeDesired() / 3 * 2) && chain.getStat() == Chain.Stat.NOT_READY) {
                    chain.setStat(Chain.Stat.IN_SERVICE);
                    chainRepository.save(chain);
                    log.info("Chain-Start Finished 2/3, Chain Is In Service! >> ChainId:{}, DesiredNodeAmount:{}, InServiceNodeAmount:{} \n", chain.getChainId(), chain.getSizeDesired(), inServiceNodeAmout);
                }
                break;

            case REMOVED:
                // update chain
                chainDao.updateSizeService(chain, -1);
                // Not back status at this version
                //if (chain.getStat() == Chain.Stat.IN_SERVICE && (chain.getSizeService() < chain.getSizeDesired())) {
                //    chain.setStat(Chain.Stat.NOT_READY);
                //}
                chainRepository.save(chain);

                // update chain-node status
                chainNode.setStat(Chain_Node.Stat.REMOVED);
                chainNode.setRemovedAt(new Date());
                chainNodeRepository.save(chainNode);

                log.info("Chain Removed on Node, ChainId:{}, NodeId:{} \n", chain.getChainId(), node.getId());

                // update chain status
                if (chainNodes.size() == 1) {
                    if (chain.getStat() != Chain.Stat.REMOVED) {
                        log.info("No Node On Chain Now! >> ChainId:{} \n", chain.getChainId());
                    }
                    if (chain.getStat() == Chain.Stat.REMOVED) {
                        // save request status
                        chainDeletionRepository.save(ChainDeletion.builder().chain(chain).type(ChainDeletion.Type.COMPLETED).time(System.currentTimeMillis()).build());
                        log.info("Chain-Remove Finished ! >> ChainId:{} \n", chain.getChainId());
                    }
                } else {
                    log.info("Chain is Removing, ChainId:{}, InServiceNodes:{} \n", chain.getChainId(), chainNodes.size() - 1);
                }
                break;

            case NOT_READY:
                break;

            default:
                log.info("onChainNodeEvent Chain_Node.Stat : No Stat! \n");

        }
    }


    /**
     * 判断节点是否正在同步中
     *
     * @param chainNode 链节点
     * @return
     */
    public boolean nodeSynchronizing(Chain_Node chainNode) {

        String key = chainNode.getChain().getChainId() + chainNode.getNode().getIp() + "nodesync";

        String value = (String) CacheData.getInstance().getCacheData().get(key);
        String curNumber = txService.queryBlockCommitNumber(chainNode);

        if (Objects.isNull(value)) {
            if (Objects.isNull(curNumber)) {
                log.info("Node sync >> can not get current commit height >> chainId:{}, nodeIP:{} ", chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                return false;
            } else {
                CacheData.getInstance().getCacheData().put(key, RadixUtil.hexToBigDecimal(curNumber).toString() + "-" + System.currentTimeMillis());
                log.info("Node sync >> first get current commit height >> currentHeight:{}, chainId:{}, nodeIP:{} ", curNumber, chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                return true;
            }
        } else {
            String[] strings = value.split("-");
            String lastNumber = strings[0];
            String lastTime = strings[1];

            long span = System.currentTimeMillis() - Long.valueOf(lastTime);
            int timeout = chainNode.getChain().getCfd() * (chainNode.getChain().getBlockDelay() + 300);

            if (span < timeout) {
                log.info("Node sync >> synchronizing ...  >> currentHeight:{}, chainId:{}, nodeIP:{} ", curNumber, chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                return true;
            }

            BigDecimal bdCurNumber = RadixUtil.hexToBigDecimal(curNumber);
            BigDecimal bdLastNumber = new BigDecimal(lastNumber);
            if (bdCurNumber.compareTo(bdLastNumber) == 1) {
                log.info("Node sync >> commit height increase >> currentHeight:{}, chainId:{}, nodeIP:{} ", curNumber, chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                return true;
            } else {
                log.info("Node sync >> commit height not increase >> currentHeight:{}, chainId:{}, nodeIP:{} ", curNumber, chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                return false;
            }
        }
    }


    /**
     * 获取基础服务链节点
     *
     * @return 链节点
     */
    public Chain_Node getBasicServiceChainNode() {
        Chain basicBizChain = chainRepository.findChainByName(basicServiceChainName);
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(basicBizChain, Chain_Node.Stat.REMOVED);
        return chainManager.getActiveChainNode(chainNodes);
    }


    /**
     * 根据链id获取RPC连接
     *
     * @param chainId 链id
     * @return rpc
     */
    public String getRpcHostByChainId(Long chainId) {
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndStatIsNot(chainId, Chain_Node.Stat.REMOVED);
        Chain_Node activeChainNode = chainManager.getActiveChainNode(chainNodes);
        if (activeChainNode != null) {
            return "http://" + activeChainNode.getNode().getIp() + ":" + activeChainNode.getRpcPort();
        } else {
            return null;
        }
    }


    /**
     * 根据链id获取活跃节点
     *
     * @param chainId 链id
     * @return 活跃节点
     */
    public Chain_Node getActiveChainNodeByChainId(Long chainId) {
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndStatIsNot(chainId, Chain_Node.Stat.REMOVED);
        return chainManager.getActiveChainNode(chainNodes);
    }


    /**
     * 获取tokenChain
     *
     * @param chain 链
     * @return
     */
    public TokenChainDTO getTokenChainDTO(Chain chain) {
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);
        chainNodes = chainNodes.stream().filter(chainNode -> chainNode.getStat() == Chain_Node.Stat.IN_SERVICE).collect(Collectors.toList());
        String[] enodeList = chainNodes.stream().map(chainNode -> chainNode.getNode().getEnode() + "@" + chainNode.getNode().getIp() + ":" + chainNode.getRpcPort()).toArray(String[]::new);
        String[] hostList = chainNodes.stream().map(chainNode -> "http://" + chainNode.getNode().getIp() + ":" + chainNode.getRpcPort()).toArray(String[]::new);
        return TokenChainDTO.builder().chainId(chain.getChainId().toString()).chainOwner(chain.getOwner().getAddr()).enodes(enodeList).rpcHosts(hostList).tokenSymbol(chain.getName()).build();
    }


    /**
     * 获取static node数据
     *
     * @param model 端口参数
     * @param ip    节点ip
     * @return static nodes
     */
    public List<String> getStaticNodes(NodePortVO model, String ip) {

        Node node = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);

        Chain_Node chainNode = chainNodeRepository.findChain_NodeByChain_ChainIdAndNode_IdAndStatIsNot(model.getChainId(), node.getId(), Chain_Node.Stat.REMOVED);

        chainNode.setP2pPort(model.getP2pPort());
        chainNode.setRpcPort(model.getRpcPort());
        chainNodeRepository.save(chainNode);

        if (chainNode.getChain().getType() == Chain.Type.TRUST_CHAIN) {
            return processTrustChainStaticNodes(chainNode.getChain(), ip);
        }

        boolean first = isFirstNodeOnZookeeper(model.getChainId().toString());
        boolean initializing = isInitializing(chainNode.getChain());
        if (first && initializing) {
            return new ArrayList<>();
        }

        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndStatIsNot(model.getChainId(), Chain_Node.Stat.REMOVED);
        chainNodes = chainNodes.stream().filter(cn -> cn.getRpcPort() != null).filter(cn -> !cn.getNode().getIp().equals(ip)).collect(Collectors.toList());
        int time = 60;
        while (chainNodes.isEmpty() && time > 0) {
            ThreadUtil.sleep(1);
            chainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndStatIsNot(model.getChainId(), Chain_Node.Stat.REMOVED);
            chainNodes = chainNodes.stream().filter(cn -> cn.getRpcPort() != null).filter(cn -> !cn.getNode().getIp().equals(ip)).limit(10).collect(Collectors.toList());
            Collections.shuffle(chainNodes);
            if (chainNodes.size() == 10) {
                break;
            }
            if (chainNode.getChain().getSizeService() > 5 && (chainNodes.size() * 3) < (chainNode.getChain().getSizeService() * 2)) {
                chainNodes.clear();
            }
            time--;
        }

        return chainNodes.stream().map(cn -> cn.getNode().getEnode() + "@" + cn.getNode().getIp() + ":" + cn.getP2pPort()).collect(Collectors.toList());

    }


    /**
     * 处理信任链StaticNode
     *
     * @param chain 链
     * @param ip    ip
     * @return
     */
    private List<String> processTrustChainStaticNodes(Chain chain, String ip) {
        boolean first = isFirstNodeOnZookeeper(chain.getName());
        if (first) {
            return new ArrayList<>();
        } else {
            List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndNode_IpIsNotAndStatIsNot(chain.getChainId(), ip, Chain_Node.Stat.REMOVED);
            chainNodes = chainNodes.stream().filter(cn -> cn.getRpcPort() != null).filter(cn -> !cn.getNode().getIp().equals(ip)).limit(10).collect(Collectors.toList());
            Collections.shuffle(chainNodes);
            int time = 30;
            while (chainNodes.isEmpty() && time > 0) {
                ThreadUtil.sleep(1);
                time--;
                chainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndNode_IpIsNotAndStatIsNot(chain.getChainId(), ip, Chain_Node.Stat.REMOVED);
            }
            return chainNodes.stream().map(cn -> cn.getNode().getEnode() + "@" + cn.getNode().getIp() + ":" + cn.getP2pPort()).collect(Collectors.toList());
        }
    }


    /**
     * 判断链是否在初始化中
     *
     * @param chain 链
     * @return
     */
    public boolean isInitializing(Chain chain) {
        long span = (System.currentTimeMillis() - chain.getCreatedAt().getTime()) / (1000 * 60);
        return (span < 10);
    }


    /**
     * zookeeper该路径下首个节点
     *
     * @param path 路径
     * @return
     */
    @Synchronized
    public boolean isFirstNodeOnZookeeper(String path) {
        try {
            Stat stat = clusterRunner.exists(chainFirstNode + "/" + path);
            if (null == stat) {
                clusterRunner.create(chainFirstNode + "/" + path, "".getBytes(), CreateMode.EPHEMERAL);
                log.info("First node for chain >> chain:{} ", path);
                return true;
            } else {
                log.info("Another node for chain >> chain:{}", path);
                return false;
            }
        } catch (KeeperException | InterruptedException e) {
            log.error("Error >> zookeeper get chian first node error:{} ", e);
            Thread.currentThread().interrupt();
        }
        return false;
    }


    /**
     * 获取hosts列表
     *
     * @param chainId 链id
     * @param ip      IP地址
     * @return
     */
    public List<String> getHosts(Long chainId, String ip) {

        Chain chain = chainRepository.findChainByChainId(chainId);
        List<Chain_Node> activeChainNodes = chainManager.getActiveChainNodes(chain, 5);
        return activeChainNodes.stream().map(chainNode -> "http://" + chainNode.getNode().getIp() + ":" + chainNode.getRpcPort()).collect(Collectors.toList());

    }

    public TrustChainHostVO getTrustChainHosts(Long chainId, String ip){

        TrustChainHostVO vo = TrustChainHostVO.builder().build();

        Chain upperChain = chainRepository.findChainByChainId(chainId);
        if (rootTrustChainName.equals(upperChain.getName())) {
            vo.setChainId("");
            vo.setHosts(new ArrayList<>());
            return vo;
        }

        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndStatIsNot(upperChain.getLowerChainId(), Chain_Node.Stat.REMOVED);
        List<Chain_Node> activeChainNodes = chainManager.getActiveChainNodes(chainNodes, 10);
        List<String> hosts = activeChainNodes.stream().map(chainNode -> "http://" + chainNode.getNode().getIp() + ":" + chainNode.getRpcPort()).collect(Collectors.toList());
        vo.setChainId(upperChain.getLowerChainId().toString());
        vo.setHosts(hosts);
        return vo;
    }


    @Override
    public void run(String... args) throws Exception {
        log.info("Chain service started !");
    }
}

