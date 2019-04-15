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


import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.proto.vo.NodeRegisterVO;
import biz.pdxtech.iaas.proto.StreamInfoModel;
import biz.pdxtech.iaas.repository.ChainRepository;
import biz.pdxtech.iaas.repository.NodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Component
@Path("/wallet")
public class WalletRegistry {
    private static final Logger log = LoggerFactory.getLogger(NodeRepository.class);

    @Autowired
    private ChainRepository chainRepository;


    @POST
    @Path("/getStreamInfo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStreamInfo(NodeRegisterVO model, @Context HttpServletRequest request) {

        StreamInfoModel streamInfoModel = StreamInfoModel.builder().build();

        return Response.status(200).entity(streamInfoModel).build();

    }

    @POST
    @Path("/getChainList")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getChainList(NodeRegisterVO model, @Context HttpServletRequest request) {

        Iterable<Chain> chain = chainRepository.findAll();

        return Response.status(200).entity(chain).build();
    }


}
