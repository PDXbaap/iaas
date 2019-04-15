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
import biz.pdxtech.iaas.amqp.QueMessage;
import biz.pdxtech.iaas.cluster.ClusterRunner;
import biz.pdxtech.iaas.cluster.Partitioner;
import biz.pdxtech.iaas.common.exception.TopologyServiceException;
import biz.pdxtech.iaas.dao.ChainDao;
import biz.pdxtech.iaas.dao.NodeDao;
import biz.pdxtech.iaas.entity.*;
import biz.pdxtech.iaas.proto.dto.TokenChainDTO;
import biz.pdxtech.iaas.proto.dto.TrustTxAddressUpdTxDTO;
import biz.pdxtech.iaas.repository.*;
import biz.pdxtech.iaas.service.NodeStatusEventListener;
import biz.pdxtech.iaas.service.common.ChainManager;
import biz.pdxtech.iaas.service.common.EmergencyService;
import biz.pdxtech.iaas.service.common.TxService;
import biz.pdxtech.iaas.util.JsonUtil;
import biz.pdxtech.iaas.util.SnowflakeIdUtil;
import biz.pdxtech.iaas.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TopologyService implements CommandLineRunner, NodeStatusEventListener {

    private final Partitioner partitioner;
    private final ClusterRunner clusterRunner;
    private final RabbitTemplate rabbitTemplate;
    private final ChainRepository chainRepository;
    private final NodeRepository nodeRepository;
    private final ChainNodeRepository chainNodeRepository;
    private final TrustTreeRepository trustTreeRepository;
    private final AddressRepository addressRepository;
    private final ChainService chainService;
    private final TxService txService;
    private final EmergencyService emergencyService;
    private final ChaincodeService chaincodeService;
    private final TrustTreeService trustTreeService;
    private final ChainManager chainManager;
    private final ChainDao chainDao;
    private final NodeDao nodeDao;

    @Autowired
    public TopologyService(NodeDao nodeDao, ChainDao chainDao, ChainManager chainManager, TrustTreeService trustTreeService, ChaincodeService chaincodeService, Partitioner partitioner, ClusterRunner clusterRunner, RabbitTemplate rabbitTemplate, ChainRepository chainRepository, NodeRepository nodeRepository, ChainNodeRepository chainNodeRepository, TrustTreeRepository trustTreeRepository, AddressRepository addressRepository, ChainService chainService, TxService txService, EmergencyService emergencyService) {
        this.partitioner = partitioner;
        this.clusterRunner = clusterRunner;
        this.rabbitTemplate = rabbitTemplate;
        this.chainNodeRepository = chainNodeRepository;
        this.chainService = chainService;
        this.nodeRepository = nodeRepository;
        this.chainRepository = chainRepository;
        this.trustTreeRepository = trustTreeRepository;
        this.addressRepository = addressRepository;
        this.txService = txService;
        this.emergencyService = emergencyService;
        this.chaincodeService = chaincodeService;
        this.trustTreeService = trustTreeService;
        this.chainManager = chainManager;
        this.chainDao = chainDao;
        this.nodeDao = nodeDao;
    }

    @Value("${pdx.iaas.zk.connectString}")
    String connectString;
    @Value("${pdx.iaas.zk.znode.group}")
    String iaasGroup;
    @Value("${pdx.iaas.zk.znode.trustChain.tailChains}")
    String trustChainTailChain;
    @Value("${pdx.iaas.zk.znode.trustChain.firstNode}")
    String trustChainFirstNode;
    @Value("${pdx.iaas.node.lowload.size}")
    private String nodeLowloadSize;
    @Value("${pdx.iaas.tc.node-size}")
    private int tcNodeSize;
    @Value("${pdx.iaas.tc.service-size}")
    private int tcServiceSize;
    @Value("${pdx.iaas.tc.layer}")
    private int tcLayer;
    @Value("${pdx.iaas.tc.root-factor}")
    private double rootFactor;
    @Value("${pdx.iaas.tc.normal-factor}")
    private double normalFactor;
    @Value("${pdx.iaas.basic-service-chain.name}")
    String basicServiceChainName;
    @Value("${pdx.iaas.address}")
    private String iaasAddress;
    @Value("${pdx.iaas.root-trust-chain.name}")
    private String rootTrustChainName;
    @Value("${pdx.iaas.chain.cfd.default}")
    private int defaultCfd;
    @Value("${pdx.iaas.chain.block-delay.default}")
    private int defaultBlockDelay;


    ExecutorService executor = Executors.newFixedThreadPool(1);

    private static final String DIVING_LINE = "-----------------------------------------------------------------------";

    @Override
    public Chain onNodeAdditionEvent() {
        int serviceSize = tcServiceSize;
        int layer = tcLayer;
        int nodeSize = tcNodeSize;

        log.info(DIVING_LINE);
        log.info(DIVING_LINE);

        for (int i = 0; i < layer; i++) {
            List<TrustTree> trustList = trustTreeRepository.findByChain_TypeAndLayer(Chain.Type.TRUST_CHAIN, i);

            double fullNode;
            if (i == 0) {
                fullNode = nodeSize * rootFactor;
            } else {
                fullNode = nodeSize * normalFactor;
            }
            Chain oldTrustChain = joinOldTrustChain(trustList, fullNode);
            if (Objects.nonNull(oldTrustChain)) {
                return oldTrustChain;
            }

            // 当前层满链数量
            int fullChain = (int) Math.pow(serviceSize, i);
            if (trustList.size() >= fullChain) {
                log.info("The layer is full >> current layer:{}, current chain size:{}, layer capacity:{} \n", i, trustList.size(), fullChain);
                continue;
            } else {
                log.info("The layer not full >> current layer:{}, current chain size:{}, layer capacity:{} \n", i, trustList.size(), fullChain);
            }

            int num = trustList.size();
            int zpart = num / 2;
            int ypart = num % 2;

            int idx;
            if (ypart == 0) {
                idx = zpart;
            } else {
                idx = fullChain - (zpart + ypart);
            }

            String trustChainName = "TC(" + i + "," + idx + ")";

            boolean first = chainService.isFirstNodeOnZookeeper(trustChainName);
            if (!first) {
                return joinNewTrustChain(trustChainName);
            }

            Chain newTrustChain = getNewTrustChain(trustChainName, (int) fullNode);

            log.info(DIVING_LINE);
            log.info(DIVING_LINE);

            int temp = i;
            ThreadUtil.execute(() -> processTrustTree(newTrustChain, temp, idx, serviceSize));
            return newTrustChain;
        }
        return null;
    }


    /**
     * 加入已有的信任链
     *
     * @param trustTrees   信任树
     * @param fullNodeSize 满节点数量
     * @return
     */
    private Chain joinOldTrustChain(List<TrustTree> trustTrees, double fullNodeSize) {
        for (TrustTree tt : trustTrees) {
            if (tt.getChain() != null && tt.getChain().getSizeService() < fullNodeSize) {
                Chain chain = tt.getChain();
                chain = chainDao.updateSizeService(chain, 1);
                log.info("Join trust chain >> chainName:{}, chainId:{}, serviceSize:{}", chain.getName(), chain.getChainId(),chain.getSizeService());
                return chain;
            }
        }
        log.info("Not found existent trust chain");
        return null;
    }


    /**
     * 加入新的信任链
     *
     * @param trustChainName 信任链名称
     * @return
     */
    private Chain joinNewTrustChain(String trustChainName) {
        Chain chain = chainRepository.findChainByName(trustChainName);
        int time = 30;
        while (Objects.isNull(chain) && time > 0) {
            ThreadUtil.sleep(1);
            time--;
            log.info("Waiting for join trust chain >> chainName:{}", trustChainName);
            chain = chainRepository.findChainByName(trustChainName);
        }
        if (Objects.isNull(chain)) {
            throw new TopologyServiceException("Can not join trust chain");
        }
        chainDao.updateSizeService(chain, 1);
        log.info("Join trust chain >> chainName:{}, chainId:{}", chain.getName(), chain.getChainId());
        return chain;
    }


    /**
     * 获取新的信任链
     *
     * @param name        信任链名称
     * @param sizeDesired 信任链数量
     * @return
     */
    private Chain getNewTrustChain(String name, int sizeDesired) {
        Address address = addressRepository.findAddressByAddr(iaasAddress);
        Chain chain = new Chain();
        chain.setType(Chain.Type.TRUST_CHAIN);
        chain.setStack(Chain.Stack.PDX);
        chain.setStat(Chain.Stat.IN_SERVICE);
        chain.setSizeDesired(sizeDesired);
        chain.setSizeChain(0);
        chain.setSizeService(1);

        chain.setName(name);
        chain.setPart(Partitioner.name2part(chain.getName()));
        chain.setOwner(address);
        chain.setChainId(SnowflakeIdUtil.getID());
        chain.setCfd(defaultCfd);
        chain.setBlockDelay(defaultBlockDelay);
        chain.setCreatedAt(new Date());

        Chain basicChain = chainRepository.findChainByName(basicServiceChainName);
        int time = 5;
        while (Objects.isNull(basicChain) && time > 0){
            ThreadUtil.sleep(1);
            time -- ;
            basicChain = chainRepository.findChainByName(basicServiceChainName);
        }
        if (Objects.isNull(basicChain)){
            throw new TopologyServiceException("Not found basic service chain");
        }

        TokenChainDTO tokenChainDTO = chainService.getTokenChainDTO(basicChain);

        chain.setShareChainId(basicChain.getChainId());
        chain.setGenesis(chainManager.getTrustChainGenesisJson(String.valueOf(chain.getChainId()), JsonUtil.objToJson(Collections.singletonList(tokenChainDTO)), defaultCfd, defaultBlockDelay));
        log.info("Create New Trust Chain >> chainName:{}, chainId:{}", chain.getName(), chain.getChainId());
        return chainRepository.save(chain);
    }


    /**
     * 处理信任树逻辑
     *
     * @param chain       链
     * @param layer       层数
     * @param index       索引
     * @param serviceSize 服务数量
     */
    private void processTrustTree(Chain chain, int layer, int index, int serviceSize) {
        if (layer == 0) {
            trustTreeRepository.save(TrustTree.builder().chain(chain).layer(layer).index(index).build());
            log.info("Join Trust Tree >> layer:{}, index:{} ", layer, index);
        } else {
            Chain tchain = chainRepository.findChainByNameAndType("TC(" + (layer - 1) + "," + (index / serviceSize) + ")", Chain.Type.TRUST_CHAIN);
            TrustTree trustTree = trustTreeRepository.findByChain(tchain);
            log.info("Trust Chain: {},  Trust Tree: {}", tchain, trustTree);
            trustTreeRepository.save(TrustTree.builder().chain(chain).layer(layer).index(index).parent(trustTree).build());
            log.info("Join Trust Tree >> layer:{}, index:{} ", layer, index);
        }
        if (!rootTrustChainName.equals(chain.getName()) && Objects.isNull(chain.getLowerChainId())) {
            Chain trustLowerChain = trustTreeService.getLowerTrustChainForTrustChain(chain);
            log.info("Got lower trust chain for trust chain >> chainId:{}, lowerChainId:{} \n", chain.getChainId(), trustLowerChain.getChainId());
            trustTreeService.matchLowerTrustChain(chain, trustLowerChain);
        }
    }


    /**
     * called by NodeRegistry.delete or housekeeping
     *
     * @param nodeAddress
     */
    @Override
    public void onNodeDeletionEvent(String nodeAddress) {

        List<Chain_Node> cnList = chainNodeRepository.findAllByNode_Addr_AddrAndStatIsNot(nodeAddress, Chain_Node.Stat.REMOVED);

        for (Chain_Node cn : cnList) {
            cn.setRemovedAt(new Date());
            cn.setStat(Chain_Node.Stat.REMOVED);
            chainNodeRepository.save(cn);

            Chain chain = cn.getChain();
            chainDao.updateSizeService(chain, -1);

            chainRepository.save(chain);

            Node node = cn.getNode();
            node.setStat(Node.Stat.REMOVED);
            nodeRepository.save(node);

            chainService.onChainNodeEvent(cn.getChain().getName(), cn.getNode().getAddr().getAddr(), Chain_Node.Stat.REMOVED);
        }

    }


    @Scheduled(initialDelay = 6000, fixedDelay = 120000)
    public void housekeeping() {

        //process-section
        long lwm = partitioner.getPartitionLWM();
        long hwm = partitioner.getPartitionHWM();

        //process trust-chain
        log.info("Houskeeping For Trust-Chain Started   >>>  My section is from {} to {} \n", lwm, hwm);
        long timeForTrustChain = System.currentTimeMillis();
        housekeepingForTrustChain(lwm, hwm);
        log.info("Houskeeping For Trust-Chain Finished  <<<  It takes {} seconds \n", (System.currentTimeMillis() - timeForTrustChain) / 1000);


        //process biz-chain
        log.info("Houskeeping For Biz-Chain Started   >>>  My section is from {} to {} \n", lwm, hwm);
        long timeForBizChain = System.currentTimeMillis();
        housekeepingForBizChain(lwm, hwm);
        log.info("Houskeeping For Biz-Chain Finished  >>>  It takes {} seconds \n", (System.currentTimeMillis() - timeForBizChain) / 1000);

    }


    /**
     * 信任链自组织
     *
     * @param lwm 负责区间开始值
     * @param hwm 负责区间结束值
     */
    private void housekeepingForTrustChain(long lwm, long hwm) {

        //process its every trust-chain
        List<Chain> trustChainList = chainRepository.findChainsByTypeAndStatIsNotOrderByIdDesc(Chain.Type.TRUST_CHAIN, Chain.Stat.REMOVED);
        List<Chain> toProcessTrustChainList = trustChainList.stream().filter(chain -> chain.getPart() >= lwm && chain.getPart() < hwm).collect(Collectors.toList());

        for (Chain chain : toProcessTrustChainList) {

            List<Node> deadNodeList = getDeadNodesByCheckChain(chain);

            //check actual-in-service nodes vs lack,dead
            int actualInServiceNodes = chain.getSizeService() - deadNodeList.size();

            log.info("TrustChain Healthy Status >> ChainId:{}, SizeDesired:{}, ActualInService:{}, DeadNodes:{} \n", chain.getChainId(), chain.getSizeDesired(), actualInServiceNodes, deadNodeList.size());

            if (actualInServiceNodes >= chain.getSizeDesired()) {
                continue;
            }

            //check lack nodes of chain
            int lackAmount = chain.getSizeDesired() - actualInServiceNodes;


            if (lackAmount > 0) {

                LinkedHashMap<Node, Chain> activeNodeMap = null;
                try {
                    activeNodeMap = getActiveNodesForTrustChain(chain);
                } catch (KeeperException | InterruptedException e) {
                    log.error("Error >> Get ActiveNodeMap Error >> ChainId:{}, error:{} \n", chain.getChainId(), e);
                }

                if (activeNodeMap == null || activeNodeMap.isEmpty()) {
                    log.warn("Not Found Active Nodes For TrustChain >> ChainId:{} \n", chain.getChainId());
                    continue;
                }


                //move active-nodes(s)
                Iterator<Map.Entry<Node, Chain>> iterator = activeNodeMap.entrySet().iterator();
                while (iterator.hasNext() && lackAmount > 0) {
                    Map.Entry<Node, Chain> entry = iterator.next();
                    Node activeNode = entry.getKey();
                    Chain oldChain = entry.getValue();

                    Chain_Node oldChainNode = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(oldChain, activeNode, Chain_Node.Stat.REMOVED);

                    tranferNodeForTrustChain(oldChain, chain, activeNode);

                    try {
                        transferNodeByTx(oldChainNode, chain);
                    } catch (Exception e) {
                        log.error("Error >> Send Tx >> Transfer Node For TrustChain >> OldChainId:{}, error:{} \n", chain.getChainId(), e);
                    }

                    if (null != chain.getIslandState() && chain.getIslandState()) {
                        emergencyService.onIslandState(oldChainNode.getChain(), chain, oldChainNode.getNode());
                    }

                    lackAmount--;
                }

                //not get enough active nodes for the trust-chain
                if (lackAmount > 0) {
                    log.info("Not Enough Active Nodes For Trust Chain >> ChianId:{}, LackAmount:{}", chain.getChainId(), lackAmount);
                }
            }

        }
    }


    /**
     * 为当前信任链获取活跃节点
     *
     * @param chain 信任链
     * @return 活跃节点-对应信任链
     */
    private LinkedHashMap<Node, Chain> getActiveNodesForTrustChain(Chain chain) throws KeeperException, InterruptedException {

        LinkedHashMap<Node, Chain> activeNodeMap = new LinkedHashMap<>();

        // get chains by iaas num
        List<String> children = clusterRunner.getChildren(iaasGroup);
        Page<Chain> chains = chainRepository.findChainsByTypeAndStatIsNotAndSizeServiceGreaterThanOrderByIdDesc(Chain.Type.TRUST_CHAIN, Chain.Stat.REMOVED, 0, PageRequest.of(0, 2 * children.size()));

        // filter younger chains
        List<Chain> collect = chains.stream().filter(c -> c.getId() > chain.getId()).collect(Collectors.toList());

        // get 2 chain from tail-trust-chain
        int acquiredAmount = 2;
        List<Chain> acquiredChains = new ArrayList<>();

        // create zk node
        if (null == clusterRunner.exists(trustChainTailChain)) {
            clusterRunner.create(trustChainTailChain, "".getBytes(), CreateMode.PERSISTENT);
        }

        // assign chains with other iaas
        for (Chain c : collect) {
            Stat stat = clusterRunner.exists(trustChainTailChain + "/" + c.getChainId());
            if (null == stat && acquiredAmount > 0) {
                clusterRunner.create(trustChainTailChain + "/" + c.getChainId(), "".getBytes(), CreateMode.EPHEMERAL);
                acquiredChains.add(c);
                acquiredAmount--;
                log.info("The Front-Trust-Chain Got A Tail-Trust-Chain For Transfer Nodes , RearChainId:{} \n", c.getChainId());
            }
            if (acquiredAmount == 0) {
                break;
            }
        }

        // build node-chain map for transferByIaaS
        for (Chain c : acquiredChains) {
            List<Node> nodeList = chainNodeRepository.findChain_NodesByChainAndStatIsNotOrderByIdDesc(c, Chain_Node.Stat.REMOVED).stream().map(Chain_Node::getNode).collect(Collectors.toList());
            for (Node n : nodeList) {
                activeNodeMap.put(n, c);
            }
        }

        return activeNodeMap;
    }


    /**
     * 信任链间移动节点
     *
     * @param oldChain 节点原有链
     * @param newChain 节点新链
     * @return
     */
    private void tranferNodeForTrustChain(Chain oldChain, Chain newChain, Node node) {
        Chain_Node chainNode = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(oldChain, node, Chain_Node.Stat.REMOVED);

        chainNode.setRemovedAt(new Date());
        chainNode.setStat(Chain_Node.Stat.REMOVED);
        chainNodeRepository.save(chainNode);

        chainDao.updateSizeService(oldChain, -1);

        chainNodeRepository.save(Chain_Node.builder().chain(newChain).node(node).stat(Chain_Node.Stat.NOT_READY).createdAt(new Date()).build());
        chainDao.updateSizeService(newChain, 1);
    }


    /**
     * 替换节点所属信任链
     *
     * @param oldChainNode 原信任链节点
     * @param newChain     新信任链
     * @throws BlockchainDriverException
     */
    private void transferNodeByTx(Chain_Node oldChainNode, Chain newChain) throws Exception {

        txService.sendCreateTrustChainTx(oldChainNode, newChain);

        trustTreeService.updateTrustTxAddressOnLowerChain(newChain, oldChainNode.getNode().getAddr().getAddr(), TrustTxAddressUpdTxDTO.Type.ADD);

//        Chain_Node chainNode = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(newChain, oldChainNode.getNode(), Chain_Node.Stat.REMOVED);
//        int i = 0;
//        while (!chainManager.isActive(chainNode)) {
//            ThreadUtil.sleep(2);
//            chainNode = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(newChain, oldChainNode.getNode(), Chain_Node.Stat.REMOVED);
//            // 5 mintues
//            i = i + 2;
//            if (i > 300) {
//                log.error("Error >> Can't Create Trust Chain In 5 minutes");
//                throw new BlockchainDriverException("Create New Trust Chain Failed!");
//            }
//            log.info("New Trust Chain Not ready >> newChainId:{}, time spent:{}s, 2s later try again,  ", newChain.getChainId(), i);
//        }
        txService.sendDeleteChainTx(oldChainNode.getNode(), oldChainNode.getChain());
        trustTreeService.updateTrustTxAddressOnLowerChain(oldChainNode.getChain(), oldChainNode.getNode().getAddr().getAddr(), TrustTxAddressUpdTxDTO.Type.DELETE);

        log.info("New Trust Chain In Service, Old Trust Chain Removed >> NewChain:{}, OldChain:{}\n", newChain.getChainId(), oldChainNode.getChain().getChainId());

    }


    /**
     * 业务链自组织
     *
     * @param lwm 负责区间开始值
     * @param hwm 负责区间结束值
     */
    public void housekeepingForBizChain(long lwm, long hwm) {

        // process its every biz-chain
        List<Chain> bizChainList = chainRepository.findChainsByTypeIsNotAndStatIsNot(Chain.Type.TRUST_CHAIN, Chain.Stat.REMOVED);
        List<Chain> toProcessBizChainList = bizChainList.stream().filter(chain -> chain.getPart() > lwm && chain.getPart() < hwm).collect(Collectors.toList());

        for (Chain chain : toProcessBizChainList) {


            if (!isMaintainable(chain)){
                log.info("No need to maintain, start to process next chain \n");
                continue;
            }

            if (firstInitializing(chain)) {
                log.info("Chain is initializing ...  >> chainId:{} \n", chain.getChainId());
                continue;
            }

            List<Node> deadNodeList = getDeadNodesByCheckChain(chain);

            // check actual-in-service nodes exclude dead-nodes
            int actualInServiceNodes = chain.getSizeService() - deadNodeList.size();
            log.info("BizChain Healthy Status >> ChainId:{}, SizeDesired:{}, ActualInService:{}, DeadNodes:{} \n", chain.getChainId(), chain.getSizeDesired(), actualInServiceNodes, deadNodeList.size());
            if (actualInServiceNodes >= chain.getSizeDesired()) {
                continue;
            }

            // check lack nodes of chain
            int lackAmount = chain.getSizeDesired() - actualInServiceNodes;

            // get active-node(s) from all other chains if the check chain has dead node(s) or lack nodes
            if (lackAmount > 0) {

                List<Node> activeNodeList = getActiveNodesForBizChain(chain);
                List<String> addressList = new ArrayList<>();
                // match the chain with nodes
                for (int i = 0; i < lackAmount; i++) {
                    if (i < activeNodeList.size()) {

                        // add chain-node
                        Chain_Node chainNode = chainNodeRepository.save(Chain_Node.builder().chain(chain).node(activeNodeList.get(i)).stat(Chain_Node.Stat.NOT_READY).createdAt(new Date()).build());

                        log.info("Topology Bizchain >> The Chain Got A New Node, ChainId:{}, NodeId:{} \n", chain.getChainId(), activeNodeList.get(i).getId());

                        try {
                            txService.sendCreateChainTx(chainNode.getNode(), chainNode.getChain());
                        } catch (Exception e) {
                            log.error("Error >> Send Tx >> Create Chain On Node For BizChain >> ChainId:{}, error:{} \n", chain.getChainId(), e);
                        }

                        //update chain status
                        chainDao.updateSizeService(chain, 1);

                        //update node status
                        nodeDao.updateSizeService(activeNodeList.get(i), 1);

                        //add address for trust tx
                        addressList.add(activeNodeList.get(i).getAddr().getAddr());
                    } else {
                        log.info("Not Enough Node For Biz Chain , ChainId : {} , LackAmount : {}", chain.getChainId(), lackAmount - i);
                        break;
                    }

                    Chain lowerTrustChain = chainRepository.findChainByChainId(chain.getLowerChainId());
                    trustTreeService.updateTrustTxAddressOnLowerChain(addressList, lowerTrustChain, TrustTxAddressUpdTxDTO.Type.ADD);
                }
            }

        }
    }

    /**
     * 为当前业务链获取节点
     *
     * @param chain 当前业务链
     * @return 活跃节点
     */
    private List<Node> getActiveNodesForBizChain(Chain chain) {

        log.info("Start get active nodes for biz-chain ...");

        List<Node> activeNodeList;

        // low load mode
        //List<Node> lowLoadNodeList = nodeRepository.findNodesByStatIsNotAndSizeServiceLessThanEqual(Node.Stat.REMOVED, Integer.valueOf(nodeLowloadSize));
        //Collections.shuffle(lowLoadNodeList);

        // infinite increase mode
        List<Node> lowLoadNodeList = nodeRepository.findNodesByStatIsNot(Node.Stat.REMOVED);
        lowLoadNodeList.sort(Comparator.comparing(Node::getSizeService));

        // filter nodes which chain exist now
        // List<Long> excludeNodeIdList = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED).stream().map(Chain_Node::getNode).map(Node::getId).collect(Collectors.toList());
        // filter nodes which chain exist now or ever
        List<Long> excludeNodeIdList = chainNodeRepository.findChain_NodesByChain(chain).stream().map(Chain_Node::getNode).map(Node::getId).collect(Collectors.toList());
        lowLoadNodeList = lowLoadNodeList.stream().filter(node -> !excludeNodeIdList.contains(node.getId())).collect(Collectors.toList());

        // filter trust-chain not evidence node
        List<Long> nodeIdList = lowLoadNodeList.stream().map(Node::getId).collect(Collectors.toList());
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByNode_IdInAndChain_TypeIsAndStatIsNot(nodeIdList, Chain.Type.TRUST_CHAIN, Chain_Node.Stat.REMOVED);
        activeNodeList = chainNodes.stream().filter(Chain_Node::isEvidenced).map(Chain_Node::getNode).collect(Collectors.toList());

        log.info("BizChain Active Nodes >> total amount:{}, avaliable amount:{} \n", lowLoadNodeList.size(), activeNodeList.size());

        return activeNodeList;

    }


    /**
     * 节点自组织
     * 非一次完成所有自组织
     *
     * @param lwm 负责区间开始值
     * @param hwm 负责区间结束值
     */
    private void housekeepingForNode(long lwm, long hwm) {

        // 节点总需求数量
        int total_num = chainRepository.getTotalDesireNodeNum();

        // 节点总量
        int node_num = chainRepository.getTotalServiceNodeNum();

        // 节点平均服务chain数
        int avg = node_num == 0 ? 0 : (int) Math.ceil(total_num / node_num);

        // 浮动范围
        int range = (int) (avg * 0.5);

        // 获取范围内组织节点(排序完的)
        List<Node> nodeList = nodeRepository.findByPartBetweenOrderBySizeServiceDesc(lwm, hwm);

        for (int i = 0; i < nodeList.size() / 2; i++) {
            Node snode = nodeList.get(i);
            Node enode = nodeList.get((nodeList.size() - 1) - i);

            // 服务做多的节点在浮动单位内-> 自组织结束
            if (snode.getSizeService() <= (avg + range)
                    || enode.getSizeService() >= (avg - range)) {
                log.info("节点服务chain数量在浮动范围内！{}-{}", avg + range, avg - range);
                return;
            }
            if (snode.getSizeService() > (avg + range)) {
                int starnum = snode.getSizeService();
                int endnum = enode.getSizeService();

                int mvnum;
                // 平均节点超过上限
                if ((starnum + endnum) / 2 > (avg + range)) {
                    mvnum = avg + range - endnum;
                } else {
                    mvnum = (starnum - endnum) / 2;
                }

                log.info("节点转移:node:{}, 转移node{}，节点数{}", snode.getId(), enode.getId(), mvnum);
                // 转移节点chain集合
                List<Chain_Node> rmSult = new ArrayList<>();

                // 获取节点服务的chain
                List<Chain_Node> sChainNodeList = chainNodeRepository.findAllByNode_Id(snode.getId());

                List<Chain_Node> eChainNodeList = chainNodeRepository.findAllByNode_Id(enode.getId());

                for (Chain_Node cn1 : sChainNodeList) {
                    boolean flag = true;
                    for (Chain_Node cn2 : eChainNodeList) {
                        if (cn1.getChain() == cn2.getChain()) {
                            flag = false;
                            break;
                        }
                    }
                    if (flag) {
                        rmSult.add(cn1);
                        if (rmSult.size() == mvnum) {
                            break;
                        }
                    }
                }

                log.info("转移node集合{}", rmSult);
                // 转移节点
                for (Chain_Node cn : rmSult) {
                    enode.setSizeService(enode.getSizeService() + 1);
                    enode = nodeRepository.save(enode);
                    snode.setSizeService(snode.getSizeService() - 1);
                    nodeRepository.save(snode);

                    chainNodeRepository.save(Chain_Node.builder().chain(cn.getChain()).node(enode).createdAt(new Date()).stat(Chain_Node.Stat.IN_SERVICE).build());
                    try {
                        txService.sendCreateChainTx(enode, cn.getChain());
                    } catch (Exception e) {
                        log.error("Error >> Send Tx >> Transfer Node >>  ChainId:{}, NodeId:{}, error:{} \n", cn.getChain().getChainId(), enode.getId(), e);
                    }

                    cn.setStat(Chain_Node.Stat.REMOVED);
                    cn.setRemovedAt(new Date());
                    chainNodeRepository.save(cn);

                    try {
                        txService.sendDeleteChainTx(cn.getNode(), cn.getChain());
                    } catch (Exception e) {
                        log.error("Error >> Send Tx >> Transfer Node >>  ChainId:{}, NodeId:{}, error:{} \n", cn.getChain().getChainId(), cn.getNode().getId(), e);
                    }
                }

            }
        }

    }


    /**
     * 检查链的健康状况
     *
     * @param chain 被检查链
     * @return 死亡节点列表
     */
    private List<Node> getDeadNodesByCheckChain(Chain chain) {

        List<Node> deadNodeList = new ArrayList<>();

        List<Node> checkNodeList = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED).stream().map(Chain_Node::getNode).collect(Collectors.toList());

        for (Node node : checkNodeList) {
            if (!isActive(node, chain)) {
                deadNodeList.add(node);
            }
        }

        return deadNodeList;
    }


    /**
     * 判断节点是否活跃
     *
     * @param node 节点
     * @return boolean
     */
    private boolean isActive(Node node, Chain chain) {

        Chain_Node chainNode = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(chain, node, Chain_Node.Stat.REMOVED);

        //The chain-node not exist or removed
        if (Objects.isNull(chainNode)) {
            return false;
        }

        switch (chainNode.getStat()) {
            case NOT_READY:
                boolean existDeployInfo = chaincodeService.existDeployInfo(chain.getChainId().toString());
                if (existDeployInfo) {
                    boolean downloadStatus = chaincodeService.downloadStatus(chain.getChainId().toString(), node);
                    if (downloadStatus) {
                        log.info("The node is downloading chaincodes >> nodeId:{}, chainId:{}", node.getId(), chain.getChainId());
                        return true;
                    } else {
                        log.info("The node start chaincode timeout in init chain !!! >> nodeId:{}, chainId:{}", node.getId(), chain.getChainId());
                        emergencyService.onDeadChainNodeEvent(chainNode);
                        return false;
                    }
                }
                long createPeriod = (System.currentTimeMillis() - chainNode.getCreatedAt().getTime()) / (1000 * 60);

                if (createPeriod > 5) {
                    log.info("The node init chain time out !!! >> nodeId:{}, chainId:{}", node.getId(), chain.getChainId());
                    emergencyService.onDeadChainNodeEvent(chainNode);
                    return false;
                } else {
                    log.info("The node is initing chain >> nodeId:{}, chainId:{}", node.getId(), chain.getChainId());
                    return true;
                }
            case NOT_ACTIVE:
                long updatePeriod = (System.currentTimeMillis() - chainNode.getUpdatedAt().getTime()) / (1000 * 60);
                if (updatePeriod < 30) {
                    chainNode.setStat(Chain_Node.Stat.IN_SERVICE);
                    chainNodeRepository.save(chainNode);
                    return true;
                } else {
                    emergencyService.onDeadChainNodeEvent(chainNode);
                    return false;
                }
            case IN_SERVICE:
                long updatePeriod2 = (System.currentTimeMillis() - chainNode.getUpdatedAt().getTime()) / (1000 * 60);
                if (updatePeriod2 < 30) {
                    return true;
                } else {
                    if (chainService.nodeSynchronizing(chainNode)) {
                        log.info("The node is synchronizing ...  >> chainId:{}, nodeIP:{}", chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                        return true;
                    }
                    chainNode.setStat(Chain_Node.Stat.NOT_ACTIVE);
                    chainNode.setEvidenced(false);
                    chainNodeRepository.save(chainNode);
                    emergencyService.onDeadChainNodeEvent(chainNode);
                    return false;
                }
            default:
                log.error("The chain node did't have status !");
                chainNode.setStat(Chain_Node.Stat.NOT_READY);
                chainNodeRepository.save(chainNode);
                return false;
        }
    }


    /**
     * 判断业务链是否在进行首次初始化
     *
     * @param chain 链
     * @return
     */
    private boolean firstInitializing(Chain chain) {
        long createTime = chain.getCreatedAt().getTime();
        long span = (System.currentTimeMillis() - createTime) / (1000 * 60);
        if (span < 10) {
            return true;
        }

        // get all nodes
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);
        chainNodes.sort(Comparator.comparing(Chain_Node::getCreatedAt));

        // get first batch nodes
        if (!chainNodes.isEmpty()) {
            chainNodes = chainNodes.stream()
                    .filter(chainNode -> (chainNode.getCreatedAt().getTime() - createTime) / (1000 * 60) < 5)
                    .filter(chainNode -> chainNode.getUpdatedAt() == null).collect(Collectors.toList());
        }

        return !chainNodes.isEmpty();

    }


    /**
     * 判断链是否需要维护
     *
     * @param chain 链
     * @return
     */
    private boolean isMaintainable(Chain chain){
        if (chain.getType() == Chain.Type.SERVICE_CHAIN) {
            return true;
        }
        if (System.currentTimeMillis() > chain.getDeadline()) {
            QueMessage msg = QueMessage.builder().type(QueMessage.Type.CHAIN_DELETE).data("chain expired").build();
            this.rabbitTemplate.convertAndSend("pdx.iaas.exch", "pdx.iaas.topology", msg);
            log.info("No need to maintain, the chain is expired >> chainId:{} \n",chain.getChainId());
            return false;
        }
        if (chain.getGenesis()==null){
            log.info("No need to maintain, the chain is not paid >> chainId:{} \n",chain.getChainId());
            return false;
        }
        return true;
    }


    @Override
    public void run(String... args) {
        log.info("Topology service started");
        Chain chain = chainRepository.findChainByChainId(146751263424380928L);
        firstInitializing(chain);
    }


}


