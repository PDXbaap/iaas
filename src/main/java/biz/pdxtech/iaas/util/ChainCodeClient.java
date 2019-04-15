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

package biz.pdxtech.iaas.util;

import biz.pdxtech.baap.api.Constants;
import biz.pdxtech.baap.api.Deployment;
import biz.pdxtech.baap.api.Invocation;
import biz.pdxtech.baap.driver.BlockChainDriverFactory;
import biz.pdxtech.baap.driver.BlockchainDriverException;
import biz.pdxtech.baap.driver.ethereum.EthereumBlockchainDriver;
import biz.pdxtech.baap.util.encrypt.EncryptUtil;
import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.Node;
import biz.pdxtech.iaas.proto.dto.*;
import biz.pdxtech.iaas.service.common.ChainManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.util.*;

import static biz.pdxtech.baap.util.encrypt.util.ByteUtil.longToBytesNoLeadZeroes;
import static biz.pdxtech.iaas.Application.IAAS_ADDRESS;
import static biz.pdxtech.iaas.Application.IAAS_PRIVATE_KEY;


@Slf4j
public class ChainCodeClient {

    private EthereumBlockchainDriver driver;
    private EthJsonRpc ethJsonRpc;
    private String chaincode;
    private String version;
    private String chainid;

    public static ChainCodeClient getBaaPClient(String nodeIp, int rpcPort, long chainId) {
        return new ChainCodeClient(nodeIp, rpcPort, chainId, Constants.BAAP_CHAINIAAS_NAME, Constants.BAAP_CHAINIAAS_VERSION, IAAS_PRIVATE_KEY);
    }

    public static ChainCodeClient getTcUpdaterClient(String nodeIp, int rpcPort, long chainId) {
        return new ChainCodeClient(nodeIp, rpcPort, chainId, "tcUpdater", "", IAAS_PRIVATE_KEY);
    }

    /**
     * 内置合约
     */
    public ChainCodeClient(String nodeIp, int rpcPort, long chainId, String chaincodeName, String chaincodeVersion, String privateKey) {
        String rpcAddress = "http://" + nodeIp + ":" + rpcPort;

        Properties properties = new Properties();
        properties.setProperty(Constants.BAAP_ENGINE_TYPE_KEY, Constants.BAAP_ENGINE_TYPE_ETHEREUM);
        properties.setProperty(Constants.BAAP_ENGINE_URL_HOST_KEY, rpcAddress);
        properties.setProperty(Constants.BAAP_SENDER_PRIVATE_KEY, privateKey);
        properties.setProperty(Constants.BAAP_ENGINE_ID, String.valueOf(chainId));//新添加

        try {
            driver = (EthereumBlockchainDriver) BlockChainDriverFactory.get(properties);
        } catch (BlockchainDriverException e) {
            log.error("Error >> ChainCode >> error:{} \n", e.getMessage());
        }

        chaincode = chaincodeName;
        version = chaincodeVersion;
        chainid = String.valueOf(chainId);
    }

    /**
     * 预编译合约
     */
    public ChainCodeClient(String nodeIp, int rpcPort, Long chainId) {
        String rpcAddress = "http://" + nodeIp + ":" + rpcPort;
        chainid = String.valueOf(chainId);
        try {
            ethJsonRpc = new EthJsonRpc(rpcAddress);
        } catch (MalformedURLException e) {
            log.error("Error >> EthJsonRpc >> error:{} \n", e);
        }
    }


    /**
     * 执行内置合约
     *
     * @param invocation 调用体
     * @return 交易hash
     * @throws BlockchainDriverException
     */
    private String applyForInBuildCC(Invocation invocation) throws BlockchainDriverException {
        Deployment deployment = Deployment.builder().owner("").name(chaincode).version(version).build();
        String address = EncryptUtil.keccak256ToAddress(deployment.getOwner() + ":" + deployment.getName() + ":" + deployment.getVersion());
        String txid = driver.exec(address, invocation);
        log.info("------------ txid:{} ------------ target chain:{} \n", txid, chainid);
        return txid;
    }

