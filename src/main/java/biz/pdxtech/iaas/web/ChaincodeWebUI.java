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

package biz.pdxtech.iaas.web;

import biz.pdxtech.baap.util.encrypt.BaapECKeyUtil;
import biz.pdxtech.baap.util.encrypt.EncryptUtil;
import biz.pdxtech.baap.util.file.FileHashUtil;
import biz.pdxtech.iaas.common.DeployStatus;
import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.Chain_Node;
import biz.pdxtech.iaas.entity.DeployInfo;
import biz.pdxtech.iaas.proto.resp.BaseResponse;
import biz.pdxtech.iaas.proto.dto.ChaincodeDTO;
import biz.pdxtech.iaas.proto.resp.DataResponse;
import biz.pdxtech.iaas.proto.StreamInfoModel;
import biz.pdxtech.iaas.repository.ChainRepository;
import biz.pdxtech.iaas.service.impl.ChaincodeService;
import biz.pdxtech.iaas.service.impl.ChainService;
import biz.pdxtech.iaas.util.DeployUtil;
import biz.pdxtech.iaas.util.ThreadUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cc")
public class ChaincodeWebUI {

    @Autowired
    private Environment env;
    private Client client = Client.create();
    private static ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private ChaincodeService chaincodeService;
    @Autowired
    private ChainService chainService;
    @Autowired
    private StreamInfoModel streamInfoModel;
    @Autowired
    ChainRepository chainRepository;

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    @PostMapping("/upload/{chainId}")
    public BaseResponse upload(@PathVariable("chainId") String chainId, @Validated ChaincodeDTO chaincodeDTO, BindingResult bindingResult,
                               @RequestParam("file") MultipartFile file) {
        log.debug(chaincodeDTO.toString());
        // valide params
        BaseResponse response = new BaseResponse();
        if (bindingResult.hasErrors()) {
            response.setCode(400);
            response.setMsg("params can not be empty!");
            return response;
        }

        String owner = BaapECKeyUtil.getAddressFromPubkey(chaincodeDTO.getPbk());
        String chaincodeId = String.format("%s:%s:%s", owner, chaincodeDTO.getChaincode(), chaincodeDTO.getVersion());

        response.setCode(0);
        response.setMsg("success");
        chaincodeDTO.setFileName(file.getOriginalFilename());
        List<String> hashes = new ArrayList<>();
        InputStream inputStream;
        try {
            inputStream = file.getInputStream();
            log.info("upload file size : {} \n", inputStream.available());
        } catch (IOException e) {
            e.printStackTrace();
            response.setCode(500);
            response.setMsg(e.getMessage());
            return response;
        }
        Pair<String, InputStream> streamPair = FileHashUtil.sha256WithIOReset(inputStream);
        hashes.add(streamPair.getLeft());
        chaincodeDTO.setFileHash(streamPair.getLeft());
        chaincodeDTO.setStreamHash(FileHashUtil.sha256(hashes));
        //save to DB
        DeployInfo deployInfo = new DeployInfo();
        deployInfo.setPbk(chaincodeDTO.getPbk());
        deployInfo.setFileHash(chaincodeDTO.getStreamHash());
        deployInfo.setFileId(chaincodeDTO.getStreamId());
        deployInfo.setChaincodeId(chaincodeId);
        deployInfo.setChaincodeName(chaincodeDTO.getChaincode());
        deployInfo.setChaincodeAddress(EncryptUtil.keccak256ToAddress(chaincodeId));
        deployInfo.setChannel(chaincodeDTO.getChannel());
        deployInfo.setAlias(chaincodeDTO.getAlias());
        deployInfo.setDesc(chaincodeDTO.getDesc());
        deployInfo.setFileName(chaincodeDTO.getFileName());
        deployInfo.setStatus(DeployStatus.STREAMHANDLING.getValue());
        DeployInfo save = chaincodeService.save(deployInfo);

        //deploy
        ThreadUtil.execute(() -> {
            int status = 0;
            String reason = "";
            long deploytime = System.currentTimeMillis();
            try {
                Chain_Node chainNode = chainService.getActiveChainNodeByChainId(Long.parseLong(chainId));
                log.info("Upload Chaincode Start >> chainId:{}, nodeIP:{}, chaincodeDTO:{}", chainNode.getChain().getChainId(), chainNode.getNode().getIp(), chaincodeDTO);
                Pair<Boolean, String> pair = DeployUtil.upload(chaincodeDTO, streamPair.getRight(), chainNode, streamInfoModel, Long.parseLong(chainId));
                if (pair.getLeft()) {
                    status = DeployStatus.DEPLOYABLE.getValue();
                } else {
                    status = DeployStatus.ERROR.getValue();
                    reason = pair.getRight();
                }
                log.info("Upload Chaincode End >> chainId:{}, nodeIP:{}, status:{}, reason:{}, chaincodeDTO:{}", chainNode.getChain().getChainId(), chainNode.getNode().getIp(), status, reason, chaincodeDTO);
            } catch (Exception e) {
                reason = e.getMessage();
                status = DeployStatus.ERROR.getValue();
                log.error("Upload Chaincode Error >> chaincodeDTO:{}, error:{}", chaincodeDTO, e);
            }
            chaincodeService.update(status, deploytime, reason, save.getId());
            log.info("Update chaincode status >> chaincodeId:{}, stats:{}, deploytime:{}, reason:{}", save.getId(), status, deploytime, reason);
        });
        return response;
    }


