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

import java.net.URLDecoder;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.MediaType;

import biz.pdxtech.baap.util.file.DeployInfo;
import biz.pdxtech.baap.util.json.BaapJSONUtil;
import biz.pdxtech.iaas.proto.resp.DataResponse;
import biz.pdxtech.iaas.service.impl.ChainService;
import com.google.gson.reflect.TypeToken;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource.Builder;

@RestController
public class ProxyWebUI {

    private ObjectMapper mapper = new ObjectMapper();
    private Client client = Client.create();
    private String jsonRpc = "";

    @Autowired
    private ChainService chainService;
    @Autowired
    private Environment env;

    @PostConstruct
    public void init() {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private String getJsonRpc(Long chainId) {
        jsonRpc = chainService.getRpcHostByChainId(chainId);
        return jsonRpc;
    }

/*
    @PostMapping(value = "/proxy/{chainId}")
    public String proxy(@PathVariable("chainId") String chainId, @RequestBody String param) throws JsonProcessingException {
        DataResponse ret = new DataResponse();
        try {
            param = URLDecoder.decode(param, "utf-8");
            String jsonRpc = getJsonRpc(Long.parseLong(chainId));
            Builder builder = client.resource(jsonRpc).type(MediaType.APPLICATION_JSON);
            ClientResponse clientResponse = builder.entity(param).post(ClientResponse.class);
            String result = clientResponse.getEntity(String.class);
            JsonNode json = mapper.readValue(result, JsonNode.class);
            JsonNode resultStr = json.get("result");
            if (resultStr != null) {
                ret.setCode(0);
                ret.setData(resultStr);
                ret.setMsg("");
            } else {
                JsonNode error = json.get("error");
                ret.setCode(error.get("code").asInt());
                ret.setData("");
                ret.setMsg(error.get("message").asText());
            }
        } catch (Exception e) {
            e.printStackTrace();
            ret.setCode(-999);
            ret.setData("");
            ret.setMsg("internal error!");
        }
        return mapper.writeValueAsString(ret);
    }
*/

    @PostMapping(value = "/proxyWithDecode/{chainId}")
    public String proxyWithDecode(@PathVariable("chainId") String chainId, @RequestBody String param) throws JsonProcessingException {
        DataResponse ret = new DataResponse();
        try {
            param = URLDecoder.decode(param, "utf-8");
            Builder builder = client.resource(getJsonRpc(Long.parseLong(chainId))).type(MediaType.APPLICATION_JSON);
            ClientResponse clientResponse = builder.entity(param).post(ClientResponse.class);
            String result = clientResponse.getEntity(String.class);
            JsonNode json = mapper.readValue(result, JsonNode.class);
            JsonNode resultStr = json.get("result");
            if (resultStr != null) {
                ret.setCode(0);
                String text = resultStr.asText().substring(2);
                String jsonStr = new String(Hex.decode(text));
                List<DeployInfo> list = BaapJSONUtil.fromJson(jsonStr, new TypeToken<List<DeployInfo>>() {
                }.getType());
                ret.setData(list);
                ret.setMsg("");
            } else {
                JsonNode error = json.get("error");
                ret.setCode(error.get("code").asInt());
                ret.setData("");
                ret.setMsg(error.get("message").asText());
            }
        } catch (Exception e) {
            e.printStackTrace();
            ret.setCode(-999);
            ret.setData("");
            ret.setMsg("internal error!");
        }
        return mapper.writeValueAsString(ret);
    }
}
