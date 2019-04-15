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
import biz.pdxtech.iaas.entity.Address;
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
public class ChainCodeClientTest {

    public static void main(String[] args) throws Exception {

        IAAS_ADDRESS = "0xde587cff6c1137c4eb992cf8149ecff3e163ee07";
        IAAS_PRIVATE_KEY = "a2f1a32e5234f64a6624210b871c22909034f24a52166369c2619681390433aa";

        testUpdateTrustTxAddress();
        testTrustChainUpdate();
        testCreateChain();
        testLiuSongTransfer();
        testDeleteChain();
        testXChainTranferTX1();
        testTX1Finished();
        testXChainTranferTX2();
        testBalance();
        testNormalTransfer();
        testUpdateConsensusNode();
        testBlockCommitNumber();
    }

    private static void testChaincodeAddress() {
        String s = EncryptUtil.keccak256ToAddress("x-chain-transfer-withdraw");
        System.out.println(s);
    }


    private static void testBlockCommitNumber() {
        ChainCodeClient client = new ChainCodeClient("10.0.0.175", 30130, 739L);
        String number = client.getBlockCommitNumber();
        System.out.println(number);
    }


    private static void testUpdateConsensusNode() {

        String chainA_IP = "10.0.0.155";
        int chainA_Port = 8546;
        Long chainA_ID = 739L;
        String chainA_PrivateKey = "55ea77dc7293e0d8a231654e2757f881a916d7e21592ee30bb0a8840b634ce48";
        String cert = ChainUtil.getCertifacateByName("tcadm.crt");

        ConsensusNodeUpdTxDTO dto = ConsensusNodeUpdTxDTO.builder()
                .NodeType(ConsensusNodeUpdTxDTO.nodeType.CONSENSUS.ordinal())
                .Cert(cert)
                .CommitHeight(5L)
                .ToChainID(739L)
                .FromChainID(738L)
                .Address(Arrays.asList("0x08b299d855734914cd7b19eea60dc84b825680f9", "0xeaf7d7d101cf089fb87523dd4e5187c49dbc78dd"))
                .build();

        ChainCodeClient client = new ChainCodeClient(chainA_IP, chainA_Port, chainA_ID);
        client.updateConsensusNode(dto);
    }

    private static void testUpdateTrustTxAddress() {
        String privateKey = "55ea77dc7293e0d8a231654e2757f881a916d7e21592ee30bb0a8840b634ce48";
        //String privateKey = "08414f44845761f9f5a5558b181a435a9b6b312a750ee4ef9b959d8401a07b46";
        String publicKey = "03acf1faaebee8f406f21dd773aa8a8bbc838e26aa3444282b83269c16356531ca";
        String address = "0xde587cff6c1137c4eb992cf8149ecff3e163ee07";

        List<String> list = new ArrayList<>();

        list.add("0xde587cff6c1137c4eb992cf8149ecff3e163ee07");
        list.add("0xd32f7041722aab5108fc78036596ea3dad5ef192");
        list.add("0xBC552D6D8BDeC1EDf12Ed622F9B6AD4d13dB016F");
        list.add("0x9AE2aAf193A0C8C082ACe36ce405CE4906257088");
        list.add("0xE36F5457079e6E8EBFc8f0b794B638DE9193347e");
        list.add("0xF5c78FcA5Eb24C82a3Cc2eE336Ee50D189910f1d");
        list.add("0x4dA3742Ad9e7cBd31fbde184Cd215DCA88e04de5");
        list.add("0xF4eA2bbe9166750F0719D47308c7E91F80DA31fe");

        TrustTxAddressUpdTxDTO model = TrustTxAddressUpdTxDTO.builder()
                .nodes(list)
                .type(1)
                .timestamp(System.currentTimeMillis())
                .random(String.valueOf(RandomUtils.nextInt(1000, 9999)))
                .tcadmCrt(ChainUtil.getCertifacateByName("tcadm.crt")).build();

        ChainCodeClient client = new ChainCodeClient("10.0.0.191", 30130, 142726080980058112L);
        client.updateTrustTxAddress(model);
    }


