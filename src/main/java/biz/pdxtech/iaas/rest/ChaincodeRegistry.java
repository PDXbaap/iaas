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

package biz.pdxtech.iaas.rest;

import biz.pdxtech.baap.util.encrypt.EncryptUtil;
import biz.pdxtech.iaas.entity.DeployInfo;
import biz.pdxtech.iaas.proto.dto.DeployInfoDTO;
import biz.pdxtech.iaas.proto.vo.DeployDeleteVO;
import biz.pdxtech.iaas.proto.vo.DeployQueryVO;
import biz.pdxtech.iaas.proto.vo.DeployUpdateVO;
import biz.pdxtech.iaas.proto.resp.Result;
import biz.pdxtech.iaas.service.impl.ChaincodeService;
import biz.pdxtech.iaas.util.HttpUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Date;
import java.util.List;


@Slf4j
@Component
@Path("/deploy")
public class ChaincodeRegistry {

    @Autowired
    ChaincodeService chaincodeService;


    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(DeployInfo deployInfo, @Context HttpServletRequest request) {

        String ip = HttpUtil.getIpByRequest(request);
        log.info("Create ChainCode Start >> ChainId:{}, IP:{}, DeployInfo:{} \n", deployInfo.getChannel(), ip, deployInfo);

        Result result;
        try {
            String chaincodeAddress = EncryptUtil.keccak256ToAddress(deployInfo.getChaincodeId());

            deployInfo.setCreatedAt(new Date());
            deployInfo.setChaincodeAddress(chaincodeAddress);

            chaincodeService.processCreate(ip, deployInfo);
            result = Result.builder().status(200).data("done").build();
        } catch (Exception e) {
            log.error("Error >> Create ChainCode , error:{}", e);
            result = Result.builder().status(500).data("fail").build();
        }
        return Response.status(200).entity(result).build();
    }


    @POST
    @Path("/queryDeployByChainCodeIdAndChannel")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response findByChaincodeIdAndChannel(DeployQueryVO model, @Context HttpServletRequest request) {

        String ip = HttpUtil.getIpByRequest(request);
        log.info("Query Chaincode >> chainId:{}, ip:{}, chaincodeId:{}", model.getChannel(), ip, model.getChainCodeId());

        Result result;
        try {
            DeployInfo deployInfo = chaincodeService.findByChaincodeIdAndChannel(model.getChainCodeId(), model.getChannel(), ip);
            result = Result.builder().status(200).data(deployInfo).build();
        } catch (Exception e) {
            log.error("Error >> Query ChainCode , error:{} ", e);
            result = Result.builder().status(500).data("fail").build();
        }
        return Response.status(200).entity(result).build();
    }


    @POST
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(DeployUpdateVO model, @Context HttpServletRequest request) {
        String ip = HttpUtil.getIpByRequest(request);
        log.info("Update Chaincode Node >> chainId:{}, Ip:{}, chaincodeAddress:{}, status:{} \n", model.getChannel(), ip, model.getChaincodeAddress(), model.getStatus());

        Result result;
        try {
            chaincodeService.updateDeployNodeStatus(model.getChaincodeAddress(), model.getChannel(), model.getStatus(), ip);
            result = Result.builder().status(200).data("done").build();
        } catch (Exception e) {
            log.error("Error >> Update Chaincode Node >> error:{}", e);
            result = Result.builder().status(500).data("fail").build();
        }
        return Response.status(200).entity(result).build();
    }


    @POST
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(DeployDeleteVO model, @Context HttpServletRequest request) {

        String ip = HttpUtil.getIpByRequest(request);
        log.info("Delete ChainCode >> ChainId:{}, IP:{}, ChainCodeAddress:{} \n", model.getChannel(), ip, model.getChaincodeAddress());

        Result result;
        try {
            chaincodeService.deleteDeployNode(model.getChaincodeAddress(), model.getChannel(), ip);
            result = Result.builder().status(200).data("done").build();
        } catch (Exception e) {
            log.error("Error >> Delete ChainCode >> error:{} ", e);
            result = Result.builder().status(500).data("fail").build();
        }
        return Response.status(200).entity(result).build();
    }


    @POST
    @Path("/undownload")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response undownload(@RequestParam("chainId") String chainId, @Context HttpServletRequest request) throws IOException {

        String ip = HttpUtil.getIpByRequest(request);
        JsonObject jsonObject = new JsonParser().parse(chainId).getAsJsonObject();
        String id = jsonObject.get("chainId").getAsString();
        log.info("UnDownload Chaincode >> chainId:{}, ip:{} \n", chainId, ip);

        Result result;
        try {
            List<DeployInfoDTO> deployInfoDTOList = chaincodeService.queryUndownloadChaincode(ip, id);
            result = Result.builder().status(200).data(deployInfoDTOList).build();
        } catch (Exception e) {
            log.error("Error >> UnDownload ChainCode >> error:{} ", e);
            result = Result.builder().status(500).data("fail").build();
        }
        return Response.status(200).entity(result).build();
    }


}
