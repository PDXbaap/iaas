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

import biz.pdxtech.baap.driver.BlockchainDriverException;
import biz.pdxtech.baap.util.encrypt.EncryptUtil;
import biz.pdxtech.iaas.common.exception.TxException;
import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.Chain_Node;
import biz.pdxtech.iaas.entity.DeployInfo;
import biz.pdxtech.iaas.entity.Node;
import biz.pdxtech.iaas.proto.dto.*;
import biz.pdxtech.iaas.repository.ChainNodeRepository;
import biz.pdxtech.iaas.repository.ChainRepository;
import biz.pdxtech.iaas.repository.DeployInfoRepository;
import biz.pdxtech.iaas.repository.NodeRepository;
import biz.pdxtech.iaas.util.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


@Slf4j
@Service
public class TxService {

    @Value("${pdx.iaas.transfer.token.amount}")
    private String transferTokenAmount;

    private static final String CERT_NAME = "tcadm.crt";
    private static final String TXID_NULL = "txid is null";

    private final ChainNodeRepository chainNodeRepository;
    private final ChainRepository chainRepository;
    private final DeployInfoRepository deployInfoRepository;
    private final ChainManager chainManager;
    private final NodeRepository nodeRepository;

    @Autowired
    public TxService(NodeRepository nodeRepository, ChainRepository chainRepository, ChainNodeRepository chainNodeRepository, DeployInfoRepository deployInfoRepository, ChainManager chainManager) {
        this.chainRepository = chainRepository;
        this.chainNodeRepository = chainNodeRepository;
        this.deployInfoRepository = deployInfoRepository;
        this.chainManager = chainManager;
        this.nodeRepository = nodeRepository;
    }

    private ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("tx-pool-%d").build();
    private ExecutorService executor = new ThreadPoolExecutor(5, 30, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(1024), threadFactory, new ThreadPoolExecutor.AbortPolicy());

    private ConcurrentHashMap<Chain_Node, Chain> assignChainNodeMap = new ConcurrentHashMap();
    private ConcurrentHashMap<Chain, Chain> assignChainMap = new ConcurrentHashMap();
    private ConcurrentHashMap<Chain, TrustTxAddressUpdTxDTO> updateWhiteListMap = new ConcurrentHashMap();
    private ConcurrentHashMap<String, Chain_Node> deleteChainNodeMap = new ConcurrentHashMap<>();


