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

import biz.pdxtech.iaas.cluster.Partitioner;
import biz.pdxtech.iaas.entity.Address;
import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.Chain_Node;
import biz.pdxtech.iaas.entity.Node;
import biz.pdxtech.iaas.repository.ChainNodeRepository;
import biz.pdxtech.iaas.repository.ChainRepository;
import biz.pdxtech.iaas.service.impl.ChainService;
import biz.pdxtech.iaas.util.ChainCodeClient;
import biz.pdxtech.iaas.util.RadixUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

//@Service
@Slf4j
public class TokenService {

    @Autowired
    Partitioner partitioner;
    @Autowired
    ChainRepository chainRepository;
    @Autowired
    ChainNodeRepository chainNodeRepository;
    @Autowired
    TxService txService;
    @Autowired
    ChainService chainService;
    @Autowired
    ChainManager chainManager;

    @Value("${pdx.iaas.transfer.token.limit}")
    String tokenLimit;
    @Value("${pdx.iaas.basic-service-chain.name}")
    String basicServiceChainName;


//    @Scheduled(initialDelay = 6000, fixedDelay = 120000)
    private void deliverToken() {

        Chain_Node basicBizChainNode = chainService.getBasicServiceChainNode();
        if (basicBizChainNode == null) {
            log.warn("BasicServiceChain Not In Service !!!");
            return;
        }

        //process-section
        Long lwm = partitioner.getPartitionLWM();
        Long hwm = partitioner.getPartitionHWM();

        log.info("DeliverToken Start >>>  My section is from {} to {} \n", lwm, hwm);

        long timeForDeliverToken = System.currentTimeMillis();

        List<Chain> chains = chainRepository.findChainsByPartBetweenAndStatIsNot(lwm, hwm, Chain.Stat.REMOVED);
        chains = chains.stream().filter(chain -> chain.getLowerChainId() != null).collect(Collectors.toList());

        for (Chain chain : chains) {
            //target addresses
            List<Chain_Node> chainNodeList = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);
            List<String> addressList = chainNodeList.stream().map(Chain_Node::getNode).map(Node::getAddr).map(Address::getAddr).collect(Collectors.toList());

            //target chain-nodes
            Chain lowerTrustChain = chainRepository.findChainByChainId(chain.getLowerChainId());
            List<Chain_Node> lowerChainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(lowerTrustChain, Chain_Node.Stat.REMOVED);
            Chain_Node activeChainNode = chainManager.getActiveChainNode(lowerChainNodes);

            ChainCodeClient client = new ChainCodeClient(activeChainNode.getNode().getIp(), activeChainNode.getRpcPort(), activeChainNode.getChain().getChainId());

            for (String address : addressList) {
                boolean balanceEnough = isBalanceEnough(client, address, tokenLimit, chain);
                if (balanceEnough) {
                    continue;
                }
                //avoid x-transferByIaaS at same chain
                if (lowerTrustChain.getChainId().equals(basicBizChainNode.getChain().getChainId())) {
                    txService.sendNormalTranferTx(basicBizChainNode, address);
                    continue;
                }
                String hash = txService.sendXChainTranferTX1(basicBizChainNode, lowerTrustChain, address);
                if (hash != null) {
                    txService.sendXChainTranferTX2(basicBizChainNode, activeChainNode, hash);
                }
            }
        }