    @PostMapping("/deploy/{chainId}")
    public BaseResponse deploy(@PathVariable("chainId") String chainId, @RequestParam("deployTxSign") String deployTxSign) {
        BaseResponse response = new BaseResponse();
        try {
            String jsonRpc = chainService.getRpcHostByChainId(Long.parseLong(chainId));
            String txId = DeployUtil.deploy(deployTxSign, jsonRpc);
            response.setCode(0);
            response.setMsg(txId);
        } catch (Exception e) {
            e.printStackTrace();
            response.setCode(500);
            response.setMsg(e.getMessage());
        }
        return response;
    }

    @GetMapping("/my/{pbk}/{chainId}")
    public DataResponse myccByPbkAndChainId(@PathVariable("pbk") String pbk, @PathVariable("chainId") String chainId) throws IOException {
        List<DeployInfo> tmpList = chaincodeService.findAllByPbkAndChannel(pbk, chainId);
        List<DeployInfo> data = getDeployInfo(tmpList);
        DataResponse response = new DataResponse();
        response.setCode(0);
        response.setMsg("success");
        response.setData(data);
        return response;
    }

    @GetMapping("/my/{pbk}")
    public DataResponse myccByPbk(@PathVariable("pbk") String pbk) throws IOException {
        List<DeployInfo> tmpList = chaincodeService.findAllByPbk(pbk);
        List<DeployInfo> data = getDeployInfo(tmpList);
        DataResponse response = new DataResponse();
        response.setCode(0);
        response.setMsg("success");
        response.setData(getDeployInfosMap(data));
        return response;
    }

    private List<Map<String, Object>> getDeployInfosMap(List<DeployInfo> data) {
        return data.stream().map(deployInfo -> getDeployInfoMap(deployInfo)).collect(Collectors.toList());
    }

    public Map<String, Object> getDeployInfoMap(DeployInfo deployInfo) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("id", deployInfo.getId());
        map.put("fileId", deployInfo.getFileId());
        map.put("fileName", deployInfo.getFileName());
        map.put("fileHash", deployInfo.getFileHash());
        map.put("channel", deployInfo.getChannel());
        map.put("chaincodeName", deployInfo.getChaincodeName());
        map.put("chaincodeId", deployInfo.getChaincodeId());
        map.put("alias", deployInfo.getAlias());
        map.put("desc", deployInfo.getDesc());
        map.put("pbk", deployInfo.getPbk());
        map.put("reason", deployInfo.getReason());
        map.put("deployTime", deployInfo.getDeployTime());
        map.put("createdAt", deployInfo.getCreatedAt());
        map.put("updatedAt", deployInfo.getUpdatedAt());
        map.put("removedAt", deployInfo.getRemovedAt());
        map.put("chaincodeAddress", deployInfo.getChaincodeAddress());
        map.put("status", deployInfo.getStatus());
        Chain chain = chainRepository.findChainByChainId(Long.parseLong(deployInfo.getChannel()));
        map.put("chainName", chain.getName());
        return map;

    }

    @GetMapping("/streamInfo")
    public DataResponse getSteamInfo() {
        DataResponse response = new DataResponse();
        try {
            String streamAccount = streamInfoModel.getBaapStreamAccount();
            if (StringUtils.isEmpty(streamAccount)) {
                response.setCode(500);
                response.setMsg("can not get stream account!");
                return response;
            }
            long streamPrice = streamInfoModel.getBaapStreamPrice();
            if (streamPrice < 1) {
                response.setCode(500);
                response.setMsg("can not get stream price!");
                return response;
            }
            response.setCode(0);
            response.setMsg("");
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("transferAccount", streamAccount);
            data.put("price", streamPrice);
            response.setData(data);
        } catch (Exception e) {
            e.printStackTrace();
            response.setCode(500);
            response.setMsg(e.getMessage());
            response.setData(null);
        }
        return response;
    }

    @GetMapping("/streamInfoDetail")
    public DataResponse getSteamInfoDetail() {
        DataResponse response = new DataResponse();
        try {
            response.setCode(0);
            response.setMsg("");
            response.setData(streamInfoModel);
        } catch (Exception e) {
            e.printStackTrace();
            response.setCode(500);
            response.setMsg(e.getMessage());
            response.setData(null);
        }
        return response;
    }

    public List<DeployInfo> getDeployInfo(List<DeployInfo> tmpList) {
        List<DeployInfo> data = new ArrayList<DeployInfo>(tmpList.size());
        if (tmpList != null) {
            for (DeployInfo tmp : tmpList) {
                if (tmp.getNodes() != null && tmp.getNodes().size() > 0) {
                    tmp.setStatus(tmp.getNodes().get(0).getStatus());
                    data.add(tmp);
                } else {
                    data.add(tmp);
                }
            }
        }
        return data;
    }

}