    /**
     * 发送信任链指派交易
     *
     * @param lowerTC     下层信任链
     * @param targetChain 上层 业务链/信任链
     * @throws BlockchainDriverException
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 15000L, multiplier = 2))
    public String sendAssignTrustChainTx(@NotNull Chain lowerTC, @NotNull Chain targetChain) throws Exception {
        Future<String> future = executor.submit(() -> startSendAssignTrustChainTx(lowerTC, targetChain));
        return future.get();
    }

    /**
     * 发送信任链指派交易到新加入节点
     *
     * @param lowerTC         下层信任链
     * @param targetChainNode 上层 业务链/信任链 节点
     * @throws BlockchainDriverException
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 15000L, multiplier = 2))
    public String sendAssignTrustChainTxToNewNode(@NotNull Chain lowerTC, @NotNull Chain_Node targetChainNode) throws Exception {
        Future<String> future = executor.submit(() -> startSendAssignTrustChainTx(lowerTC, targetChainNode));
        return future.get();
    }

    private String startSendAssignTrustChainTx(@NotNull Chain lowerTC, @NotNull Chain targetChain) throws Exception {
        ChainUtil.getOrderByChainId(targetChain.getChainId());
        Chain_Node activeChainNode = chainManager.getActiveChainNode(targetChain);
        if (Objects.isNull(activeChainNode)) {
            assignChainMap.put(targetChain, lowerTC);
            log.warn("TX >> Add Assign Task >> target-chain:{}, lower-trust-chain:{} ", targetChain.getChainId(), lowerTC.getChainId());
            return null;
        }
        return startSendAssignTrustChainTx(lowerTC, activeChainNode);
    }

    private String startSendAssignTrustChainTx(@NotNull Chain lowerTC, @NotNull Chain_Node targetChainNode) throws Exception {
        ChainUtil.getOrderByChainId(targetChainNode.getChain().getChainId());

        if (!targetChainNode.isEvidenced()) {
            assignChainNodeMap.put(targetChainNode, lowerTC);
            log.warn("TX >> Add Assign Task >> target-chain:{}, target-node:{}, lower-trust-chain:{} ", targetChainNode.getChain().getChainId(), targetChainNode.getNode().getId(), lowerTC.getChainId());
            return null;
        }

        log.info("TX >> AssignTrustChain >> ChainId:{}, LowerTC:{} \n", targetChainNode.getChain().getChainId(), lowerTC.getChainId());

        List<Chain_Node> lowerChainNodes = chainManager.getActiveChainNodes(lowerTC, 10);

        List<String> enodeList = lowerChainNodes.stream()
                .filter(chainNode -> !chainNode.getChain().getChainId().equals(targetChainNode.getChain().getChainId()))
                .map(chainNode -> chainNode.getNode().getEnode() + "@" + chainNode.getNode().getIp() + ":" + chainNode.getP2pPort())
                .collect(Collectors.toList());

        List<String> hostList = lowerChainNodes.stream()
                .map(chainNode -> "http://" + chainNode.getNode().getIp() + ":" + chainNode.getRpcPort())
                .collect(Collectors.toList());

        ChainCodeClient client = ChainCodeClient.getTcUpdaterClient(targetChainNode.getNode().getIp(), targetChainNode.getRpcPort(), targetChainNode.getChain().getChainId());

        Chain preChain;
        String assignRecord = targetChainNode.getChain().getAssignRecord();
        AssignRecordDTO[] dtos = JsonUtil.jsonToObj(assignRecord, AssignRecordDTO[].class);
        Long preChainId = dtos[dtos.length - 1].getPreChainId();
        Long curChainId = dtos[dtos.length - 1].getCurChainId();
        if (preChainId.equals(curChainId)) {
            preChain = lowerTC;
        } else {
            preChain = chainRepository.findChainByChainId(preChainId);
        }

        TrustChainUpdTxDTO dto = TrustChainUpdTxDTO.builder()
                .chainId(String.valueOf(lowerTC.getChainId()))
                .chainOwner(lowerTC.getOwner().getAddr())
                .prevChainId(String.valueOf(preChain.getChainId()))
                .prevChainOwner(preChain.getOwner().getAddr())
                .selfHost("")
                .timestamp(System.currentTimeMillis())
                .random(String.valueOf(RandomUtils.nextInt(1000, 9999)))
                .tcadmCrt(ChainUtil.getCertifacateByName(CERT_NAME))
                .enodeList(enodeList)
                .hostList(hostList).build();

        String txid = client.updateTrustChain(dto);
        if (txid == null) {
            throw new BlockchainDriverException(TXID_NULL);
        }
        return txid;
    }


    /**
     * 发送更新信任交易白名单地址交易
     *
     * @param targetChain 目标链
     * @param addressList 地址列表
     * @param type        增加/删除
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 10000L, multiplier = 2))
    public String sendUpdateTrustTxAddressTx(@NotNull Chain targetChain, @NotNull List<String> addressList, TrustTxAddressUpdTxDTO.Type type) throws Exception {
        Future<String> future = executor.submit(() -> startSendUpdateTrustTxAddressTx(targetChain, addressList, type));
        return future.get();
    }

    private String startSendUpdateTrustTxAddressTx(@NotNull Chain chain, @NotNull List<String> addressList, TrustTxAddressUpdTxDTO.Type type) throws Exception {

        if (type == TrustTxAddressUpdTxDTO.Type.DELETE) {
            List<String> deleteAddresses = new ArrayList<>();
            for (String address : addressList) {
                Node node = nodeRepository.findNodeByAddr_Addr(address);
                List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByNode_IdAndStatIsNot(node.getId(), Chain_Node.Stat.REMOVED);
                List<Chain> chains = chainNodes.stream().map(Chain_Node::getChain).filter(c -> c.getLowerChainId().equals(chain.getChainId())).collect(Collectors.toList());
                if (chains.size() <= 1) {
                    deleteAddresses.add(address);
                }
            }
            if (deleteAddresses.isEmpty()) {
                log.info("Address is in-use, can not delete");
                return null;
            }
            addressList = deleteAddresses;
        }

        ChainUtil.getOrderByChainId(chain.getChainId());

        Chain_Node activeChainNode = chainManager.getActiveChainNode(chain);

        if (Objects.isNull(activeChainNode)) {
            TrustTxAddressUpdTxDTO dto = TrustTxAddressUpdTxDTO.builder().nodes(addressList).type(type.ordinal()).build();
            updateWhiteListMap.put(chain, dto);
            log.warn("TX >> Add Update White List Task >> ChainId:{}, AddressList:{}, Type:{} ", chain.getChainId(), dto.getNodes(), dto.getType());
            return null;
        }

        log.info("TX >> UpdateTrustTxAddress >> ChainId:{}, NodeIP:{}, Stat:{}, AddressList:{} \n", chain.getChainId(), activeChainNode.getNode().getIp(), type.name(), addressList);

        ChainCodeClient client = new ChainCodeClient(activeChainNode.getNode().getIp(), activeChainNode.getRpcPort(), activeChainNode.getChain().getChainId());

        TrustTxAddressUpdTxDTO dto = TrustTxAddressUpdTxDTO.builder()
                .nodes(addressList)
                .type(type.ordinal())
                .timestamp(System.currentTimeMillis())
                .random(String.valueOf(RandomUtils.nextInt(1000, 9999)))
                .tcadmCrt(ChainUtil.getCertifacateByName(CERT_NAME)).build();

        String txid = client.updateTrustTxAddress(dto);
        if (txid == null) {
            throw new TxException(TXID_NULL);
        }
        return txid;

    }


    /**
     * 发送创链交易
     *
     * @param node  节点
     * @param chain 新链
     * @throws BlockchainDriverException
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 10000L, multiplier = 1.5))
    public String sendCreateChainTx(@NotNull Node node, @NotNull Chain chain) throws Exception {
        Future<String> future = executor.submit(() -> startSendCreateChainTx(node, chain));
        return future.get();
    }

    private String startSendCreateChainTx(@NotNull Node node, @NotNull Chain chain) throws Exception {

        log.info("TX >> CreateChain >> ChainId:{}, NodeId:{} \n", chain.getChainId(), node.getId());
        List<Chain_Node> nodeChainsList = chainNodeRepository.findChain_NodesByNodeAndStatIsNot(node, Chain_Node.Stat.REMOVED);
        List<Chain_Node> collect = nodeChainsList.stream()
                .filter(nodeChain -> nodeChain.getChain().getType() == Chain.Type.TRUST_CHAIN)
                .collect(Collectors.toList());

        List<DeployInfo> deployInfoList = deployInfoRepository.findDeployInfosByChannelIs(String.valueOf(chain.getChainId()));

        List<DeployInfoDTO> deployInfoDTOList = new ArrayList<>();
        for (DeployInfo deployInfo : deployInfoList) {
            DeployInfoDTO dto = DeployInfoDTO.builder().build();
            BeanUtils.copyProperties(deployInfo, dto);
            if (deployInfo.getStatus() != DeployInfo.Stat.ERROR.ordinal() && deployInfo.getStatus() != DeployInfo.Stat.STREAMHANDLING.ordinal()) {
                // avoid large data in create-chain-tx
                deployInfoDTOList.add(dto);
                break;
            }
        }

        if (collect.isEmpty()) {
            throw new BlockchainDriverException("Can't find trust-chain!");
        }
        ChainUtil.getOrderByChainId(collect.get(0).getChain().getChainId());
        ChainCodeClient client = ChainCodeClient.getBaaPClient(node.getIp(), collect.get(0).getRpcPort(), collect.get(0).getChain().getChainId());
        String txid = client.createChain(chain, node, deployInfoDTOList);
        if (txid == null) {
            throw new BlockchainDriverException(TXID_NULL);
        }
        return txid;

    }


    /**
     * 发送创建信任链交易
     *
     * @param oldChainNode 原链节点
     * @param newChain     新信任链
     * @throws BlockchainDriverException
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 10000L, multiplier = 1.5))
    public String sendCreateTrustChainTx(@NotNull Chain_Node oldChainNode, @NotNull Chain newChain) throws Exception {
        Future<String> future = executor.submit(() -> startSendCreateTrustChainTx(oldChainNode, newChain));
        return future.get();
    }

    private String startSendCreateTrustChainTx(@NotNull Chain_Node oldChainNode, @NotNull Chain newChain) throws Exception {
        ChainUtil.getOrderByChainId(oldChainNode.getChain().getChainId());

        log.info("TX >> CreateTrustChain >> oldTrustChain:{}, NodeId:{}, newTrustChain:{} \n", oldChainNode.getChain().getChainId(), oldChainNode.getNode().getId(), newChain.getChainId());
        ChainCodeClient client = ChainCodeClient.getBaaPClient(oldChainNode.getNode().getIp(), oldChainNode.getRpcPort(), oldChainNode.getChain().getChainId());
        List<DeployInfoDTO> deployInfoDTOList = new ArrayList<>();
        String txid = client.createChain(newChain, oldChainNode.getNode(), deployInfoDTOList);
        if (txid == null) {
            throw new BlockchainDriverException(TXID_NULL);
        }
        return txid;
    }


    /**
     * 发送删链交易
     *
     * @param node  节点
     * @param chain 删除链
     * @throws BlockchainDriverException
     */
    @Retryable(value = {BlockchainDriverException.class}, maxAttempts = 3, backoff = @Backoff(delay = 10000L, multiplier = 1.5))
    public String sendDeleteChainTx(@NotNull Node node, @NotNull Chain chain) throws Exception {
        Chain_Node cn = chainNodeRepository.findChain_NodeByNodeAndStatIsNotAndChain_Type(node, Chain_Node.Stat.REMOVED, Chain.Type.TRUST_CHAIN);
        if (!chainManager.isActive(cn)) {
            String key = node.getId().toString() + chain.getId().toString();
            Chain_Node value = Chain_Node.builder().chain(chain).node(node).build();
            deleteChainNodeMap.put(key, value);
            log.warn("TX >> Delete Chain Node Task >> chainId:{}, nodeIp:{} ", chain.getChainId(), node.getIp());
            return null;
        }
        Future<String> future = executor.submit(() -> startSendDeleteChainTx(node, chain, null));
        return future.get();
    }

