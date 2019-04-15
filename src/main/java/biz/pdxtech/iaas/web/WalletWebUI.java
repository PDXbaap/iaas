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

import biz.pdxtech.iaas.entity.*;
import biz.pdxtech.iaas.proto.vo.ChainsVO;
import biz.pdxtech.iaas.proto.resp.Result;
import biz.pdxtech.iaas.repository.AddressAssetRepository;
import biz.pdxtech.iaas.repository.AssetRepository;
import biz.pdxtech.iaas.repository.ChainRepository;
import biz.pdxtech.iaas.repository.TransforAddressRecordRepository;
import biz.pdxtech.iaas.service.impl.ChainService;
import biz.pdxtech.iaas.util.EthJsonRpc;
import biz.pdxtech.iaas.util.HttpUtil;
import biz.pdxtech.iaas.util.RadixUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.core.MediaType;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.*;

@Slf4j
@RestController
@RequestMapping(value = "/webui/wallet")
public class WalletWebUI {


    private final ChainRepository chainRepository;
    private final ChainService chainService;
    private final TransforAddressRecordRepository transforAddressRecordRepository;
    private final AddressAssetRepository addressAssetRepository;
    private final AssetRepository assetRepository;

    @Autowired
    public WalletWebUI(ChainRepository chainRepository, ChainService chainService, TransforAddressRecordRepository transforAddressRecordRepository
            , AddressAssetRepository addressAssetRepository, AssetRepository assetRepository) {
        this.chainRepository = chainRepository;
        this.chainService = chainService;
        this.transforAddressRecordRepository = transforAddressRecordRepository;
        this.addressAssetRepository = addressAssetRepository;
        this.assetRepository = assetRepository;
    }

    private Client client = Client.create();
    private static ObjectMapper mapper = new ObjectMapper();
    private static final String INTERNAL_ERROR = "internal error!";
    private EthJsonRpc ethJsonRpc;

    @Value("${imgsuffix}")
    private String imgsuffix;


    @PostMapping(value = "/chains")
    public Result chains() {

        List<Chain> chains = chainRepository.findChainsByTypeIsNotAndStatIs (Chain.Type.TRUST_CHAIN, Chain.Stat.IN_SERVICE);
        List<ChainsVO> chainsVOList = new ArrayList<>();
        for (Chain chain : chains) {
            ChainsVO vo = ChainsVO.builder().chainId(chain.getChainId().toString()).chainName(chain.getName()).chainOwner(chain.getOwner().getAddr()).chainImg (imgsuffix+chain.getImgUrl ()).build();
            chainsVOList.add(vo);
        }
        return Result.builder().status(200).data(chainsVOList).build();
    }

    @PostMapping(value = "/transforchains/{chainId}")
    public Result transforchains(@PathVariable("chainId") Long chainId) {

        List<Chain> chains = chainRepository.findChainsByTypeIsNotAndStatIsAndShareChainId(Chain.Type.TRUST_CHAIN, Chain.Stat.IN_SERVICE, chainId);

        List<ChainsVO> chainsVOList = new ArrayList<>();
        for (Chain chain : chains) {
            ChainsVO vo = ChainsVO.builder().chainId(chain.getChainId().toString()).chainName(chain.getName()).chainOwner(chain.getOwner().getAddr()).build();
            chainsVOList.add(vo);
        }
        return Result.builder().status(200).data(chainsVOList).build();
    }


    @PostMapping(value = "/proxy/{chainId}")
    public Result proxy(@RequestBody String param, @PathVariable("chainId") Long chainId) {
        try {
            param = URLDecoder.decode(param, "utf-8");
            String jsonRpc = chainService.getRpcHostByChainId(chainId);
            if (jsonRpc==null){
                return Result.builder().status(500).data("链id不存在").build();
            }
            WebResource.Builder builder = client.resource(jsonRpc).type(MediaType.APPLICATION_JSON);
            ClientResponse clientResponse = builder.entity(param).post(ClientResponse.class);
            String result = clientResponse.getEntity(String.class);
            JsonNode json = mapper.readValue(result, JsonNode.class);
            JsonNode resultStr = json.get("result");
            if (resultStr != null) {
                return Result.builder().status(200).data(resultStr).build();
            } else {
                return Result.builder().status(500).data(result).build();
            }
        } catch (Exception e) {
            log.info("{}", e);
            return Result.builder().status(500).data(INTERNAL_ERROR).build();
        }
    }


    @PostMapping(value = "/address/add")
    @Transactional
    public Result addAddress(@RequestParam String address, @RequestParam String transforAddress, @RequestParam Long chainId, @RequestParam(required = false) String note) {
        TransforAddressRecord transforAddressRecord = new TransforAddressRecord();
        transforAddressRecord.setAddress(address);
        transforAddressRecord.setTransforAddress(transforAddress);
        transforAddressRecord.setNote(note);
        transforAddressRecordRepository.save(transforAddressRecord);
        return Result.builder().status(200).data("").build();
    }


    @PostMapping(value = "/address/update")
    public Result updateAddress(@RequestParam Long id, @RequestParam String transforAddress, @RequestParam(required = false) String note) {
        TransforAddressRecord transforAddressRecord = transforAddressRecordRepository.findById(id).orElse(null);
        if (transforAddressRecord == null) {
            return Result.builder().status(500).data(INTERNAL_ERROR).build();
        }
        transforAddressRecord.setTransforAddress(transforAddress);
        transforAddressRecord.setNote(note);
        transforAddressRecordRepository.save(transforAddressRecord);
        return Result.builder().status(200).data("").build();
    }


