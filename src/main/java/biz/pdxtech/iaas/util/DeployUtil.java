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
import biz.pdxtech.baap.driver.BlockchainDriverException;
import biz.pdxtech.baap.driver.ethereum.EthereumBlockchainDriver;
import biz.pdxtech.baap.util.encrypt.EncryptUtil;
import biz.pdxtech.baap.util.json.BaapJSONUtil;
import biz.pdxtech.baap.util.stream.StreamFileInfo;
import biz.pdxtech.baap.util.stream.StreamInfo;
import biz.pdxtech.baap.util.stream.StreamUtil;
import biz.pdxtech.iaas.entity.Chain_Node;
import biz.pdxtech.iaas.proto.dto.ChaincodeDTO;
import biz.pdxtech.iaas.proto.StreamInfoModel;
import com.google.protobuf.ByteString;
import okhttp3.Response;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

public class DeployUtil {

    private static Logger log = LoggerFactory.getLogger(DeployUtil.class);

    public static Pair<Boolean, String> upload(ChaincodeDTO chaincodeDTO, InputStream deployIn, Chain_Node chainNode, StreamInfoModel streamInfoModel, Long chainId) {
        try {
            String jsonRpc = "";
            if (chainNode != null) {
                jsonRpc = "http://" + chainNode.getNode().getIp() + ":" + chainNode.getRpcPort();
            } else {
                return null;
            }
            //pay tx
            String payTxId = pay(chaincodeDTO.getPayTxSign(), jsonRpc);
            log.info("Upload Chaincode >> start query upload info >> chainId:{}, txid:{}, jsonRPC:{}", chainId, payTxId, jsonRpc);
            boolean payTxResult = waitUntilExecuted(payTxId, jsonRpc, chainId + "");
            if (!payTxResult) {
                log.error("pay tx : {},is failed to execute!", payTxId);
                return new ImmutablePair<>(false, String.format("pay tx : %s,is failed to execute!", payTxId));
            }
            //upload file
            Map<String, StreamFileInfo> files = new HashMap<>();
            StreamFileInfo info = new StreamFileInfo();
            info.setFileName(chaincodeDTO.getFileName());
            info.setIn(deployIn);

            info.setFileHash(chaincodeDTO.getFileHash());
            String fileId = UUID.randomUUID().toString().replaceAll("-", "");
            files.put(fileId, info);

            Map<String, ByteString> extra = new HashMap<>();
            extra.put(Constants.BAAP_STREAM_PAY_TXID, ByteString.copyFromUtf8(payTxId));
            extra.put(Constants.BAAP_ENGINE_IP, ByteString.copyFromUtf8(chainNode.getNode().getIp()));
            extra.put(Constants.BAAP_ENGINE_PORT, ByteString.copyFromUtf8(chainNode.getRpcPort() + ""));
            extra.put(Constants.BAAP_ENGINE_ID, ByteString.copyFromUtf8(chainId + ""));
            extra.put(Constants.BAAP_ENGINE_TYPE_KEY, ByteString.copyFromUtf8(Constants.BAAP_ENGINE_TYPE_ETHEREUM));

            StreamInfo streamInfo = new StreamInfo();
            streamInfo.setExtra(extra);
            streamInfo.setFiles(files);
            streamInfo.setPubKey(chaincodeDTO.getPbk());
            streamInfo.setSign(chaincodeDTO.getStreamHashSign());
            streamInfo.setStreamHash(chaincodeDTO.getStreamHash());
            streamInfo.setStreamId(chaincodeDTO.getStreamId());
//            String streamUrl = getStreamUrl();
            String streamUrl = streamInfoModel.getBaapStreamServiceHost();

            if (streamUrl.startsWith("http://")) {
                streamUrl = streamUrl.replaceAll("http://", "");
            }
            String[] splitUrl = streamUrl.split(":");
            streamInfo.setHost(splitUrl[0]);
            int port;
            if (splitUrl.length == 1) {
                port = 80;
            } else {
                port = Integer.parseInt(splitUrl[1]);
            }
            streamInfo.setPort(port);


            boolean result = deliver(streamInfo);
            if (!result) {
                return new ImmutablePair<>(false, "failed to upload file!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ImmutablePair<>(false, "error occured : " + e.getMessage());
        }
        return new ImmutablePair<>(true, "");
    }

    private static String pay(String payTxSign, String jsonRpc) throws Exception {
        log.info("exec pay tx !!!!");
        Response response = EthereumBlockChainUtil.callStack(payTxSign, jsonRpc);
        if (!response.isSuccessful()) {
            throw new BlockchainDriverException("send pay tx failed!");
        }
        String body = Objects.requireNonNull(response.body()).string();
        Map map = BaapJSONUtil.fromJson(body, Map.class);
        if (map.containsKey("error")) {
            throw new BlockchainDriverException(String.format("error of %s", map.get("error")));
        }
        return ((Map<String, String>) map).get("result").substring(2);
    }

    private static boolean deliver(StreamInfo streamInfo) throws BlockchainDriverException {
        log.info("start upload!!!");
        Pair<Boolean, String> upload = StreamUtil.upload(streamInfo);
        return upload.getLeft();
    }

    public static String deploy(String deployTxSign, String jsonRpc) throws Exception {
        log.info("exec deploy tx !!!!");
        Response response = EthereumBlockChainUtil.callStack(deployTxSign, jsonRpc);
        if (!response.isSuccessful()) {
            throw new BlockchainDriverException("send deploy tx failed!");
        }
        String body = Objects.requireNonNull(response.body()).string();
        Map map = BaapJSONUtil.fromJson(body, Map.class);
        if (map.containsKey("error")) {
            throw new BlockchainDriverException(String.format("error of %s", map.get("error")));
        }
        return ((Map<String, String>) map).get("result").substring(2);
    }

    private static boolean waitUntilExecuted(String txId, String jsonRpc, String chainId) {
        long begin = System.currentTimeMillis();
        while (System.currentTimeMillis() - begin < 500000) {
            try {
                Thread.sleep(10000);
                ArrayList<byte[]> list = new ArrayList<>();
                list.add(txId.getBytes());
//                Transaction tx = Transaction.builder().fcn("query").params(list).build();
                Invocation invocation = Invocation.builder().fcn("query").args(list).build();
                EthereumBlockchainDriver driver = BaapDriverUtil.getInstance().getDriver(jsonRpc, chainId);
                if (driver == null) {
                    return false;
                }
                Deployment deployment = Deployment.builder().owner(Constants.BAAP_PDX_OWNER).name(Constants.BAAP_PAYMENT_NAME).version(Constants.BAAP_PAYMENT_VERSION).build();
//                Chaincode paymentCc = Chaincode.builder().chain(chainId).name("baap-payment").version("v1.0").build();
                String address = EncryptUtil.keccak256ToAddress(deployment.getOwner() + ":" + deployment.getName() + ":" + deployment.getVersion());
                byte[] query = driver.query(address, invocation);
//                byte[] query = driver.query(paymentCc, tx);
                String result = new String(query);
                log.info("check result is : {}", result);
                if (!result.equals("-1")) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }
}