    @Retryable(value = {BlockchainDriverException.class}, maxAttempts = 3, backoff = @Backoff(delay = 10000L, multiplier = 1.5))
    public String sendDeleteChainTx(@NotNull Node node, @NotNull Chain chain, Chain targetChain) throws Exception {
        Future<String> future = executor.submit(() -> startSendDeleteChainTx(node, chain, targetChain));
        return future.get();
    }

    private String startSendDeleteChainTx(@NotNull Node node, @NotNull Chain chain, Chain targetChain) throws Exception {

        Chain_Node chainNode;
        log.info("TX >> DeleteChain >> ChainId:{}, NodeId:{} \n", chain.getChainId(), node.getId());

        if (Objects.nonNull(targetChain)) {
            chainNode = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(targetChain, node, Chain_Node.Stat.REMOVED);
        } else {
            chainNode = chainNodeRepository.findChain_NodeByNodeAndStatIsNotAndChain_Type(node, Chain_Node.Stat.REMOVED, Chain.Type.TRUST_CHAIN);
        }

        ChainUtil.getOrderByChainId(chainNode.getChain().getChainId());

        ChainCodeClient client = ChainCodeClient.getBaaPClient(chainNode.getNode().getIp(), chainNode.getRpcPort(), chainNode.getChain().getChainId());
        String txid = client.deleteChain(chain, node);
        if (txid == null) {
            throw new BlockchainDriverException(TXID_NULL);
        }
        return txid;
    }