    /**
     * 执行预编译合约
     *
     * @param invocation 调用体
     * @return 交易hash
     * @throws BlockchainDriverException
     */
    private String applyForPreBuildCC(Invocation invocation) throws BlockchainDriverException {
        Deployment deployment = Deployment.builder().owner("").name(chaincode).version(version).build();
        String address = EncryptUtil.keccak256ToAddress(deployment.getName());
        String txid = driver.exec(address, invocation);
        log.info("------------ txid:{} ------------ target chain:{} \n", txid, chainid);
        return txid;
    }

    /**
     * 创建链
     *
     * @param chain 链
     * @param node  节点
     * @return 交易hash
     * @throws BlockchainDriverException
     */
    public String createChain(Chain chain, Node node, List<DeployInfoDTO> dto) throws BlockchainDriverException {
        ArrayList<byte[]> list = new ArrayList<>();
        list.add(String.valueOf(chain.getChainId()).getBytes());
        list.add((node.getEnode()).getBytes());
        list.add(chain.getGenesis().getBytes());
        list.add(node.getAddr().getAddr().getBytes());
        list.add(chain.getStack().name().toLowerCase().getBytes());
        list.add(JsonUtil.objToJson(dto).getBytes());
        Invocation invocation = Invocation.builder().fcn("createChain").args(list).build();
        log.info("------------ tx:create chain ------------ target chain:{}", chainid);
        log.info("------------ chain:{}, node:{}", chain.toString(), node.toString());
        return applyForInBuildCC(invocation);
    }

    /**
     * 删除链
     *
     * @param chain 链
     * @param node  节点
     * @return 交易hash
     * @throws BlockchainDriverException
     */
    public String deleteChain(Chain chain, Node node) throws BlockchainDriverException {
        ArrayList<byte[]> list = new ArrayList<>();
        list.add(String.valueOf(chain.getChainId()).getBytes());
        list.add((node.getEnode()).getBytes());
        Invocation invocation = Invocation.builder().fcn("deleteChain").args(list).build();
        log.info("------------ tx:delete chain ------------ target chain:{}", chainid);
        log.info("------------ chain:{}", chain.toString());
        return applyForInBuildCC(invocation);
    }

    /**
     * 更新下层信任链
     *
     * @param dto 信任链数据
     * @return 交易hash
     * @throws BlockchainDriverException
     */
    public String updateTrustChain(TrustChainUpdTxDTO dto) throws BlockchainDriverException {
        ArrayList<byte[]> list = new ArrayList<>();
        list.add((JsonUtil.objToJson(dto)).getBytes());
        Invocation invocation = Invocation.builder().fcn("tcUpdater").args(list).build();
        //Invocation invocation = Invocation.builder().fcn("createNode").args(list).build();
        log.info("------------ tx:update lower tc ------------ target chain:{}", chainid);
        TrustChainUpdTxDTO temp = TrustChainUpdTxDTO.builder().build();
        BeanUtils.copyProperties(dto, temp);
        temp.setTcadmCrt("log not show");
        log.info("------------ model:{}", temp.toString());
        return applyForPreBuildCC(invocation);
    }

    /**
     * 更新信任交易地址白名单
     *
     * @param dto 白名单数据
     * @return 交易hash
     */
    public String updateTrustTxAddress(TrustTxAddressUpdTxDTO dto) {
        log.info("------------ tx:update trust tx address ------------ target chain:{}", chainid);
        String data = JsonUtil.objToJson(dto);
        TrustTxAddressUpdTxDTO temp = TrustTxAddressUpdTxDTO.builder().build();
        BeanUtils.copyProperties(dto, temp);
        temp.setTcadmCrt("log not show");
        log.info("------------ white list update data:{}", temp);
        String to = EncryptUtil.keccak256ToAddress("trust-tx-white-list");
        String nonce = ethJsonRpc.getNonce(IAAS_ADDRESS);
        if (nonce.startsWith("0x")) {
            nonce = nonce.substring(2);
        }
        byte[] bnonce = longToBytesNoLeadZeroes(Long.parseLong(nonce, 16));
        byte[] bgasprice = longToBytesNoLeadZeroes(20000000000L);
        byte[] bgaslimit = longToBytesNoLeadZeroes(4712388L);
        byte[] bvalue = longToBytesNoLeadZeroes(1L);
        byte[] bdata = data.getBytes();
        biz.pdxtech.baap.driver.ethereum.core.Transaction c = new biz.pdxtech.baap.driver.ethereum.core.Transaction(bnonce, bgasprice, bgaslimit, Hex.decode(to), bvalue, bdata);
        c.sign(Hex.decode(IAAS_PRIVATE_KEY));
        byte[] x = c.getEncoded();
        String txid = ethJsonRpc.sendRawTransaction("0x" + Hex.toHexString(x));
        log.info("------------ txid:{} ------------ target chain:{} \n", txid, chainid);
        return txid;
    }

