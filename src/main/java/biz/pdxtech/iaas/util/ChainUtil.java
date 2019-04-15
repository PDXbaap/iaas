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

import biz.pdxtech.iaas.entity.Chain_Node;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChainUtil {

    private static String chainRpcPort = "";
    private static String chainId = "";
    private static String stackHost = "";

    private static String IAAS_ADDRESS;
    private static String BASIC_SERVICE_CHAIN_NAME;
    private static String BASIC_SERVICE_CHAIN_TOKEN;
    private static String BASIC_SERVICE_CHAIN_ADDRESS;
    private static String TRANSFER_TOKEN_LIMIT;
    private static String TRANSFER_TOKEN_AMOUNT;

    @Value("${pdx.iaas.address}")
    private String iaasAddress;

    @Value("${pdx.iaas.basic-service-chain.name}")
    private String basicServiceChainName;
    @Value("${pdx.iaas.basic-service-chain.token}")
    private String basicServiceChainToken;
    @Value("${pdx.iaas.basic-service-chain.address}")
    private String basicServiceChainAddress;
    @Value("${pdx.iaas.transfer.token.limit}")
    private String transferTokenLimit;
    @Value("${pdx.iaas.transfer.token.amount}")
    private String transferTokenAmount;

    @PostConstruct
    private void init() {
        IAAS_ADDRESS = iaasAddress;
        BASIC_SERVICE_CHAIN_NAME = basicServiceChainName;
        BASIC_SERVICE_CHAIN_TOKEN = basicServiceChainToken;
        BASIC_SERVICE_CHAIN_ADDRESS = basicServiceChainAddress;
        TRANSFER_TOKEN_LIMIT = transferTokenLimit;
        TRANSFER_TOKEN_AMOUNT = transferTokenAmount;
    }


//    public static Pair<Boolean, String> query(Chaincode chaincode, String method, String... params) {
//        EthereumBlockchainDriver driver = BaapDriverUtil.getDefault();
//        if (driver == null) {
//            return new ImmutablePair<>(false, "internal error!");
//        }
//        ArrayList<byte[]> paramList = new ArrayList<>();
//        for (String param : params) {
//            paramList.add(param.getBytes());
//        }
//        Transaction tx = Transaction.builder().fcn(method).params(paramList).build();
//        try {
//            byte[] query = driver.query(chaincode, tx);
//            String responseStr = new String(query);
//            return new ImmutablePair<>(true, responseStr);
//        } catch (BlockchainDriverException e) {
//            e.printStackTrace();
//            return new ImmutablePair<>(false, e.getMessage());
//        }
//    }

//    public static String getPort() {
//        if (StringUtils.isNotEmpty(chainRpcPort)) {
//            return chainRpcPort;
//        }
//        loadProperties();
//        return chainRpcPort;
//    }
//
//    public static String getChainId() {
//        if (StringUtils.isNotEmpty(chainId)) {
//            return chainId;
//        }
//        loadProperties();
//        return chainId;
//    }
//
//    public static String getStackHost() {
//        if (StringUtils.isNotEmpty(stackHost)) {
//            return stackHost;
//        }
//        loadProperties();
//        return stackHost;
//    }
//
//    private static void loadProperties() {
//        String stackUrl = System.getenv("stack_host");
//        String stackId = System.getenv("stack_id");
//        if (StringUtils.isNotEmpty(stackUrl) && StringUtils.isNotEmpty(stackId)) {
//            chainId = stackId;
//            stackHost = stackUrl;
//            chainRpcPort = stackUrl.split(":")[1];
//        }
//    }


    public static String getCommonGenesisJson(String chainId) {
        String alloc = "\"0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\":{\"balance\":\"10000000000000000000000000000000\"}";
        return getGenesisJson(chainId, alloc, "", 5, 2000);
//        return getGenesisJson(chainId, "", "");
    }

    public static String getGenesisJsonWithTokenChain(String chainId, String tokenChain) {
        String alloc = "\"0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\":{\"balance\":\"10000000000000000000000000000000\"}";
        return getGenesisJson(chainId, alloc, tokenChain, 5, 2000);
        //return getGenesisJson(chainId, "", tokenChain);
    }

    public static String getBasicServiceChainGenesisJson(String chainId) {
        String alloc = "\"" + IAAS_ADDRESS + "\":{\"balance\":\"" + BASIC_SERVICE_CHAIN_TOKEN + "\"}," +
                "\"0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\":{\"balance\":\"10000000000000000000000000000000\"}";
        //String alloc = "\"" + IAAS_ADDRESS + "\":{\"balance\":\"" + BASIC_SERVICE_CHAIN_TOKEN + "\"}" +
        return getGenesisJson(chainId, alloc, "", 5, 2000);
    }

    public static String getTrustChainGenesisJson(String chainId, String tokenChain) {
        String alloc = "\"" + IAAS_ADDRESS + "\":{\"balance\":\"" + TRANSFER_TOKEN_LIMIT + "\"}," +
                "\"0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\":{\"balance\":\"10000000000000000000000000000000\"}";
        //String alloc = "\"" + IAAS_ADDRESS + "\":{\"balance\":\"" + TRANSFER_TOKEN_LIMIT + "\"}" +
        return getGenesisJson(chainId, alloc, tokenChain, 5, 2000);
    }


    private static String getGenesisJson(String chainId, String alloc, String tokenChain, int cfd, int blockDelay) {

        log.info("Generate genesis.json >> ChainId:{}, tokenChain:{} \n", chainId, tokenChain);

        String template = "{" +
                "  \"config\": {" +
                "    \"chainId\": %s," +
                "    \"homesteadBlock\": 0," +
                "    \"eip155Block\": 0," +
                "    \"eip158Block\": 0," +
                "    \"utopia\": {" +
                "      \"epoch\":30000," +
                "      \"cfd\":%s," +
                "      \"numMasters\":3," +
                "      \"blockDelay\":%s" +
                "       %s" +
                "    }" +
                "  }," +
                "  \"alloc\": {" +
                "       %s" +
                "}," +
                "  \"nonce\": \"0x0000000000000042\"," +
                "  \"difficulty\": \"0x1000\"," +
                "  \"mixhash\": \"0x0000000000000000000000000000000000000000000000000000000000000000\"," +
                "  \"coinbase\": \"0x0000000000000000000000000000000000000000\"," +
                "  \"timestamp\": \"0x00\"," +
                "  \"parentHash\": \"0x0000000000000000000000000000000000000000000000000000000000000000\"," +
                "  \"extraData\": \"\"," +
                "  \"gasLimit\": \"0x74523528\"" +
                "}";

        if ("".equals(tokenChain)) {
            tokenChain = ",\"noRewards\":false";
        } else {
            tokenChain = ",\"noRewards\":true,\"tokenChain\":" + tokenChain;
        }

        return String.format(template, chainId, String.valueOf(cfd), String.valueOf(blockDelay), tokenChain, alloc);
    }

    private static ConcurrentHashMap<Long, Long> concurrentHashMap = new ConcurrentHashMap();
    private final static long WAIT_SPAN = 10000;

    private static int getWaitTime(Long chainId) {
        Long saveTime = concurrentHashMap.get(chainId);
        long now = System.currentTimeMillis();
        if (saveTime == null) {
            concurrentHashMap.put(chainId, now);
            return 0;
        }
        if (now > saveTime) {
            if ((now - saveTime) > WAIT_SPAN) {
                concurrentHashMap.put(chainId, now);
                return 0;
            }
            long wait = WAIT_SPAN - (now - saveTime);
            concurrentHashMap.put(chainId, saveTime + WAIT_SPAN);
            return (int) (wait / 1000);
        } else {
            long span = saveTime + WAIT_SPAN;
            concurrentHashMap.put(chainId, span);
            return (int) ((span - now) / 1000);
        }
    }

    private static void clearMap() {
        for (Map.Entry<Long, Long> entry : concurrentHashMap.entrySet()) {
            Long time = entry.getValue();
            if ((time - System.currentTimeMillis()) > WAIT_SPAN) {
                concurrentHashMap.remove(entry.getKey());
            }
        }
    }

    /**
     * 获取发送交易次序
     *
     * @param chainId 链id
     */
    public static void getOrderByChainId(Long chainId) {
        int waitTime = getWaitTime(chainId);

        log.warn("ChainId:{} >> send tx frequently, wait for {} seconds \n", chainId, waitTime);
        try {
            Thread.sleep(waitTime * 1000L);
        } catch (InterruptedException e) {
            log.error("Error >> Thread Sleep, error:{}", e);
            Thread.currentThread().interrupt();
        }
        if (concurrentHashMap.size() > 200) {
            clearMap();
        }
    }


    /**
     * 获取证书
     *
     * @param certName 证书名字
     * @return 证书
     */
    public static String getCertifacateByName(String certName) {
        try {
            ClassPathResource classPathResource = new ClassPathResource("conf/" + certName);
            InputStream inputStream = classPathResource.getInputStream();
            return new BufferedReader(new InputStreamReader(inputStream)).lines().parallel().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            return null;
        }

    }



    public static void main(String[] args) {
        String s = "chain:Chain(id=109, chainId=140690313898360832, name=liujiao2, part=630173797, type=BIZ_CHAIN, bcType=PUBLIC, stat=IN_SERVICE, tps=null, open=null, consensus=null, intensity=null, stack=PDX, sizeChain=null, lowerChainId=140680768715227136, deadline=1556369986358, owner=Address(id=86, addr=0x7de2a31d6ca36302ea7b7917c4fc5ef4c12913b6, createdAt=2019-03-27 20:58:08.0, lastSeenAt=null), sizeDesired=10, sizeService=9, shareChainId=140680737140506624, islandState=false, cfd=10, blockDelay=3000, commitHeight=1703, genesis={  \"config\": {    \"chainId\": 140690313898360832,    \"homesteadBlock\": 0,    \"eip155Block\": 0,    \"eip158Block\": 0,    \"utopia\": {      \"epoch\":30000,      \"cfd\":10,      \"numMasters\":3,      \"blockDelay\":3000       ,\"noRewards\":true,\"tokenChain\":[{\"chainId\":\"140680737140506624\",\"chainOwner\":\"0xd32f7041722aab5108fc78036596ea3dad5ef192\",\"tokenSymbol\":\"pdx\",\"enodes\":[\"enode://738e69ed0b3ae5346d364cb4473e745b6a5f74ff61f03780c92fd454b53bbd342fa559d422dbc826dd908ead960676f024fd14c910169560ba4ef931091acdc4@39.98.223.123:30130\",\"enode://30b2917fccf7bbd74773930c5ef6c4fd59587005c5a982d1735eba9a79e35a96886e0d4f2f32d687a8a0f32af26b86f5ccc690064e5ada2d186e43ff5485c234@39.98.89.43:30271\",\"enode://9951fd550e0012137817f279e243944395e333442ce737f6a5edadaddb875a9c348719cfe6b26f026b07b388e44da9fbb6ecc068b54de67c9de31e477883e96a@47.92.111.150:30065\",\"enode://2fe4e956f161f6773309efe3ea40807d487a8e11d4bd3f02a46dded868686261b43c1196b6722c97935e23932f743e7f73cc4ebb3bf14a88b7972f277dde3bf7@47.92.90.190:30175\",\"enode://b96703aba1d69134f785d27d2e243ab52f421c4047cf2693028028b7c007f7534b296c67beb6ca115e0e4e91209c179aba463a75b6c55646994432d28a710d00@47.92.164.38:30074\",\"enode://3074aecf2c34c478605ad433b855b820c8963c18aa4d14ec78814adc698d0fefba49b1a29918dd94b6ee36982095ce13251b9673d5dac4f9a2452c7e0950c3d5@47.92.137.120:30185\",\"enode://02a7b953c09acda3f21a1e4d8c8d7917034f4f44361aa3983af11afdd120a618274e9ba6625b0dd19287e4b7987c83897a0467f43bcd61209347fe7233f9f3de@39.98.63.34:30313\",\"enode://00ada352ae441d8cf86cd3a64d95898e6049ee7ac3c279410bc84a11b6868a0868a4ce611ff546821b988aa386321569bdce22a4286066fce2364864bedd19a3@47.92.113.235:30036\",\"enode://70119638d3507f62b9ba7338b83b8c17fe1db8c6b09501745e037d96289b180c39403e79886e17d1dba24fa4651db5675524888aadfd41f6635890444b79cde0@47.92.165.218:30302\",\"enode://928620b918237181058a7a32ea209861539531dedfe9e2eec2ceed3b67eecfe0f26012f06f9bd8f5edb06832baf8862245f0cb87a363fe222640ed3b3854c306@39.98.67.5:30254\"],\"rpcHosts\":[\"http://39.98.223.123:30130\",\"http://39.98.89.43:30271\",\"http://47.92.111.150:30065\",\"http://47.92.90.190:30175\",\"http://47.92.164.38:30074\",\"http://47.92.137.120:30185\",\"http://39.98.63.34:30313\",\"http://47.92.113.235:30036\",\"http://47.92.165.218:30302\",\"http://39.98.67.5:30254\"]}]    }  },  \"alloc\": {       \"0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\":{\"balance\":\"10000000000000000000000000000000\"}},  \"nonce\": \"0x0000000000000042\",  \"difficulty\": \"0x1000\",  \"mixhash\": \"0x0000000000000000000000000000000000000000000000000000000000000000\",  \"coinbase\": \"0x0000000000000000000000000000000000000000\",  \"timestamp\": \"0x00\",  \"parentHash\": \"0x0000000000000000000000000000000000000000000000000000000000000000\",  \"extraData\": \"\",  \"gasLimit\": \"0x74523528\"}, assignRecord=[{\"preChainId\":140680768715227136,\"curChainId\":140680768715227136}], imgUrl=/home/harrison/pic/chain/067c744b-0198-427c-aae0-5152fe83833d.JPG, properties=null, createdAt=2019-03-27 20:59:46.0), node:Node(id=307, addr=Address(id=306, addr=0x2E03e789468B4B5Ed3CBCAd0745f570A3b6F7607, createdAt=2019-03-28 14:02:30.0, lastSeenAt=null), owner=null, stat=IN_SERVICE, sizeService=3, part=1342174724, ip=39.98.67.5, enode=enode://d2937d38040bf7d8d2ed916d7a8dbd9dbf7a3c7b00d89afbc80ea56096d6dde52d0bba017f9fb5ccb7c854e982506ad1d4e321849e386d6b701a929d26a78491, nodeKey=:bf4cd0a4d90298db75cb33395e8d7f7418a0b53984c1135590c0951fa204772d, minerKey=046e3a5767978258faeccc4ed65d20f06f235baccfd1cd5477ef8c0b825894409f5c9a80b96822b9a0aae271cb73e6c892cafd6c2174353046c0a991d18580c242, position=null, personal=null, properties=null)\n";
    System.out.println(s.getBytes().length / 192 * 80000);
    }


}