    private String startSendDeleteChainTx(@NotNull Node node, @NotNull Chain chain) throws Exception {

        ChainUtil.getOrderByChainId(chain.getChainId());

        log.info("TX >> DeleteChain >> ChainId:{}, NodeId:{} \n", chain.getChainId(), node.getId());

        Chain_Node chainNode = chainNodeRepository.findChain_NodeByNodeAndStatIsNotAndChain_Type(node, Chain_Node.Stat.REMOVED, Chain.Type.TRUST_CHAIN);
        ChainCodeClient client = ChainCodeClient.getBaaPClient(chainNode.getNode().getIp(), chainNode.getRpcPort(), chainNode.getChain().getChainId());
        String txid = client.deleteChain(chain, node);
        if (txid == null) {
            throw new BlockchainDriverException(TXID_NULL);
        }
        return txid;
    }


    /**
     * 发送跨链转账tx1
     *
     * @param fromChainNode 出款链节点
     * @param toChain       收款链
     * @param address       收款地址
     * @return
     */
    @Retryable(value = {TxException.class}, maxAttempts = 3, backoff = @Backoff(delay = 15000L, multiplier = 2))
    public String sendXChainTranferTX1(@NotNull Chain_Node fromChainNode, @NotNull Chain toChain, @NotNull String address) {
        ChainUtil.getOrderByChainId(fromChainNode.getChain().getChainId());

        log.info("TX >> XChainTransferTX1 >> FromChain:{}, ToChain:{}, Address:{} \n", fromChainNode.getChain().getChainId(), toChain.getChainId(), address);

        String hash;

        XChainTransferFromDTO model = XChainTransferFromDTO.builder()
                .dst_chain_id(String.valueOf(toChain.getChainId()))
                .dst_chain_owner(String.valueOf(toChain.getOwner().getAddr()))
                .dst_user_addr(address)
                .dst_contract_addr(EncryptUtil.keccak256ToAddress("x-chain-transferByIaaS-deposit")).build();

        ChainCodeClient client = new ChainCodeClient(fromChainNode.getNode().getIp(), fromChainNode.getRpcPort(), fromChainNode.getChain().getChainId());
        hash = client.xchainTransferTX1(transferTokenAmount, JsonUtil.objToJson(model));
        if (hash == null) {
            throw new TxException(TXID_NULL);
        }
        return hash;
    }


