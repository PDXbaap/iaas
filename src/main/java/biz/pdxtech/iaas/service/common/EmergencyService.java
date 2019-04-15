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

package biz.pdxtech.iaas.service.common;


import biz.pdxtech.iaas.dao.ChainDao;
import biz.pdxtech.iaas.dao.NodeDao;
import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.Chain_Node;
import biz.pdxtech.iaas.entity.Deploy_Node;
import biz.pdxtech.iaas.entity.Node;
import biz.pdxtech.iaas.hazelcast.CacheData;
import biz.pdxtech.iaas.repository.*;
import biz.pdxtech.iaas.util.HttpUtil;
import biz.pdxtech.iaas.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmergencyService {

    @Autowired
    private ChainRepository chainRepository;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private ChainNodeRepository chainNodeRepository;
    @Autowired
    private DeployNodeRepository deployNodeRepository;
    @Autowired
    private TxService txService;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private NodeDao nodeDao;
    @Autowired
    private ChainDao chainDao;


    private ConcurrentHashMap<Chain_Node, Chain> islandChainMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, Chain_Node> deadChainNodeMap = new ConcurrentHashMap<>();

    public void onIslandState(Chain fromChain, Chain tochain, Node node) {
        if (tochain.getIslandState() != null && !tochain.getIslandState()) {
            return;
        }
        Chain_Node chainNode = Chain_Node.builder().chain(tochain).node(node).build();
        islandChainMap.put(chainNode, fromChain);
    }


    public void onDeadChainNodeEvent(Chain_Node chainNode) {
        Iterator<Map.Entry<Long, Chain_Node>> iterator = deadChainNodeMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Chain_Node> next = iterator.next();
            Long key = next.getKey();
            if (key.equals(chainNode.getId())) {
                iterator.remove();
            }
        }
        deadChainNodeMap.put(chainNode.getId(), chainNode);
    }


    @Scheduled(initialDelay = 120000, fixedDelay = 180000)
    protected void cureIslandChain() {
        if (islandChainMap.isEmpty()) {
            log.info("No island chain on the server ");
            return;
        } else {
            log.info("The island chains need cure ...  >>  islandChains:{} \n", islandChainMap.keySet().stream().map(Chain_Node::getChain).map(Chain::getChainId).toArray());
        }

        log.info("Cure Island Chain Start >> island-chain-amount:{}", islandChainMap.size());

        // filter back-to-mainland-chain
        Iterator<Map.Entry<Chain_Node, Chain>> iterator = islandChainMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Chain_Node, Chain> next = iterator.next();
            Chain_Node chainNode = next.getKey();
            Chain chain = chainRepository.findChainByChainId(chainNode.getChain().getChainId());
            if (!chain.getIslandState()) {
                log.info("");
                iterator.remove();
            }
        }

        // get chain node which participate evidence
        HashMap<Chain_Node, Chain> excuteableTask = new HashMap<>();
        Iterator<Map.Entry<Chain_Node, Chain>> iterator2 = islandChainMap.entrySet().iterator();
        while (iterator2.hasNext()) {
            Map.Entry<Chain_Node, Chain> next = iterator2.next();
            Chain_Node chainNode = next.getKey();
            Chain_Node cn = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsAndEvidencedIs(chainNode.getChain(), chainNode.getNode(), Chain_Node.Stat.IN_SERVICE, true);
            if (Objects.nonNull(cn)) {
                Chain c = next.getValue();
                excuteableTask.put(cn, c);
                iterator2.remove();
            }
        }

        // excute update consensus
        for (HashMap.Entry entry : excuteableTask.entrySet()) {
            Chain_Node chainNode = (Chain_Node) entry.getKey();
            List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chainNode.getChain(), Chain_Node.Stat.REMOVED);
            chainNodes.sort(Comparator.comparing(Chain_Node::getId));
            if (!chainNodes.isEmpty()) {
                txService.sendUpdateConsensusNodeTx((Chain) entry.getValue(), chainNodes.get(0), Collections.singletonList(chainNode.getNode().getAddr().getAddr()));
            }
        }

        log.info("Cure Island Chain End >> island-chain-amount:{}", islandChainMap.size());

    }


    @Scheduled(initialDelay = 120000, fixedDelay = 60000)
    protected void processDeadChainNode() {
        if (deadChainNodeMap.isEmpty()) {
            log.info("No dead chain node on the server \n");
            return;
        }

        log.info("Process Dead Chain Node Start >> deadNodeIds:{}", deadChainNodeMap.values().stream().map(Chain_Node::getId).toArray());

        Iterator<Map.Entry<Long, Chain_Node>> iterator = deadChainNodeMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Chain_Node> next = iterator.next();
            Chain_Node chainNode = next.getValue();

            if (chainNode.getStat() == Chain_Node.Stat.NOT_ACTIVE) {
                long span = (System.currentTimeMillis() - chainNode.getUpdatedAt().getTime()) / (1000 * 60);
                if (span > 60) {
                    if (chainNode.getChain().getType()!=Chain.Type.TRUST_CHAIN){
                        log.info("chain node is not active for 60 minutes, start deleting >> id:{}, chianId:{}, nodeIP:{}", chainNode.getId(), chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                        deleteChainNode(chainNode);
                        iterator.remove();
                    }else {
                        log.info("trust chain node is not active for 60 minutes, start processing >> id:{}, chianId:{}, nodeIP:{}", chainNode.getId(), chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                        processDeadTrustChainNode(chainNode);
                        iterator.remove();
                    }
                } else {
                    log.info("chain node is not active less than 60 minutes, wait deleting ...  >>  id:{}, chianId:{}, nodeIP:{}", chainNode.getId(), chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                }
            }
            if (chainNode.getStat() == Chain_Node.Stat.NOT_READY) {
                if (chainNode.getChain().getType() != Chain.Type.TRUST_CHAIN) {
                    log.info("chain node failed to initialize, start deleting >> id:{}, chianId:{}, nodeIP:{}", chainNode.getId(), chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                    deleteChainNode(chainNode);
                    iterator.remove();
                }else {
                    log.info("trust chain node failed to initialize, start processing >> id:{}, chianId:{}, nodeIP:{}", chainNode.getId(), chainNode.getChain().getChainId(), chainNode.getNode().getIp());
                    processDeadTrustChainNode(chainNode);
                    iterator.remove();
                }
            }
            Integer service = chainNode.getNode().getSizeService();
            if (service <= 2 && isNodeDead(chainNode)) {
                log.info("no alive chain on the node, start node-deleting >> nodeIP:{} ", chainNode.getNode().getIp());
                deleteNode(chainNode.getNode());
            }
        }

        log.info("Process Dead Chain Node End >> deadNodeIds:{}", deadChainNodeMap.values().stream().map(Chain_Node::getId).toArray());

    }


    /**
     * 是否是死亡节点
     *
     * @param chainNode 链节点
     * @return
     */
    private boolean isNodeDead(Chain_Node chainNode) {
        boolean connectable = false;
        if (Objects.nonNull(chainNode.getRpcPort())) {
            connectable = HttpUtil.isHostConnectable(chainNode.getNode().getIp(), chainNode.getRpcPort());
        }
        if (!connectable) {
            List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByNode_IdAndStatIsNot(chainNode.getNode().getId(), Chain_Node.Stat.REMOVED);
            for (Chain_Node cn : chainNodes) {
                if (Objects.isNull(cn.getRpcPort())) {
                    continue;
                }
                if (HttpUtil.isHostConnectable(cn.getNode().getIp(), cn.getRpcPort())) {
                    return false;
                } else {
                    cn.setConnected(false);
                    chainNodeRepository.save(cn);
                }
            }
        }
        return !connectable;
    }


    /**
     * 删除链节点
     *
     * @param chainNode 链节点
     */
    private void deleteChainNode(Chain_Node chainNode) {
        Chain_Node cn = chainNodeRepository.findChain_NodeById(chainNode.getId());
        if (cn.getStat() != Chain_Node.Stat.REMOVED) {
            nodeDao.updateSizeService(chainNode.getNode(), -1);
            chainDao.updateSizeService(chainNode.getChain(), -1);
            chainNode.setStat(Chain_Node.Stat.REMOVED);
            chainNode.setRemovedAt(new Date());
            chainNodeRepository.save(chainNode);
            //TODO: send delete chain tx
        }
    }


    /**
     * 删除节点
     *
     * @param node 节点
     */
    private void deleteNode(Node node) {

        Node n = nodeRepository.findNodeById(node.getId());
        if (n.getStat() != Node.Stat.REMOVED) {
            // update node status
            n.setStat(Node.Stat.REMOVED);
            nodeRepository.save(n);

            // update node-chain status & chain service size
            List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByNodeAndStatIsNot(n, Chain_Node.Stat.REMOVED);
            for (Chain_Node chainNode : chainNodes) {
                chainNode.setStat(Chain_Node.Stat.REMOVED);
                chainNode.setRemovedAt(new Date());
                chainNodeRepository.save(chainNode);
                chainDao.updateSizeService(chainNode.getChain(), -1);
            }

            // update node-chaincode status
            List<Deploy_Node> deployNodes = deployNodeRepository.findDeploy_NodesByNode_IdAndStatIsNot(n.getId(), Deploy_Node.Stat.REMOVED);
            for (Deploy_Node deployNode : deployNodes) {
                deployNode.setStat(Deploy_Node.Stat.REMOVED);
                deployNodeRepository.save(deployNode);
            }
        }
    }


    /**
     * 处理死亡的信任链节点
     *
     * @param chainNode 链节点
     */
    private void processDeadTrustChainNode(Chain_Node chainNode) {
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByNode_IdAndStatIsNot(chainNode.getNode().getId(), Chain_Node.Stat.REMOVED);
        chainNodes = chainNodes.stream().filter(cn -> !cn.getId().equals(chainNode.getId())).collect(Collectors.toList());
        Chain_Node activeChainNode = null;
        for (Chain_Node cn : chainNodes) {
            if (chainManager.isActive(cn)) {
                activeChainNode = cn;
            }
        }
        if (Objects.isNull(activeChainNode)) {
            deleteNode(chainNode.getNode());
        } else {
            try {
                deleteChainNode(chainNode);
                txService.sendDeleteChainTx(chainNode.getNode(), chainNode.getChain(), activeChainNode.getChain());
                ThreadUtil.sleep(5);
                txService.sendCreateTrustChainTx(activeChainNode, chainNode.getChain());
            } catch (Exception e) {
                log.error("Error >> emergency process trust chain node, error:{} ",e);
            }
        }
    }


}