    private static void testTrustChainUpdate() throws BlockchainDriverException {

        String chainA_IP = "10.0.0.148";
        int chainA_Port = 8545;
        long chainA_ID = 739L;
        String chainA_PrivateKey = "55ea77dc7293e0d8a231654e2757f881a916d7e21592ee30bb0a8840b634ce48";

//        String chainB_IP = "10.0.0.148";
//        int chainB_Port = 8545;
//        String chainB_ID = "739";

        String chainB_IP = "10.0.0.154";
        int chainB_Port = 8545;
        String chainB_ID = "738";


//        String chainA_IP = "47.105.222.58";
//        int chainA_Port = 30130;
//        long chainA_ID = 138407375492087808L;
//        String chainA_PrivateKey = "55ea77dc7293e0d8a231654e2757f881a916d7e21592ee30bb0a8840b634ce48";
//
//        String chainB_IP = "47.92.111.150";
//        int chainB_Port = 30130;
//        String chainB_ID = "138414825754591232";


        ChainCodeClient client = new ChainCodeClient(chainA_IP, chainA_Port, chainA_ID, "tcUpdater", "", chainA_PrivateKey);
        List<String> enodeList = Arrays.asList("enode://62e0efb85d4e61ca17d88650b52827a491972b16f43672d81864b34bced7dd5dfb2f925e59dbbe19923ddd606e1ca68b59275744ffbbbb114ed28c98935d703d@" + chainB_IP + ":" + chainB_Port);
        List<String> hostList = Arrays.asList("http://" + chainB_IP + ":" + chainB_Port);

        String cert = ChainUtil.getCertifacateByName("tcadm.crt");

        TrustChainUpdTxDTO model = TrustChainUpdTxDTO.builder()
                .chainId(String.valueOf(chainB_ID)).chainOwner("0xd32f7041722aab5108fc78036596ea3dad5ef192")
                .prevChainId(String.valueOf("138407408744529920"))
                .prevChainOwner("0xd32f7041722aab5108fc78036596ea3dad5ef192")
                .selfHost("")
                .random(String.valueOf(RandomUtils.nextInt(10000, 90000)))
                .timestamp(System.currentTimeMillis())
                .enodeList(enodeList)
                .hostList(hostList)
                .tcadmCrt(cert).build();

        System.out.println(JsonUtil.objToJson(model));
        client.updateTrustChain(model);
    }


//    public static void testTrustChainUpdate() throws BlockchainDriverException {
//
//        ChainCodeClient cc = new ChainCodeClient("10.0.0.135", 30030, 146, "tcUpdater","", "55ea77dc7293e0d8a231654e2757f881a916d7e21592ee30bb0a8840b634ce48");
//        List<String> enodeList = Arrays.asList("enode://bf9219bf5e6bc6e30fe2d0d12bae357787a1390f8f74fb6fca92e9517a24318934edcb92ca886e2383f93c108b56891d3a9da78e605db9ddcc27a69b43102800@10.0.0.155:12181", "enode://3e3e179197a47a53dded4522de140cfcb8e956f262bc443d8e314d7401ed64c4a8e8c08839eb58a640a4f3e7593d791b34b8e0992a2ed66f1995fcbc98aae8a6@10.0.0.156:12181");
//        List<String> hostList = Arrays.asList("http://10.0.0.135:30030");
//
//        String cert = ChainUtil.getCertifacateByName("tcadm.crt");
//
//        TrustChainUpdTxDTO dto = TrustChainUpdTxDTO.builder()
//                .chainId("146").chainOwner("de587cff6c1137c4eb992cf8149ecff3e163ee07")
//                .prevChainId("146").prevChainOwner("de587cff6c1137c4eb992cf8149ecff3e163ee07")
//                .selfHost("http://10.0.0.135:30030")
//                .random(String.valueOf(RandomUtils.nextInt(10000, 90000)))
//                .timestamp(System.currentTimeMillis()).enodeList(enodeList).hostList(hostList)
//                .tcadmCrt(cert).build();
//
//        ArrayList<byte[]> list = new ArrayList<>();
//        list.add((JsonUtil.objToJson(dto)).getBytes());
//        Invocation build = Invocation.builder().fcn("createNode").args(list).build();
////        Transaction build = Transaction.builder().fcn("createNode").params(list).build();
//        cc.applyForInBuildCC(build);
//    }


    private static void testCreateChain() throws BlockchainDriverException {

        ChainCodeClient client = ChainCodeClient.getBaaPClient("10.0.0.155", 8546, 738);

        String genesis = new ChainManager().getCommonGenesisJson("50", 5, 2000);
        Address addr = Address.builder().addr("0x0a16DA41C48beeE39FC516f850Adb3961E7Dfc2d").build();
        Chain chain = Chain.builder().chainId(50L).stack(Chain.Stack.PDX).genesis(genesis).build();
        Node node = Node.builder().enode("enode://134215432532465").addr(addr).build();
        List<DeployInfoDTO> dto = new ArrayList<>();
        DeployInfoDTO build = DeployInfoDTO.builder()
                .pbk("02cc3e2432b5a73c2213d38209895e9d641e3d9df739b66750ed307b6127f6b779")
                .fileName("testcc-jar-with-dependencies.jar")
                .fileId("9029C92B")
                .fileHash("aefebb88f84a85f4b885d19a3062330668ae34be06c1d0e1cce2d8b3dd54c748")
                .chaincodeId("1ceb7edecea8d481aa315b9a51b65c4def9b3dc6:asdaf:2.2.5")
                .build();
        dto.add(build);

        client.createChain(chain,node,dto);
    }

    private static void testDeleteChain() throws BlockchainDriverException {

        ChainCodeClient client = ChainCodeClient.getBaaPClient("10.0.0.148", 8545, 739);
        String genesis = new ChainManager().getCommonGenesisJson("47", 5, 2000);
        Chain chain = Chain.builder().chainId(47L).stack(Chain.Stack.PDX).genesis(genesis).build();
        Node node = Node.builder().enode("enode://4254235642365436").build();
        client.deleteChain(chain,node);

    }