    /**
     * 发送跨链转账tx2
     *
     * @param fromChainNode 出款链节点
     * @param toChainNode   收款链节点
     * @param hash          tx1-hash
     */
    @Retryable(value = {TxException.class}, maxAttempts = 3, backoff = @Backoff(delay = 15000L, multiplier = 2))
    public void sendXChainTranferTX2(@NotNull Chain_Node fromChainNode, @NotNull Chain_Node toChainNode, @NotNull String hash) {
        ChainUtil.getOrderByChainId(toChainNode.getChain().getChainId());

        log.info("TX >> XChainTransferTX2 >> FromChain:{}, ToChain:{}, Hash:{} \n", fromChainNode.getChain().getChainId(), toChainNode.getChain().getChainId(), hash);

        ChainCodeClient client1 = new ChainCodeClient(fromChainNode.getNode().getIp(), fromChainNode.getRpcPort(), fromChainNode.getChain().getChainId());

        String txMsg = null;
        while (txMsg == null) {
            txMsg = client1.tx1Finished(hash);
            ThreadUtil.sleep(5);
        }

        XChainTransferToDTO model = XChainTransferToDTO.builder()
                .src_chain_id(String.valueOf(fromChainNode.getChain().getChainId()))
                .src_chain_owner(String.valueOf(fromChainNode.getChain().getOwner().getAddr()))
                .tx_msg(txMsg).build();

        ChainCodeClient client2 = new ChainCodeClient(toChainNode.getNode().getIp(), toChainNode.getRpcPort(), toChainNode.getChain().getChainId());
        String txid = client2.xchainTransferTX2(transferTokenAmount, JsonUtil.objToJson(model));
        if (txid == null) {
            throw new TxException(TXID_NULL);
        }

    }


