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

import biz.pdxtech.iaas.proto.resp.Result;
import biz.pdxtech.iaas.proto.vo.NodeRegisterVO;
import biz.pdxtech.iaas.service.impl.NodeService;
import biz.pdxtech.iaas.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;

@Slf4j
@Component
@Path("/node")
public class NodeRegistry {

    @Autowired
    private NodeService nodeService;


    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(NodeRegisterVO vo, @Context HttpServletRequest request) {
        String ip = HttpUtil.getIpByRequest(request);
        log.info("Node Register Request >> nodeKey:{}, minerKey:{}, NodeIP:{}, Address:{}, rpcPort:{}, enode:{} \n", vo.getNodeKey(), vo.getMinerKey(), ip, vo.getAddress(), vo.getRpcPort(), vo.getEnode());
        HashMap<String, String> meta = nodeService.register(vo, ip);
        Result result = Result.builder().status(200).meta(meta).data("done").build();
        return Response.status(200).entity(result).build();
    }


    long i = 1;

    @GET
    @Path("/test")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response test() {
        String ip = String.valueOf(i);
        i++;
        NodeRegisterVO vo = NodeRegisterVO.builder().enode(ip).minerKey(ip).nodeKey(ip).p2pPort((int)i).rpcPort((int)i).address(ip).build();
        log.info("Node Register Request >> nodeKey:{}, minerKey:{}, NodeIP:{}, Address:{}, rpcPort:{}, enode:{} \n", vo.getNodeKey(), vo.getMinerKey(), ip, vo.getAddress(), vo.getRpcPort(), vo.getEnode());
        HashMap<String, String> meta = nodeService.register(vo, ip);
        Result result = Result.builder().status(200).meta(meta).data("done").build();
        return Response.status(200).entity(result).build();
    }


    @GET
    @Path("/ip")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String ip(@Context HttpServletRequest request) {
        return HttpUtil.getIpByRequest(request);
    }


    @DELETE
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpServletRequest request) {

        String ip = HttpUtil.getIpByRequest(request);
        log.info("Node Delete Request >> NodeIP:{} \n", ip);
        nodeService.deleteNode(ip);
        Result result = Result.builder().status(200).data("done").build();
        return Response.status(200).entity(result).build();

    }


}
