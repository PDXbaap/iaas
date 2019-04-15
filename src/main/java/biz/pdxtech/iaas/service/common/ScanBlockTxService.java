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

import biz.pdxtech.iaas.Application;
import biz.pdxtech.iaas.cluster.ClusterRunner;
import biz.pdxtech.iaas.entity.*;
import biz.pdxtech.iaas.proto.dto.BlockDTO;
import biz.pdxtech.iaas.proto.dto.TransactionDTO;
import biz.pdxtech.iaas.repository.BlockRepository;
import biz.pdxtech.iaas.repository.ChainNodeRepository;
import biz.pdxtech.iaas.repository.ChainOrderRepository;
import biz.pdxtech.iaas.repository.ChainRepository;
import biz.pdxtech.iaas.service.impl.ChainService;
import biz.pdxtech.iaas.util.EthJsonRpc;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ScanBlockTxService {

    private ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private ChainOrderRepository chainOrderRepository;
    @Autowired
    private ChainService chainService;
    @Autowired
    private ChainRepository chainRepository;
    @Autowired
    private ChainNodeRepository chainNodeRepository;
    @Autowired
    private BlockRepository blockRepository;
    @Autowired
    private ClusterRunner clusterRunner;

    @Value("${pdx.iaas.zk.znode.scan.order}")
    private String scanOrder;

    private static Long synBlockNum = 0L;
    private Client client = Client.create();
    private String jsonRpc = "";
    private EthJsonRpc rpc = null;
    private Chain_Node chainNode;

    @PostConstruct
    public void getCurrentBlockNum() {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        synBlockNum = getRepositoryBlock().getNumber();
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 15000)
    public void syncBlock() {

        if (!getOrder()) {
            log.info("Server not got order to scan ...");
            return;
        } else {
            log.info("Server got order to scan !");
            synBlockNum = getRepositoryBlock().getNumber();
        }

        try {
            if (rpc == null || "".equals(jsonRpc)) {
                chainNode = chainService.getBasicServiceChainNode();
                if (Objects.isNull(chainNode)) {
                    log.info("sync block --> not found basic service chain!");
                    return;
                }
                jsonRpc = "http://" + chainNode.getNode().getIp() + ":" + chainNode.getRpcPort();
                rpc = new EthJsonRpc(jsonRpc);
            }

            String blockNumber = rpc.blockNumber();
            if (blockNumber == null) {
                resetRPC(chainNode);
                return;
            }
            if (blockNumber.isEmpty()) {
                return;
            }
            Long blockNum = Long.parseLong(blockNumber.substring(2), 16);
            int time = (int) (blockNum - synBlockNum);
            if (time > 0) {
                handleData(time);
            }
        } catch (Exception e) {
            log.error("Error >> Sync Block, error:{} ", e);
        }
    }


    /**
     * 获取扫描权限
     *
     * @return boolean
     */
    private boolean getOrder() {
        try {
            List<String> strings = clusterRunner.getChildren(scanOrder);
            if (!strings.isEmpty()){
                return  strings.contains(clusterRunner.uuid);
            }

            Stat stat = clusterRunner.exists(scanOrder + "/" + clusterRunner.uuid);
            if (null == stat) {
                clusterRunner.create(scanOrder + "/" + clusterRunner.uuid, "".getBytes(), CreateMode.EPHEMERAL);
                return true;
            } else {
                return false;
            }
        } catch (KeeperException | InterruptedException e) {
            log.error("Error >> zookeeper scan get order error:{} ", e);
            Thread.currentThread().interrupt();
        }
        return false;
    }


    /**
     * 重置rpc连接
     *
     * @param chainNode 链节点
     */
    private void resetRPC(Chain_Node chainNode) {
        long now = System.currentTimeMillis();
        long span = (now - chainNode.getUpdatedAt().getTime()) / (1000 * 60);
        if (span > 2) {
            chainNode.setConnected(false);
            chainNodeRepository.save(chainNode);
            rpc = null;
            jsonRpc = "";
        }
    }


    /**
     * 获取区块已处理高度
     *
     * @return
     */
    private Block getRepositoryBlock() {
        List<Block> all = blockRepository.findAll();
        if (all.isEmpty()) {
            Block block = Block.builder().number(0L).build();
            return blockRepository.save(block);
        } else {
            return all.get(0);
        }
    }


    /**
     * 处理区块数据
     *
     * @param time 次数
     * @throws IOException
     */
    private void handleData(int time) throws IOException {
        log.info("sync block --> handle data --> time:{}, synblocknum:{} \n", time, synBlockNum);
        for (int i = 0; i < time; i++) {
            synBlockNum++;
            String blockString = getBlock("0x" + Long.toHexString(synBlockNum));
            log.trace("get block --> " + blockString);
            JsonNode blockJson = getResult(blockString);
            if (blockJson == null) {
                continue;
            }
            BlockDTO blockdto = mapper.readValue(blockJson.toString(), BlockDTO.class);
            if (blockdto == null) {
                log.error("Error >> BlockDTO NULL, blockJson:{}", blockJson);
            }
            Block block = blockdto.getBlock();
            List<TransactionDTO> txs = blockdto.getTransactions();
            for (TransactionDTO tx : txs) {
                //转帐 创建合约 合约转帐 合约普通调用
                JsonNode receiptJson = getResult(getTransactionReceipt(tx.getHash()));
                if (receiptJson == null) {
                    continue;
                }
                //send
                if (!"0x0".equals(tx.getValue()) && tx.getTo() != null) {
                    if (!tx.getTo().equals(Application.rece_Account.toLowerCase())) {
                        continue;
                    }
                    String value = new BigInteger(tx.getValue().substring(2), 16).pow(18).toString();

                    sycnChainOrder(tx.getFrom(), value);
                }
            }
            Block repositoryBlock = getRepositoryBlock();
            repositoryBlock.setNumber(block.getNumber());
            blockRepository.save(repositoryBlock);
        }

    }


    /**
     * 同步链订单
     *
     * @param from  地址
     * @param va    数量
     */
    private void sycnChainOrder(String from, String va) {

        log.info(" --- Transaction Address Order Match  -->  fromAddr:{}  --- " , from);
        ChainOrder chainOrder = chainOrderRepository.findChainOrderByFromAddrAndStatIs(from, ChainOrder.Stat.DEF_PAY);
        if (chainOrder != null && chainOrder.getStat() == ChainOrder.Stat.DEF_PAY) {
            if (va.startsWith("0x")) {
                va = va.substring(2);
            }

            Double fee = Double.valueOf(va);
            if (chainOrder.getConfFee() > fee) {
                log.info(" --- Payment Insufficient For The Chian Creation Fee --- ");
                return;
            }

            log.info(" --- Transaction Order Match Success --> fromAddr:{}, orderId:{} --- " ,from,chainOrder.getId());
            chainOrder.setStat(ChainOrder.Stat.PAY_FINISH);
            chainOrder.setUpdateTime(new Date());
            chainOrderRepository.save(chainOrder);

            log.info(" --- Start Sending Chain Creation ...  --- ");
            // 发送创建任务
            if (chainOrder.getType() == ChainOrder.Type.CREATE) {
                ChainCreation request = ChainCreation.builder().chain(chainOrder.getChain()).type(ChainCreation.Type.PROVISIONING).time(System.currentTimeMillis()).build();
                chainService.submit(request);
            } else {
                Chain updateChain = chainOrder.getChain();
                updateChain.setDeadline(addmonth(updateChain.getDeadline(), chainOrder.getFeeMode()));
                chainRepository.save(updateChain);
            }
//            chainRegistry.create(chainOrder.getChain());

        }
    }

    private Long addmonth(Long oldTime, int month) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(oldTime), ZoneId.systemDefault());
        return localDateTime.plusMonths(month).toInstant(ZoneOffset.of("+8")).toEpochMilli();
    }

    private String getBlock(String blockNumber) {
        Builder builder = client.resource(jsonRpc).type(MediaType.APPLICATION_JSON);
        String param = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBlockByNumber\",\"params\":[\""
                + blockNumber + "\", true],\"id\":1}";
        ClientResponse clientResponse = builder.entity(param).post(ClientResponse.class);
        String result = clientResponse.getEntity(String.class);
        return result;
    }

    private String getTransactionReceipt(String txHash) {
        Builder builder = client.resource(jsonRpc).type(MediaType.APPLICATION_JSON);
        String param = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getTransactionReceipt\",\"params\":[\""
                + txHash + "\"],\"id\":1}";
        ClientResponse clientResponse = builder.entity(param).post(ClientResponse.class);
        String result = clientResponse.getEntity(String.class);
        return result;
    }

    private JsonNode getResult(String jsonData) throws IOException {
        JsonNode data = mapper.readValue(jsonData, JsonNode.class);
        JsonNode result = data.get("result");
        if (result == null) {
            log.error("jsonData error ===> " + jsonData);
        }
        return result;
    }

}
