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

import biz.pdxtech.iaas.Application;
import biz.pdxtech.iaas.cluster.Partitioner;
import biz.pdxtech.iaas.entity.*;
import biz.pdxtech.iaas.proto.vo.ChainsVO;
import biz.pdxtech.iaas.proto.resp.RespCode;
import biz.pdxtech.iaas.proto.resp.Result;
import biz.pdxtech.iaas.repository.*;
import biz.pdxtech.iaas.proto.resp.RespEntity;
import biz.pdxtech.iaas.rest.ChainRegistry;
import biz.pdxtech.iaas.rest.UserRegistry;
import biz.pdxtech.iaas.service.impl.ChainService;
import biz.pdxtech.iaas.util.DataUtils;
import biz.pdxtech.iaas.util.FileUtil;
import biz.pdxtech.iaas.util.SnowflakeIdUtil;
import biz.pdxtech.iaas.util.UUIDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;


@RestController
@RequestMapping(value = "/webui/chain")
public class ChainWebUI {

    private static final Logger log = LoggerFactory.getLogger(ChainWebUI.class);

    @Autowired
    ChainService chainService;
    @Autowired
    ChainRepository chainRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserRegistry userRegistry;
    @Autowired
    ChainRegistry chainRegistry;
    @Autowired
    ChainCreationRepository chainCreationRepository;
    @Autowired
    ChainDeletionRepository chainDeletionRepository;
    @Autowired
    AddressRepository addressRepository;
    @Autowired
    ChainOrderRepository chainOrderRepository;
    @Autowired
    ChainNodeRepository chainNodeRepository;
    @Autowired
    UserAddressRepository userAddressRepository;
    @Autowired
    AssetRepository assetRepository;
    @Autowired
    NodeRepository nodeRepository;
    @Value("${chainImgPath}")
    private String chainImgPath;
    @Value("${tokenImgPath}")
    private String tokenImgPath;
    @Value("${imgsuffix}")
    private String imgsuffix;
    @Value("${pdx.iaas.basic-service-chain.name}")
    private String defaultChainName;

    @ResponseBody
    @RequestMapping("/getChainList")
    public RespEntity getChainList(@RequestParam(name = "token") String token,
                                   @RequestParam(name = "status") int status,
                                   @RequestParam(name = "pageNo") Integer pageNo,
                                   @RequestParam(name = "pageSize") Integer pageSize) {

        Map<String, Object> result = new HashMap<>();

        User user = userRegistry.getTokenUser(token);

        if (user == null) {
            return RespEntity.error(RespCode.TOKEN_IS_ERROR); // token验证失败
        }

        Page<ChainOrder> page;
        if (status == -1) {
            page = chainOrderRepository.findByUserOrderByCreateTimeDesc(user, new PageRequest(pageNo - 1, pageSize));
        } else {
            page = chainOrderRepository.findByChain_StatAndUserOrderByCreateTimeDesc(Chain.Stat.values()[status], user, new PageRequest(pageNo - 1, pageSize));
        }

        List<ChainOrder> content = page.getContent();

        result.put("totalNum", page.getTotalElements());

        List<Map<String, Object>> list = new ArrayList<>();

        for (ChainOrder chainOrder : content) {
            Map<String, Object> map = new HashMap<>();
            map.put("orderId", chainOrder.getUuid());
            map.put("chainName", chainOrder.getChain().getName());
            map.put("stack", chainOrder.getChain().getStack().ordinal());
            map.put("state", chainOrder.getChain().getStat().ordinal());
            map.put("nodeNum", chainOrder.getChain().getSizeDesired());
            map.put("type", chainOrder.getType().ordinal());
            map.put("chainImg", imgsuffix + chainOrder.getChain().getImgUrl());
            map.put("shareChainId", chainOrder.getChain().getShareChainId());
            map.put("createTime", DataUtils.formatData(chainOrder.getCreateTime()));
            map.put("deadline", chainOrder.getChain().getDeadline());
            List<Asset> assetList = assetRepository.findByChainId(chainOrder.getChain().getChainId());
            if (assetList != null && assetList.size() > 0) {
                Asset asset = assetList.get(0);
                map.put("tokenName", asset.getName());
                map.put("tokenFullName", asset.getFullName());
                map.put("tokenImg", imgsuffix + asset.getImgUrl());
                map.put("tokenDesc", asset.getNote());
            }
            List<Chain_Node> nodeList = chainNodeRepository.findChain_NodesByChainAndStatIs(chainOrder.getChain(), Chain_Node.Stat.IN_SERVICE);

            List<Map<String, Object>> nodelist = new ArrayList<>();
            nodeList.forEach(chain_node -> {
                Map<String, Object> node = new HashMap<>();
                node.put("nodeAdr", chain_node.getNode().getAddr().getAddr());
                node.put("nodeIp", chain_node.getNode().getIp());
                nodelist.add(node);
            });
            map.put("nodeList", nodelist);

            list.add(map);
        }

        result.put("list", list);

        return RespEntity.success(result);
    }