    /**
     * 更新委员会节点交易
     *
     * @param dto 节点数据
     * @return 交易hash
     */
    public String updateConsensusNode(ConsensusNodeUpdTxDTO dto) {
        log.info("------------ tx:consensus node update ------------ target chain:{}", chainid);
        String data = JsonUtil.objToJson(dto);
        ConsensusNodeUpdTxDTO temp = ConsensusNodeUpdTxDTO.builder().build();
        BeanUtils.copyProperties(dto, temp);
        temp.setCert("log not show");
        log.info("------------ consensus node update data:{}", temp);
        String to = EncryptUtil.keccak256ToAddress("consensus-node-update");
        String nonce = ethJsonRpc.getNonce(IAAS_ADDRESS);
        if (nonce.startsWith("0x")) {
            nonce = nonce.substring(2);
        }
        byte[] bnonce = longToBytesNoLeadZeroes(Long.parseLong(nonce, 16));
        byte[] bgasprice = longToBytesNoLeadZeroes(20000000000L);
        byte[] bgaslimit = longToBytesNoLeadZeroes(4712388L);
        byte[] bvalue = longToBytesNoLeadZeroes(1L);
        byte[] bdata = data.getBytes();
        biz.pdxtech.baap.driver.ethereum.core.Transaction c = new biz.pdxtech.baap.driver.ethereum.core.Transaction(bnonce, bgasprice, bgaslimit, Hex.decode(to), bvalue, bdata);
        c.sign(Hex.decode(IAAS_PRIVATE_KEY));
        byte[] x = c.getEncoded();
        String txid = ethJsonRpc.sendRawTransaction("0x" + Hex.toHexString(x));
        log.info("------------ txid:{} ------------ target chain:{} \n", txid, chainid);
        return txid;
    }


    /**
     * 跨链转账TX1
     *
     * @param value 转账金额
     * @param data  转账数据
     * @return 交易hash
     */
    public String xchainTransferTX1(String value, String data) {
        log.info("------------ tx: x-transfer-tx1  ------------ target chain:{}", chainid);
        log.info("------------ data:{}", data);
        String to = EncryptUtil.keccak256ToAddress("x-chain-transfer-withdraw");
        String nonce = ethJsonRpc.getNonce(IAAS_ADDRESS);
        if (nonce.startsWith("0x")) {
            nonce = nonce.substring(2);
        }
        byte[] bnonce = longToBytesNoLeadZeroes(Long.parseLong(nonce, 16));
        byte[] bgasprice = longToBytesNoLeadZeroes(20000000000L);
        byte[] bgaslimit = longToBytesNoLeadZeroes(4712388L);
        byte[] bvalue = Hex.decode(RadixUtil.bigDecimalToHex(new BigDecimal(value)));
        byte[] bdata = data.getBytes();
        biz.pdxtech.baap.driver.ethereum.core.Transaction c = new biz.pdxtech.baap.driver.ethereum.core.Transaction(bnonce, bgasprice, bgaslimit, Hex.decode(to), bvalue, bdata);
        c.sign(Hex.decode(IAAS_PRIVATE_KEY));
        byte[] x = c.getEncoded();
        String txid = ethJsonRpc.sendRawTransaction("0x" + Hex.toHexString(x));
        log.info("------------ txid:{} ------------ target chain:{} \n", txid, chainid);
        return txid;
    }