    /**
     * 发送普通转账交易
     *
     * @param chainNode 链节点
     * @param address   收款地址
     */
    @Retryable(value = {TxException.class}, maxAttempts = 3, backoff = @Backoff(delay = 10000L, multiplier = 2))
    public void sendNormalTranferTx(@NotNull Chain_Node chainNode, @NotNull String address) {
        ChainUtil.getOrderByChainId(chainNode.getChain().getChainId());

        ChainCodeClient client = new ChainCodeClient(chainNode.getNode().getIp(), chainNode.getRpcPort(), chainNode.getChain().getChainId());

        log.info("TX >> Normal Transfer >> ChainId:{}, NodeId:{}, Address:{} \n", chainNode.getChain().getChainId(), chainNode.getNode().getId(), address);

        String txid = client.transferByIaaS(address, transferTokenAmount);
        if (txid == null) {
            throw new TxException(TXID_NULL);
        }
    }


    /**
     * 发送更新委员会成员交易
     *
     * @param fromChain   节点源链
     * @param toChainNode 目标链节点
     * @param addressList 地址列表
     */
    @Retryable(value = {TxException.class}, maxAttempts = 3, backoff = @Backoff(delay = 10000L, multiplier = 2))
    public void sendUpdateConsensusNodeTx(Chain fromChain, @NotNull Chain_Node toChainNode, @NotNull List<String> addressList) {
        ChainUtil.getOrderByChainId(toChainNode.getChain().getChainId());

        //update commit height
        Chain toChain = chainRepository.findChainByChainId(toChainNode.getChain().getChainId());

        log.info("TX >> Update Consensus Node >> ChainId:{}, NodeId:{}, Address:{} \n", toChainNode.getChain().getChainId(), toChainNode.getNode().getId(), addressList);

        ConsensusNodeUpdTxDTO dto = ConsensusNodeUpdTxDTO.builder()
                .FromChainID(fromChain.getChainId())
                .ToChainID(toChainNode.getChain().getChainId())
                .Address(addressList)
                .CommitHeight(toChain.getCommitHeight() + 5)
                .Cert(ChainUtil.getCertifacateByName(CERT_NAME))
                .NodeType(ConsensusNodeUpdTxDTO.nodeType.CONSENSUS.ordinal()).build();

        ChainCodeClient client = new ChainCodeClient(toChainNode.getNode().getIp(), toChainNode.getRpcPort(), toChainNode.getChain().getChainId());
        String txid = client.updateConsensusNode(dto);

        if (txid == null) {
            throw new TxException(TXID_NULL);
        }
    }


    /**
     * 查询链Commit Number
     *
     * @param chainNode 链节点
     * @return
     */
    public String queryBlockCommitNumber(@NotNull Chain_Node chainNode) {
        ChainCodeClient client = new ChainCodeClient(chainNode.getNode().getIp(), chainNode.getRpcPort(), chainNode.getChain().getChainId());
        return client.getBlockCommitNumber();
    }


    /**
     * 延迟交易任务
     *
     * @throws Exception
     */
    @Scheduled(initialDelay = 120000, fixedDelay = 30000)
    protected void txTask() throws Exception {

        executeAssignTask();
        executeUpdateWhiteListTask();
        executeDeleteChainNodeTask();

    }


    /**
     * 执行指派任务
     *
     * @throws Exception
     */
    private void executeAssignTask() throws Exception {
        if (!assignChainNodeMap.isEmpty()) {
            log.info("Assign Chain Node Tx Task Start >> target-chain-node amount:{} \n", assignChainNodeMap.size());
            Iterator<Map.Entry<Chain_Node, Chain>> iterator = assignChainNodeMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Chain_Node, Chain> next = iterator.next();
                Chain_Node key = next.getKey();
                Chain value = next.getValue();

                key = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(key.getChain(), key.getNode(), Chain_Node.Stat.REMOVED);
                if (Objects.isNull(key)) {
                    log.info("chain-node not exist, give up assign ...");
                    iterator.remove();
                    continue;
                }
                if (key.isEvidenced()) {
                    log.info("start to process assign chain node ...");
                    startSendAssignTrustChainTx(value, key);
                    iterator.remove();
                }
            }
            log.info("Assign Chain Node Tx Task End >> target-chain-node amount:{} \n", assignChainNodeMap.size());
        }