    @PostMapping(value = "/createForTest")
    public String createTest(@RequestParam(name = "chainName") String chainName,
                             @RequestParam(name = "nodeNum") int nodeNum,
                             @RequestParam(name = "monthNum") int monthNum,
//                             @RequestParam(name = "shareChain") String shareChain,
                             @RequestParam(name = "stack") int stack,
                             @RequestParam(name = "chainImg") MultipartFile chainImg,
                             @RequestParam(name = "shareChainId", required = false) Long shareChainId,
                             @RequestParam(name = "tokenImg", required = false) MultipartFile tokenImg,
                             @RequestParam(name = "tokenName", required = false) String tokenName,
                             @RequestParam(name = "tokenFullName", required = false) String tokenFullName,
                             @RequestParam(name = "tokenDesc", required = false) String tokenDesc) throws ParseException {


        Address address = addressRepository.findAddressByAddr("0xde587cff6c1137c4eb992cf8149ecff3e163ee07");
        if (address == null) {
            address = Address.builder().addr("0xde587cff6c1137c4eb992cf8149ecff3e163ee07").createdAt(new Date()).build();
            addressRepository.save(address);
        }

        //过期时间
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = dateFormat.parse("2010-01-01");

        String chainImgUrl = "";
        try {
            chainImgUrl = FileUtil.uploadFile(chainImg, chainImgPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Chain chain = Chain.builder()
                .name(chainName)
                .owner(address)
                .chainId(SnowflakeIdUtil.getID())
                .sizeService(0)
                .cfd(10)
                .blockDelay(3000)
                .stat(Chain.Stat.NOT_READY)
                .part(Partitioner.name2part(chainName))
                .sizeDesired(nodeNum)
                .type(Chain.Type.BIZ_CHAIN)
                .stack(Chain.Stack.PDX)
                .shareChainId(shareChainId)
                .deadline(date.getTime())
                .createdAt(new Date())
                .imgUrl(chainImgPath + "/" + chainImgUrl).build();
        Chain savedChain = chainRepository.save(chain);
        ChainCreation request = ChainCreation.builder().chain(savedChain).type(ChainCreation.Type.SUBMITTED).time(System.currentTimeMillis()).build();
        chainCreationRepository.save(request);
        String tokenImgUrl = "";
        try {
            tokenImgUrl = FileUtil.uploadFile(tokenImg, tokenImgPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Asset asset = Asset.builder().name(tokenName).imgUrl(tokenImgPath + "/" + tokenImgUrl).note(tokenDesc).fullName(tokenFullName).chainId(chain.getChainId()).build();
        assetRepository.save(asset);
        chainService.submit(request);
        return "chain_register";
    }


    @GetMapping(value = "/createTestBak")
    public String createTestBak(@RequestParam(name = "name") String name,
                                @RequestParam(name = "sizeDesired") int sizeDesired,
                                @RequestParam(name = "shareChainId", required = false) Long shareChainId) throws ParseException {


        Address address = addressRepository.findAddressByAddr("0xde587cff6c1137c4eb992cf8149ecff3e163ee07");
        if (address == null) {
            address = Address.builder().addr("0xde587cff6c1137c4eb992cf8149ecff3e163ee07").createdAt(new Date()).build();
            addressRepository.save(address);
        }

        //过期时间
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = dateFormat.parse("2010-01-01");

        Chain chain = Chain.builder()
                .name(name)
                .owner(address)
                .chainId(SnowflakeIdUtil.getID())
                .sizeService(0)
                .stat(Chain.Stat.NOT_READY)
                .cfd(10)
                .blockDelay(3000)
                .part(Partitioner.name2part(name))
                .sizeDesired(sizeDesired)
                .type(Chain.Type.BIZ_CHAIN)
                .stack(Chain.Stack.PDX)
                .shareChainId(shareChainId)
                .createdAt(new Date())
                .deadline(date.getTime()).build();

        Chain savedChain = chainRepository.save(chain);
        ChainCreation request = ChainCreation.builder().chain(savedChain).type(ChainCreation.Type.SUBMITTED).time(System.currentTimeMillis()).build();
        chainCreationRepository.save(request);
        chainService.submit(request);
        return "chain_register";

    }


    @ResponseBody
    @RequestMapping("/getConfigFee")
    public RespEntity renew(@RequestParam(name = "nodeNum") int nodeNum,
                            @RequestParam(name = "monthNum") int monthNum) {
        List<Node> nodes = nodeRepository.findNodesByStatIs(Node.Stat.IN_SERVICE);
        if (nodes.size() < nodeNum) {
            return RespEntity.error(RespCode.NODE_NUMBER_HIGHT);
        }
        Map<String, Long> map = new HashMap<>(16);
        map.put("configFee", 10L * Integer.valueOf(nodeNum) * Integer.valueOf(monthNum));
        return RespEntity.success(map);
    }


    @ResponseBody
    @RequestMapping("/create")
    @Transactional
    public RespEntity create(@RequestParam(name = "token") String token,
                             @RequestParam(name = "chainName") String chainName,
                             @RequestParam(name = "nodeNum") int nodeNum,
                             @RequestParam(name = "monthNum") int monthNum,
                             @RequestParam(name = "stack") int stack,
                             @RequestParam(name = "chainImg") MultipartFile chainImg,
                             @RequestParam(name = "shareChainId", required = false) Long shareChainId,
                             @RequestParam(name = "tokenImg", required = false) MultipartFile tokenImg,
                             @RequestParam(name = "tokenName", required = false) String tokenName,
                             @RequestParam(name = "tokenFullName", required = false) String tokenFullName,
                             @RequestParam(name = "tokenDesc", required = false) String tokenDesc) {

        Map<String, Object> map = new HashMap<>();

        User user = userRegistry.getTokenUser(token);

        if (user == null) {
            return RespEntity.error(RespCode.TOKEN_IS_ERROR); // token验证失败
        }

        List<User_Address> addrlist = userAddressRepository.findByUserId(user.getId());
        if (addrlist.isEmpty()) {
            return RespEntity.error(RespCode.USER_NOT_WALLET);
        }
        List<ChainOrder> co = chainOrderRepository.findByStatAndUserId(ChainOrder.Stat.DEF_PAY, user.getId());
        if (!co.isEmpty()) {
            return RespEntity.error(RespCode.DEF_PAY_ORDER);
        }
        Chain chain = chainRepository.findChainByName(chainName);

        if (Objects.nonNull(chain)) {
            return RespEntity.error(RespCode.CHAIN_NAME_ERROR);
        }

        // TODO 需要验证账户钱够不够支付创建链的金额
//        double balance = 10.12;
//        int price = 100000 * Integer.valueOf(nodeNum) * Integer.valueOf(monthNum);
//        if (balance < price){
//            return RespEntity.error(RespCode.BALANCE_IS_LESS);
//        }
        String chainImgUrl = "";
        try {
            chainImgUrl = FileUtil.uploadFile(chainImg, chainImgPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        chain = createChain(user, chainName, monthNum, nodeNum, shareChainId, stack, chainImgPath + "/" + chainImgUrl);
        log.info("chain create is success >> chain: {}", chain);


        ChainCreation request = ChainCreation.builder().chain(chain).type(ChainCreation.Type.SUBMITTED).time(System.currentTimeMillis()).build();
        chainCreationRepository.save(request);
        Asset asset;
        if (chain.getShareChainId() == null) {
            List<Asset> list = assetRepository.findByName(tokenName);
            if (!(list == null || list.size() == 0)) {
                return RespEntity.error(RespCode.ASSET_NAME_EXSIST);
            }
            asset = Asset.builder().name(tokenName).note(tokenDesc).fullName(tokenFullName).chainId(chain.getChainId()).build();
            if (tokenImg != null) {
                String tokenImgUrl = "";
                try {
                    tokenImgUrl = FileUtil.uploadFile(tokenImg, tokenImgPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                asset.setImgUrl(tokenImgPath + "/" + tokenImgUrl);
            }
        } else {
            Asset asset1 = assetRepository.findByChainId(chain.getShareChainId()).get(0);
            asset = Asset.builder().name(asset1.getName()).note(asset1.getNote()).imgUrl(asset1.getImgUrl()).fullName(asset1.getFullName()).chainId(chain.getChainId()).build();
        }

        assetRepository.save(asset);

        ChainOrder chainOrder = createOrder(user, chain, monthNum, nodeNum, ChainOrder.Type.CREATE);
        log.info("order create is success >> orderId:{}", chainOrder.getId());
        map.put("orderId", chainOrder.getUuid());
        map.put("chainName", chainName);
        map.put("stack", chain.getStack().ordinal());
        map.put("nodeNum", nodeNum);
        map.put("feeMode", monthNum);
        map.put("configFee", chainOrder.getConfFee());
        map.put("recAddr", Application.rece_Account);
        map.put("shareChain", chain.getShareChainId());
        map.put("state", chain.getStat().ordinal());

        return RespEntity.success(map);
    }

    private Long addmonth(Long oldTime, int month) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(oldTime), ZoneId.systemDefault());
        return localDateTime.plusMonths(month).toInstant(ZoneOffset.of("+8")).toEpochMilli();
    }

    private Chain createChain(User user, String chainName, int monthNum, int nodeNum, Long shareChainId, int stack, String imgUrl) {

        Chain chain = new Chain();

        chain.setChainId(SnowflakeIdUtil.getID());

        chain.setName(chainName);

        chain.setPart(Partitioner.name2part(chainName));

        chain.setStack(Chain.Stack.values()[stack]);

        chain.setDeadline(addmonth(System.currentTimeMillis(), monthNum));

        chain.setType(Chain.Type.BIZ_CHAIN);

        chain.setBcType(Chain.BCtype.PUBLIC);

        chain.setStat(Chain.Stat.NOT_READY);

        chain.setSizeDesired(nodeNum);

        chain.setCreatedAt(new Date());

        chain.setCfd(10);

        chain.setBlockDelay(3000);

        chain.setSizeService(0);

        chain.setShareChainId(shareChainId);

        chain.setImgUrl(imgUrl);

        List<User_Address> addrlist = userAddressRepository.findByUserId(user.getId());
        if (!addrlist.isEmpty()) {
            //TODO:地址应该选定
            chain.setOwner(addrlist.get(0).getAddress());
        }

        chain = chainRepository.save(chain);

        return chain;
    }

    private ChainOrder createOrder(User user, Chain chain, int monthNum, int nodeNum, ChainOrder.Type type) {

        ChainOrder chainOrder = new ChainOrder();

        String uuid = UUIDUtil.getOrderIdByUUId();

        chainOrder.setUuid(uuid);

        chainOrder.setUser(user);

        chainOrder.setFeeMode(monthNum);

        int price = 10 * Integer.valueOf(nodeNum) * Integer.valueOf(monthNum);

        chainOrder.setConfFee(price);

        chainOrder.setStat(ChainOrder.Stat.DEF_PAY);

        chainOrder.setCreateTime(new Date());

        chainOrder.setType(type);

        List<User_Address> ua = userAddressRepository.findByUserId(user.getId());

        chainOrder.setFromAddr(ua != null && ua.size() > 0 ? ua.get(0).getAddress().getAddr() : null);

        chainOrder.setToAddr(Application.rece_Account);

        chainOrder.setChain(chain);

        chainOrder = chainOrderRepository.save(chainOrder);

        return chainOrder;
    }

    @ResponseBody
    @RequestMapping("/renew")
    public RespEntity renew(@RequestParam(name = "token") String token,
                            @RequestParam(name = "orderId") String orderId,
                            @RequestParam(name = "monthNum") int monthNum) {

        Map<String, Object> map = new HashMap<>();

        User user = userRegistry.getTokenUser(token);

        if (user == null) {
            return RespEntity.error(RespCode.TOKEN_IS_ERROR); // token验证失败
        }

        ChainOrder co = chainOrderRepository.findByUuidAndUserId(orderId, user.getId());

        if (co == null) {
            return RespEntity.error(RespCode.ORDER_NOT_USER); // 订单用户不匹配
        }
        List<ChainOrder> cos = chainOrderRepository.findByStatAndUserId(ChainOrder.Stat.DEF_PAY, user.getId());
        if (!cos.isEmpty()) {
            return RespEntity.error(RespCode.DEF_PAY_ORDER);
        }
        ChainOrder chainOrder = createOrder(user, co.getChain(), monthNum, co.getChain().getSizeService(), ChainOrder.Type.RENEW);

        // TODO 需要验证账户钱够不够支付创建链的金额
//        double balance = 10.12;
//        int price = 100000 * Integer.valueOf(nodeNum) * Integer.valueOf(monthNum);
//        if (balance < price){
//            return RespEntity.error(RespCode.BALANCE_IS_LESS);
//        }

        map.put("orderId", chainOrder.getUuid());
        map.put("chainName", chainOrder.getChain().getName());
        map.put("stack", chainOrder.getChain().getStack().ordinal());
        map.put("nodeNum", chainOrder.getChain().getSizeService());
        map.put("feeMode", chainOrder.getFeeMode());
        map.put("configFee", chainOrder.getConfFee());
        map.put("recAddr", chainOrder.getToAddr());
        map.put("state", chainOrder.getChain().getStat().ordinal());

        log.info("order renew is success >> orderId:{} " , orderId);

        return RespEntity.success(map);
    }

    @ResponseBody
    @RequestMapping("/update")
    public RespEntity updateChain(@RequestParam(name = "token") String token,
                                  @RequestParam(name = "orderId") String orderId,
                                  @RequestParam(name = "chainName") String chainName,
                                  @RequestParam(name = "chainImg", required = false) MultipartFile chainImg,
                                  @RequestParam(name = "tokenImg", required = false) MultipartFile tokenImg,
                                  @RequestParam(name = "tokenName", required = false) String tokenName,
                                  @RequestParam(name = "tokenFullName", required = false) String tokenFullName,
                                  @RequestParam(name = "tokenDesc", required = false) String tokenDesc
    ) {

        User user = userRegistry.getTokenUser(token);

        if (user == null) {
            return RespEntity.error(RespCode.TOKEN_IS_ERROR); // token验证失败
        }

        ChainOrder chainOrder = chainOrderRepository.findByUuidAndUserId(orderId, user.getId());

        if (chainOrder == null) {
            return RespEntity.error(RespCode.ORDER_NOT_USER); // 订单用户不匹配
        }

        Chain chain = chainOrder.getChain();
        if (chainImg != null) {
            String chainImgUrl = "";
            try {
                chainImgUrl = FileUtil.uploadFile(chainImg, chainImgPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            chain.setImgUrl(chainImgPath + "/" + chainImgUrl);
        }

        chain.setName(chainName);
        chainOrderRepository.save(chainOrder);

        Asset asset = assetRepository.findByChainId(chain.getChainId()).get(0);
        if (chain.getShareChainId() == null) {
            if (tokenImg != null) {
                String tokenImgUrl = "";
                try {
                    tokenImgUrl = FileUtil.uploadFile(tokenImg, tokenImgPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                asset.setImgUrl(tokenImgPath + "/" + tokenImgUrl);
            }

            asset.setName(tokenName);

            asset.setNote(tokenDesc);
            asset.setFullName(tokenFullName);
            assetRepository.save(asset);
        }
        log.info("order update is success >> orderId:{}", chainOrder.getId());

        return RespEntity.success();
    }

    @ResponseBody
    @RequestMapping("/delete")
    public RespEntity deleteChain(@RequestParam(name = "token") String token,
                                  @RequestParam(name = "orderId") String orderId) {

        User user = userRegistry.getTokenUser(token);

        if (user == null) {
            return RespEntity.error(RespCode.TOKEN_IS_ERROR); // token验证失败
        }

        ChainOrder chainOrder = chainOrderRepository.findByUuidAndUserId(orderId, user.getId());

        if (chainOrder == null) {
            return RespEntity.error(RespCode.ORDER_NOT_USER); // 订单用户不匹配
        }

        Chain chain = chainOrder.getChain();
        //save request status
        ChainDeletion request = ChainDeletion.builder().chain(chain).type(ChainDeletion.Type.SUBMITTED).time(System.currentTimeMillis()).build();
        chainDeletionRepository.save(request);
        chainService.submit(request);
        chainOrder.getChain().setStat(Chain.Stat.REMOVED);
        chainRepository.save(chainOrder.getChain());

        log.info("order delete is success >> orderId:{}", chainOrder.getId());
        return RespEntity.success();
    }

    @ResponseBody
    @GetMapping("/deleteForTest")
    public Response delete2(@RequestParam(name = "chainName") String chainName) {

        Chain chain = chainRepository.findChainByNameAndStatIsNot(chainName, Chain.Stat.REMOVED);
        ChainDeletion request = ChainDeletion.builder().chain(chain).type(ChainDeletion.Type.SUBMITTED).time(System.currentTimeMillis()).build();
        chainDeletionRepository.save(request);
        chainService.submit(request);
        Result result = Result.builder().status(200).data("done").build();
        return Response.status(200).entity(result).build();
    }

    @PostMapping(value = "/asset/find")
    public Result assetFind(@RequestParam Long chainId) {
        List<Asset> list = assetRepository.findByChainId(chainId);
        if (list == null || list.size() == 0) {
            return Result.builder().status(500).data("链id不存在").build();
        }
        Map<String, Object> map = getAssetMap(list.get(0));
        return Result.builder().status(200).data(map).build();
    }

    private Map<String, Object> getAssetMap(Asset asset) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("id", asset.getId());
        map.put("imgUrl", imgsuffix + asset.getImgUrl());
        map.put("name", asset.getName());
        map.put("fullName", asset.getFullName());
        map.put("note", asset.getNote());
        map.put("contractAddress", asset.getContractAddress());
        return map;
    }


    @PostMapping(value = "/chains")
    public Result chains() {
        List<Chain> chains = chainRepository.findChainsByTypeIsNotAndStatIs(Chain.Type.TRUST_CHAIN, Chain.Stat.IN_SERVICE);
        List<ChainsVO> chainsVOList = new ArrayList<>();
        for (Chain chain : chains) {
            ChainsVO vo = ChainsVO.builder().chainId(chain.getChainId().toString()).chainName(chain.getName()).chainOwner(chain.getOwner().getAddr()).chainImg(imgsuffix + chain.getImgUrl()).build();
            chainsVOList.add(vo);
        }
        return Result.builder().status(200).data(chainsVOList).build();
    }


    @PostMapping(value = "/findDefaultChain")
    public Result findDefaultChain() {
        Chain chains = chainRepository.findChainByName(defaultChainName);
        return Result.builder().status(200).data(chains.getChainId().toString()).build();
    }

}