    private static void testXChainTranferTX1() {
        String privateKey = "55ea77dc7293e0d8a231654e2757f881a916d7e21592ee30bb0a8840b634ce48";
        //String privateKey = "08414f44845761f9f5a5558b181a435a9b6b312a750ee4ef9b959d8401a07b46";
        String publicKey = "03acf1faaebee8f406f21dd773aa8a8bbc838e26aa3444282b83269c16356531ca";
        String address = "0xde587cff6c1137c4eb992cf8149ecff3e163ee07";

        String s = EncryptUtil.keccak256ToAddress("x-chain-transferByIaaS-deposit");
        System.out.println(s);
        String s2 = "0x" + s;
        System.out.println(s2);

        XChainTransferFromDTO model = XChainTransferFromDTO.builder()
                .dst_chain_id("60")
                .dst_chain_owner("0x8000d109daef5c81799bc01d4d82b0589deedb33")
                .dst_user_addr("0x251b3740a02a1c5cf5ffcdf60d42ed2a8398ddc8")
                .dst_contract_addr(s2).build();

        ChainCodeClient client = new ChainCodeClient("10.0.0.161", 30130, 5L);
        client.xchainTransferTX1("10000000000000000000", JsonUtil.objToJson(model));
    }

    private static void testTX1Finished() {
        String hash = "0x1eccfb286d0bfc4f64ec8c1bd45f5e069fc716a899785419d97de1db9cd27362";
        ChainCodeClient client = new ChainCodeClient("10.0.0.161", 30030, 60L);
        String s = client.tx1Finished(hash);
        System.out.println(s);
    }

    private static void testXChainTranferTX2() {
        String privateKey = "55ea77dc7293e0d8a231654e2757f881a916d7e21592ee30bb0a8840b634ce48";
        String publicKey = "03acf1faaebee8f406f21dd773aa8a8bbc838e26aa3444282b83269c16356531ca";
        String address = "0xde587cff6c1137c4eb992cf8149ecff3e163ee07";

        XChainTransferToDTO model = XChainTransferToDTO.builder()
                .src_chain_id("5")
                .src_chain_owner("0x8000d109daef5c81799bc01d4d82b0589deedb33")
                .tx_msg("0xf9013e018504a817c8008347e7c494fa93c1cb54d24367944b163fcad756253e24ad86888ac7230489e80000b8d07b226473745f636861696e5f6964223a223630222c226473745f636861696e5f6f776e6572223a22307838303030643130396461656635633831373939626330316434643832623035383964656564623333222c226473745f757365725f61646472223a22307832353162333734306130326131633563663566666364663630643432656432613833393864646338222c226473745f636f6e74726163745f61646472223a2262613432633462346130633865373232656430336639643564636437326636636531326539653435227d1ba038268cfdcc0959308ecee1ece0a70ee75fd91b181d68fa2d029ced8492f8082ca02559d1baced379188eebad39c6290813f72116e3dc2aa503c7f0ca40d3b6ad9b").build();

        ChainCodeClient client = new ChainCodeClient("10.0.0.142", 30030, 738L);
        client.xchainTransferTX2("10000000000000000000", JsonUtil.objToJson(model));
    }

    private static void testBalance() {
        String address = "0xd32f7041722aab5108fc78036596ea3dad5ef192";
        ChainCodeClient client = new ChainCodeClient("47.104.144.83", 30130, 6L);
        String balance = client.getBalance(address);
        System.out.println(RadixUtil.hexToBigDecimal(balance).toString());
    }

    private static void testNormalTransfer() {
        //给用户转账
//        String from = "0xde587cff6c1137c4eb992cf8149ecff3e163ee07";
//        String to = "0xd32f7041722aab5108fc78036596ea3dad5ef192";
//        String privateKey = "55ea77dc7293e0d8a231654e2757f881a916d7e21592ee30bb0a8840b634ce48";
//        String value = "20000000000000000000000";

        //用户付款
        String from = "0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6";
        String to = "0x0a16DA41C48beeE39FC516f850Adb3961E7Dfc2d";
        String privateKey = "65035d9621f7be3bb6dc1f5a646e6ee2ef6bddf3f1ce57782d409c23857401a6";
        String value = "1000001";

        ChainCodeClient client = new ChainCodeClient("47.104.144.83", 30130, 6L);
        String balance = client.getBalance(from);
        System.out.println(RadixUtil.hexToBigDecimal(balance).toString());

        client.transfer(from, privateKey, to, value);

//        String balance = client.ethJsonRpc.getBalance(from);
//        System.out.println(RadixUtil.hexToBigDecimal(balance).toString());
    }

    private static void testLiuSongTransfer() {
        String from = "0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6";
        String to = "0x7ad0d27ae983bbe01805125a5612075f7271087d";
        String privateKey = "65035d9621f7be3bb6dc1f5a646e6ee2ef6bddf3f1ce57782d409c23857401a6";
        String value = "8000000000000000000000000000";

        ChainCodeClient client = new ChainCodeClient("10.0.0.135", 30130, 0L);
        client.transfer(from, privateKey, to, value);
    }

}