    /**
     * 跨链转账TX1是否完成
     *
     * @param hash TX1 hash
     * @return 交易hash
     */
    public String tx1Finished(String hash) {
        String txMsg = null;
        Map<String, Object> map = ethJsonRpc.getWithdrawTransaction(hash);
        if (map.get("status").toString().equals("2")) {
            txMsg = map.get("txMsg").toString();
        }
        return txMsg;
    }

    /**
     * 跨链转账TX2
     *
     * @param value 转账金额
     * @param data  转账数据
     * @return 交易hash
     */
    public String xchainTransferTX2(String value, String data) {
        log.info("------------ tx: x-transfer-tx2  ------------ target chain:{}", chainid);
        log.info("------------ data:{}", data);
        String to = EncryptUtil.keccak256ToAddress("x-chain-transfer-deposit");
        String nonce = ethJsonRpc.getNonce(IAAS_ADDRESS);
        if (nonce.startsWith("0x")) {
            nonce = nonce.substring(2);
        }
        byte[] bnonce = longToBytesNoLeadZeroes(Long.parseLong(nonce, 16));
        byte[] bgasprice = longToBytesNoLeadZeroes(20000000000L);
        byte[] bgaslimit = longToBytesNoLeadZeroes(4712388L);
        byte[] bvalue = Hex.decode(RadixUtil.bigDecimalToHex(new BigDecimal(value)));
        byte[] bdata = data.getBytes();
        biz.pdxtech.baap.driver.ethereum.core.Transaction c = new biz.pdxtech.baap.driver.ethereum.core.Transaction(bnonce, bgasprice, bgaslimit, Hex.decode(to), bvalue, bdata);
        c.sign(Hex.decode(IAAS_PRIVATE_KEY));
        byte[] x = c.getEncoded();
        String txid = ethJsonRpc.sendRawTransaction("0x" + Hex.toHexString(x));
        log.info("------------ txid:{} ------------ target chain:{} \n", txid, chainid);
        return txid;
    }

    /**
     * 普通转账
     *
     * @param from       转出账户
     * @param privateKey 转出账户私钥
     * @param to         目标账户
     * @param value      转出金额
     * @return 交易hash
     */
    public String transfer(String from, String privateKey, String to, String value) {
        log.info("------------ tx:normal transfer ------------ target chain:{}", chainid);
        String nonce = ethJsonRpc.getNonce(from);
        if (nonce.startsWith("0x")) {
            nonce = nonce.substring(2);
        }
        to = to.startsWith("0x") ? to.toLowerCase().substring(2) : to.toLowerCase();
        byte[] bnonce = longToBytesNoLeadZeroes(Long.parseLong(nonce, 16));
        byte[] bgasprice = longToBytesNoLeadZeroes(20000000000L);
        byte[] bgaslimit = longToBytesNoLeadZeroes(4712388L);
        byte[] bvalue = Hex.decode(RadixUtil.bigDecimalToHex(new BigDecimal(value)));

        biz.pdxtech.baap.driver.ethereum.core.Transaction c = new biz.pdxtech.baap.driver.ethereum.core.Transaction(bnonce, bgasprice, bgaslimit, Hex.decode(to), bvalue, null);
        c.sign(Hex.decode(privateKey));
        byte[] x = c.getEncoded();
        String txid = ethJsonRpc.sendRawTransaction("0x" + Hex.toHexString(x));
        log.info("------------ txid:{} ------------ target chain:{} \n", txid, chainid);
        return txid;
    }

    /**
     * IaaS账户普通转账
     */
    public String transferByIaaS(String to, String value) {
        return transfer(IAAS_ADDRESS, IAAS_PRIVATE_KEY, to, value);
    }


    /**
     * 获取余额
     */
    public String getBalance(String address) {
        return ethJsonRpc.getBalance(address);
    }

    /**
     * 获取链的Commit高度
     *
     * @return
     */
    public String getBlockCommitNumber() {
        return ethJsonRpc.blockCommitNumber();
    }


}

