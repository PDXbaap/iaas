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

import biz.pdxtech.iaas.common.exception.NodeServiceException;
import biz.pdxtech.iaas.entity.Node;
import biz.pdxtech.iaas.proto.resp.Result;
import biz.pdxtech.iaas.repository.NodeRepository;
import biz.pdxtech.iaas.service.impl.NodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.ws.rs.core.Response;
import java.util.Objects;

@Slf4j
@Controller
@RequestMapping(value = "/webui/node")
public class NodeWebUI {

    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private NodeService nodeService;


    @GetMapping("/listAll")
    public String listAll() {
        return "node_list_all";
    }

    @GetMapping("/create")
    public String create(@RequestParam(name = "node", required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute("name", name);
        return "node_register";
    }

    @GetMapping("/retrieve")
    public String retrieve() {
        return "node_query";
    }

    @GetMapping("/delete")
    public Response delete(@RequestParam(name = "nodeId") Long nodeId) {

        Node node = nodeRepository.findNodeById(nodeId);

        if (Objects.isNull(node)) {
            log.warn("Node Not Found!");
            throw new NodeServiceException("Delete Node >> Node Not Found!");
        }

        nodeService.deleteNode(node);

        Result result = Result.builder().status(20020).data("done").build();
        return Response.status(200).entity(result).build();

    }
}