    @PostMapping(value = "/address/delete")
    public Result deleteAddress(@RequestParam Long id) {
        transforAddressRecordRepository.deleteById(id);
        return Result.builder().status(200).data("").build();
    }


    @PostMapping(value = "/address/find")
    public Result findAddress(@RequestParam String address, @RequestParam Long chainId, @RequestParam int pageNo, @RequestParam int pageSize) {
        Page<TransforAddressRecord> addresses = transforAddressRecordRepository.findAllByAddress(address, PageRequest.of(pageNo - 1, pageSize));
        Map<String, Object> data = new HashMap<>(16);
        data.put("totalNum", addresses.getTotalElements());
        data.put("addresses", addresses.getContent());
        return Result.builder().status(200).data(data).build();
    }


    @PostMapping(value = "/address/findByAddress")
    public Result findAddressByAddress(@RequestParam String address, @RequestParam int pageNo, @RequestParam int pageSize) {
        Page<TransforAddressRecord> addresses = transforAddressRecordRepository.findAllByAddress(address, PageRequest.of(pageNo - 1, pageSize));
        Map<String, Object> data = new HashMap<>(16);
        data.put("totalNum", addresses.getTotalElements());
        data.put("addresses", addresses.getContent());
        return Result.builder().status(200).data(data).build();
    }

  /*  @PostMapping(value = "/asset/find")
    public Result findAsset(@RequestParam String address) {
        List<Asset> list = assetRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Asset asset : list) {
            Address_Asset address_asset = addressAssetRepository.findAddress_AssetByAddressAndAssetId(address, asset.getId());
            Map<String, Object> map = getAssetMap(asset);
            if (address_asset == null
                    || address_asset.getStatus() == 0) {
                map.put("status", 0);
            } else {
                map.put("status", 1);
            }
            result.add(map);
        }
        return Result.builder().status(200).data(result).build();
    }
*/


    @PostMapping(value = "/asset/update")
    public Result updateAsset(@RequestParam String address, @RequestParam Long assetId, @RequestParam int status) {
        Address_Asset address_asset = addressAssetRepository.findAddress_AssetByAddressAndAssetId(address, assetId);
        if (status == 0) {
            if (address_asset != null) {
                address_asset.setStatus(0);
                addressAssetRepository.save(address_asset);
            }
        } else {
            if (address_asset != null) {
                address_asset.setStatus(1);
                addressAssetRepository.save(address_asset);
            } else {
                address_asset = Address_Asset.builder().address(address).
                        assetId(assetId).quantity(0.0).status(1).build();
                addressAssetRepository.save(address_asset);
            }
        }

        return Result.builder().status(200).data("").build();
    }


    @PostMapping(value = "/addressAsset/find")
    public Result updateAsset(@RequestParam String address) {
        List<Address_Asset> list = addressAssetRepository.findAddress_AssetByAddressAndStatus(address, 1);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Address_Asset address_asset : list) {
            Map<String, Object> map = getAddressAssetMap(address_asset);
            result.add(map);
        }
        return Result.builder().status(200).data(result).build();
    }

    private Map<String, Object> getAddressAssetMap(Address_Asset address_Asset) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("id", address_Asset.getId());
        map.put("quantity", address_Asset.getQuantity());

        Long assetId = address_Asset.getAssetId();
        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null) {
            throw new NullPointerException();
        }
        map.put("rmbPrice", address_Asset.getQuantity() * getrmbPrice(asset.getName()));
        map.put("imgUrl", asset.getImgUrl());
        map.put("name", asset.getName());
        map.put("note", asset.getNote());
        map.put("contractAddress", asset.getContractAddress());
        return map;
    }

    private Double getrmbPrice(String assetName) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("market", assetName + "_qc");
        String last = null;
        try {
            String assetTypeInfo = HttpUtil.sendPost("http://api.zb.cn/data/v1/ticker", map, "utf-8");
            JsonNode json = mapper.readValue(assetTypeInfo, JsonNode.class);
            JsonNode ticker = json.get("ticker");
            last = ticker.get("last").textValue();
        } catch (Exception e) {
            log.error("{}", e);
        }
        if (last == null) {
            throw new NullPointerException();
        }
        return Double.valueOf(last);
    }


    @PostMapping(value = "/asset/find")
    public Result assetFind(@RequestParam String address,@RequestParam Long chainId) {
        List<Asset> list = assetRepository.findByChainId(chainId);
        if(list==null||list.size ()==0){
            return Result.builder().status(500).data("链id不存在").build();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Asset asset : list) {
            Map<String, Object> map = getAssetMap(asset);
            map.put ("balance",getBalance(address,chainId));
            map.put ("address",address);
            result.add(map);
        }
        return Result.builder().status(200).data(result).build();
    }
    private Map<String, Object> getAssetMap(Asset asset) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("id", asset.getId());
        map.put("imgUrl", imgsuffix+asset.getImgUrl());
        map.put("name", asset.getName());
        map.put("fullName", asset.getFullName ());
        map.put("note", asset.getNote());
        map.put("contractAddress", asset.getContractAddress());
        return map;
    }
    private String getBalance(String address,Long chainId){
        String rpcAddress = chainService.getRpcHostByChainId (chainId);
        String balance="";
        try {
            ethJsonRpc = new EthJsonRpc(rpcAddress);
            balance= ethJsonRpc.getBalance (address);
            System.out.println(RadixUtil.hexToBigDecimal(balance).toString());
        } catch (MalformedURLException e) {
            log.error("Error >> EthJsonRpc >> error:{} \n", e);
        }
        return  balance;
    }
}