        log.info("DeliverToken End  <<<  It takes {} seconds \n", (System.currentTimeMillis() - timeForDeliverToken) / 1000);

    }


    /**
     * 多地址转账
     *
     * @param chain 当前链
     */
    public void transferTokenToMultiAddress(Chain chain) {

        //basicBizChain as fromChain
        Chain_Node basicBizChainNode = chainService.getBasicServiceChainNode();
        if (basicBizChainNode == null) {
            log.warn("BasicServiceChain Not In Service !!!");
            return;
        }

        if (chain.getLowerChainId() == null) {
            log.info("No Lower Trust Chain >> No Need Transfer Token >> ChainId:{} \n", chain.getChainId());
            return;
        }

        //target addresses
        List<Chain_Node> toChainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);
        List<String> addressList = toChainNodes.stream().map(Chain_Node::getNode).map(Node::getAddr).map(Address::getAddr).collect(Collectors.toList());

        if (toChainNodes.isEmpty()) {
            log.warn("No node on chain, no address to transfer token !!! >> ChainId:{} \n", chain.getChainId());
            return;
        }

        //target chain nodes
        List<Chain_Node> lowerChainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndStatIsNot(chain.getLowerChainId(), Chain_Node.Stat.REMOVED);
        Chain_Node activeChainNode = chainManager.getActiveChainNode(lowerChainNodes);

        if (Objects.isNull(activeChainNode)) {
            log.warn("No node on lower-trust-chain, can't transfer token !!! >> LowerChainId:{} \n", chain.getLowerChainId());
            return;
        }

        ChainCodeClient client = new ChainCodeClient(activeChainNode.getNode().getIp(), activeChainNode.getRpcPort(), activeChainNode.getChain().getChainId());
        for (String address : addressList) {
            boolean balanceEnough = isBalanceEnough(client, address, tokenLimit, chain);
            if (balanceEnough) {
                continue;
            }
            //avoid x-transferByIaaS at same chain
            if (activeChainNode.getChain().getChainId().equals(basicBizChainNode.getChain().getChainId())) {
                txService.sendNormalTranferTx(basicBizChainNode, address);
                continue;
            }
            String hash = txService.sendXChainTranferTX1(basicBizChainNode, activeChainNode.getChain(), address);
            if (hash != null) {
                txService.sendXChainTranferTX2(basicBizChainNode, activeChainNode, hash);
            }
        }
    }


    /**
     * 单节点转账
     *
     * @param chain 当前链
     * @param node 当前节点
     */
    public void transferTokenToSingleAddress(Chain chain, Node node) {
        //basicBizChain as fromChain
        Chain_Node basicBizChainNode = chainService.getBasicServiceChainNode();
        if (basicBizChainNode == null) {
            log.warn("BasicServiceChain Not In Service !!!");
            return;
        }

        if (chain.getLowerChainId() == null) {
            log.info("No Lower Trust Chain >> No Need Transfer Token >> ChainId:{} \n", chain.getChainId());
            return;
        }

        //target addresses
        Chain_Node chainNode = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(chain, node, Chain_Node.Stat.REMOVED);
        if (Objects.isNull(chainNode)){
            log.warn("No node on chain, no address to transfer token !!! >> ChainId:{} \n", chain.getChainId());
            return;
        }
        String address = chainNode.getNode().getAddr().getAddr();

        //target chain nodes
        List<Chain_Node> lowerChainNodes = chainNodeRepository.findChain_NodesByChain_ChainIdAndStatIsNot(chain.getLowerChainId(), Chain_Node.Stat.REMOVED);
        Chain_Node activeChainNode = chainManager.getActiveChainNode(lowerChainNodes);

        if (Objects.isNull(activeChainNode)) {
            log.warn("No node on lower-trust-chain, can't transfer token !!! >> LowerChainId:{} \n", chain.getLowerChainId());
            return;
        }

        ChainCodeClient client = new ChainCodeClient(activeChainNode.getNode().getIp(), activeChainNode.getRpcPort(), activeChainNode.getChain().getChainId());
        boolean balanceEnough = isBalanceEnough(client, address, tokenLimit, chain);
        if (balanceEnough) {
            log.info("No need transfer, balance is enough >> ChainId:{}, Address:{} \n",chain.getChainId(),address);
            return;
        }
        //avoid x-transferByIaaS at same chain
        if (activeChainNode.getChain().getChainId().equals(basicBizChainNode.getChain().getChainId())) {
            txService.sendNormalTranferTx(basicBizChainNode, address);
            return;
        }
        String hash = txService.sendXChainTranferTX1(basicBizChainNode, activeChainNode.getChain(), address);
        if (hash != null) {
            txService.sendXChainTranferTX2(basicBizChainNode, activeChainNode, hash);
        }
    }


    /**
     * 判断该链地址余额是否充足
     *
     * @param client  客户端
     * @param address 地址
     * @param quota   配额
     * @param chain   链
     * @return
     */
    private boolean isBalanceEnough(ChainCodeClient client, String address, String quota, Chain chain) {
        String balance = client.getBalance(address);
        BigDecimal amount = RadixUtil.hexToBigDecimal(balance);
        int result = amount.compareTo(new BigDecimal(quota));
        String sAmount = amount.toString().length() > 18 ? new StringBuffer(amount.toString()).insert(amount.toString().length() - 18, ",").toString() : amount.toString();
        if (result == -1) {
            log.info("Token Balance Lacking >> ChainId:{}, LowerTC:{}, Address:{}, Balance:{} \n", chain.getChainId(), chain.getLowerChainId(), address, sAmount);
            return false;
        } else {
            log.info("Token Balance Enough >> ChainId:{}, LowerTC:{}, Address:{}, Balance:{} \n", chain.getChainId(), chain.getLowerChainId(), address, sAmount);
            return true;
        }
    }

}