        if (!assignChainMap.isEmpty()) {
            log.info("Assign Chain Tx Task Start >> target-chain amount:{} \n", assignChainMap.size());
            Iterator<Map.Entry<Chain, Chain>> iterator = assignChainMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Chain, Chain> next = iterator.next();
                Chain key = next.getKey();
                Chain value = next.getValue();
                Chain_Node activeChainNode = chainManager.getActiveChainNode(key);
                if (Objects.nonNull(activeChainNode)) {
                    log.info("start to process assign chain ...");
                    startSendAssignTrustChainTx(value, key);
                    iterator.remove();
                }
            }
            log.info("Assign Chain Tx Task End >> target-chain amount:{} \n", assignChainMap.size());
        }
    }


    /**
     * 执行更新白名单任务
     *
     * @throws Exception
     */
    private void executeUpdateWhiteListTask() throws Exception {
        if (!updateWhiteListMap.isEmpty()) {
            log.info("Update White List Tx Task Start >> target-chain amount:{} \n", assignChainMap.size());
            Iterator<Map.Entry<Chain, TrustTxAddressUpdTxDTO>> iterator = updateWhiteListMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Chain, TrustTxAddressUpdTxDTO> next = iterator.next();
                Chain key = next.getKey();
                TrustTxAddressUpdTxDTO value = next.getValue();

                Chain_Node activeChainNode = chainManager.getActiveChainNode(key);
                if (Objects.nonNull(activeChainNode)) {
                    log.info("start to process update white list ...");
                    startSendUpdateTrustTxAddressTx(key, value.getNodes(), EnumUtil.getEnumByCode(TrustTxAddressUpdTxDTO.Type.class, value.getType()));
                    iterator.remove();
                }
            }
            log.info("Update White List Tx Task End >> target-chain amount:{} \n", assignChainMap.size());
        }
    }


    /**
     * 执行删除链节点任务
     *
     * @throws Exception
     */
    private void executeDeleteChainNodeTask() throws Exception {
        if (!deleteChainNodeMap.isEmpty()) {
            log.info("Delete Chain Node Task Start >> target-chain amount:{} \n", deleteChainNodeMap.size());
            Iterator<Map.Entry<String, Chain_Node>> iterator = deleteChainNodeMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Chain_Node> next = iterator.next();
                Chain_Node value = next.getValue();
                Chain_Node chainNode = chainNodeRepository.findChain_NodeByNodeAndStatIsNotAndChain_Type(value.getNode(), Chain_Node.Stat.REMOVED, Chain.Type.TRUST_CHAIN);

                long span = (System.currentTimeMillis() - chainNode.getCreatedAt().getTime()) / (1000 * 60);
                if (span > 5) {
                    List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByNode_IdAndStatIsNot(value.getNode().getId(), Chain_Node.Stat.REMOVED);
                    chainNodes.sort(Comparator.comparing(Chain_Node::getCreatedAt).reversed());
                    List<Chain_Node> list = new ArrayList<>();
                    for (Chain_Node cn : chainNodes) {
                        if (chainManager.isActive(cn)) {
                            list.add(cn);
                        }
                    }
                    chainNodes = list;
                    if (!chainNodes.isEmpty()) {
                        log.info("start to process deleting chain node by another chain ...");
                        startSendDeleteChainTx(value.getNode(), value.getChain(), chainNodes.get(0).getChain());
                        iterator.remove();
                        continue;
                    }
                }

                if (chainManager.isActive(chainNode)) {
                    log.info("start to process deleting chain node by trust chain ...");
                    startSendDeleteChainTx(value.getNode(), value.getChain(), null);
                    iterator.remove();
                    continue;
                }
                log.info("no active chain on the node for now");
            }
            log.info("Delete Chain Node Task End >> target-chain amount:{} \n", deleteChainNodeMap.size());
        }
    }


//    @Recover
//    private void recover(BlockchainDriverException e) {
//        log.error("ERROR : Send tx >> Match Lower TrustChain >>  error:{} \n", e);
//    }


}
