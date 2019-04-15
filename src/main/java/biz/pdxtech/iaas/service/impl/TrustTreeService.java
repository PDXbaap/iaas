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

package biz.pdxtech.iaas.service.impl;

import biz.pdxtech.iaas.common.exception.TrustTreeServiceException;
import biz.pdxtech.iaas.dao.ChainDao;
import biz.pdxtech.iaas.entity.*;
import biz.pdxtech.iaas.proto.dto.AssignRecordDTO;
import biz.pdxtech.iaas.proto.dto.TrustTxAddressUpdTxDTO;
import biz.pdxtech.iaas.repository.*;
import biz.pdxtech.iaas.service.common.ChainManager;
import biz.pdxtech.iaas.service.common.EmergencyService;
import biz.pdxtech.iaas.service.common.TxService;
import biz.pdxtech.iaas.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrustTreeService {


    @Autowired
    private ChainNodeRepository chainNodeRepository;
    @Autowired
    private ChainRepository chainRepository;
    @Autowired
    private TrustTreeRepository trustTreeRepository;
    @Autowired
    private TxService txService;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private EmergencyService emergencyService;
    @Autowired
    private ChainDao chainDao;

    @Value("${pdx.iaas.tc.service-size}")
    private int tcServiceSize;
    @Value("${pdx.iaas.tc.layer}")
    private int tcLayer;
    @Value("${pdx.iaas.root-trust-chain.name}")
    private String rootTrustChainName;


    /**
     * 为信任链匹配下层业务链
     * Ps. 信任链匹配的实质在于和下层信任链的已服务业务链的转换调整
     *
     * @param chain 当前信任链
     */
    public Chain getLowerTrustChainForTrustChain(Chain chain) {

        log.info("Trust Tree >> Start to get lower-trust-chain for trust-chain >> chainId:{} \n", chain.getChainId());

        TrustTree tree = trustTreeRepository.findByChain(chain);

        if (tree.getLayer() == 0) {
            log.info("Trust Tree >> No lower-trust-chain for root-trust-chain >> chainId:{} \n", chain.getChainId());
            return null;
        }

        // get trust-chain and its lower-trust-chain (strong relation)
        Chain trustChain = tree.getChain();
        Chain lowerTrustChain = tree.getParent().getChain();

        // lower-trust-chain can service
        if (lowerTrustChain.getSizeChain() < tcServiceSize) {
            log.info("Trust Tree >> Lower-trust-chain not full load, got it >> chainId:{}, lowerTrustChainId:{}, lowerTrustChainLoad:{} \n", chain.getChainId(), lowerTrustChain.getChainId(), lowerTrustChain.getSizeChain());
            return lowerTrustChain;
        }

        // lower-trust-chain full service and there must be biz-chian
        List<Chain> bizChainList = chainRepository.findChainsByLowerChainIdAndTypeIsNot(lowerTrustChain.getChainId(), Chain.Type.TRUST_CHAIN);
        if (bizChainList.isEmpty()) {
            log.error("Trust Tree >> Not Found Biz Chain To Replace!");
            throw new TrustTreeServiceException("Not Found Biz Chain To Replace");
        }

        // get biz-chain to replace
        Chain bizChain = bizChainList.get(0);
        log.info("Trust Tree >> Start to replace biz-chain for trust-chain >> chainId:{}, bizChainId:{} \n", chain.getChainId(), bizChain.getChainId());

        // get lower-trust-chain for biz-chain on second high layer trust-chains
        List<Chain> chains = trustTreeRepository.findTrustTreesByLayerOrderByIndexAsc(tree.getLayer() - 1).stream().map(TrustTree::getChain).collect(Collectors.toList());
        Chain toTrustChain = getTrustChainFromFullLayer(chains);
        if (Objects.nonNull(toTrustChain)) {
            log.info("Trust Tree >> Got lower trust chain for bizchain from second high layer >> bizChainId:{}, trustChainId:{} \n", bizChain.getChainId(), toTrustChain.getChainId());
            return toTrustChain;
        }

        // get lower-trust-chain for biz-chain on highest layer trust-chains
        List<Chain> aboveChains = trustTreeRepository.findTrustTreesByLayerOrderByIndexAsc(tree.getLayer()).stream().map(TrustTree::getChain).collect(Collectors.toList());
        toTrustChain = getTrustChainFromNotFullLayer(aboveChains);
        if (Objects.nonNull(toTrustChain)) {
            log.info("Trust Tree >> Got lower trust chain for bizchain from highest layer >> bizChainId:{}, trustChainId:{} \n", bizChain.getChainId(), toTrustChain.getChainId());
            replaceLowerTrustChain(trustChain, bizChain, lowerTrustChain, toTrustChain);
            return lowerTrustChain;
        } else {
            log.error("Error >> Can Not Find Lower Trust Chain To Support Replaced BizChain >> BizChainId:{} \n", bizChain.getChainId());
            throw new TrustTreeServiceException("Can not find lower-trust-chain to support replaced biz-chain");
        }

    }


    /**
     * 为业务链获取下一层信任链
     *
     * @return chain 信任链
     */
    public Chain getLowerTrustChainForBizChain() {

        int c = tcServiceSize;
        int l = tcLayer;

        // search not-full-service trust-chain from highest layer to lowest layer
        for (int i = l; i > 0; i--) {
            List<Chain> curTrustChains = trustTreeRepository.findTrustTreesByLayer(i - 1).stream().map(TrustTree::getChain).collect(Collectors.toList());
            if (!curTrustChains.isEmpty()) {
                if (i != 1) {
                    // full chains size on the under-layer
                    int underFullChainSize = (int) Math.pow(c, i - 1);
                    List<Chain> underTrustChains = trustTreeRepository.findTrustTreesByLayer(i - 2).stream().map(TrustTree::getChain).collect(Collectors.toList());

                    // current serving chains size on the under-layer
                    int sum = underTrustChains.stream().mapToInt(Chain::getSizeChain).sum();

                    if (sum < underFullChainSize) {
                        log.info("Trust Tree >> Start search lower trust chain from layer-{}, layer service amount:{} \n", i - 2, sum);
                        // find chain on this under full-layer
                        return getTrustChainFromNotFullLayer(underTrustChains);
                    } else {
                        log.info("Trust Tree >> Start search lower trust chain from layer-{}, layer service amount:{} \n", i - 1, sum);
                        // find chain on the current full/unfull-layer
                        return getTrustChainFromNotFullLayer(curTrustChains);
                    }
                } else {
                    log.info("Trust Tree >> Start search lower trust chain from layer-{} \n", i - 1);
                    // find chain on the current full/unfull-layer
                    return getTrustChainFromNotFullLayer(curTrustChains);
                }

            }
        }

        throw new TrustTreeServiceException("Not Found Lower Trust Chain For Biz Chain");
    }


    /**
     * 从下层满信任链中获取可用信任链
     *
     * @param chains 下层所有信任链
     * @return chain 可用信任链
     */
    private Chain getTrustChainFromFullLayer(List<Chain> chains) {
        Chain chain = null;

        int s = tcServiceSize;
        for (int i = 0; i < (chains.size() / 2); i++) {
            if (chains.get(i).getSizeChain() < s) {
                if (i == 0) {
                    chain = chains.get(i);
                    break;
                }

                if (chains.get(chains.size() - i + 1).getSizeChain() < s) {
                    chain = chains.get(chains.size() - i + 1);
                    break;
                } else {
                    chain = chains.get(i);
                    break;
                }
            }
        }
        //lower-trust-chain must full nodes
        //if (chain.getSizeDesired()!=chain.getSizeService()){
        //    chain = null;
        //}
        if (Objects.isNull(chain)){
            log.info("Trust Tree >> Not found lower trust chain from full trust chain layer >> lowerTrustChain:{} \n", "");
        }else{
            log.info("Trust Tree >> Got lower trust chain from full trust chain layer >> lowerTrustChain:{} \n", chain.getChainId());
        }
        return chain;
    }


    /**
     * 从下层非满信任链中获取可用信任链
     *
     * @param chains 下层所有信任链
     * @return chain 可用信任链
     */
    private Chain getTrustChainFromNotFullLayer(List<Chain> chains) {

        int s = tcServiceSize;
        Chain chain = null;

        if (chains.size() % 2 != 0) {
            Chain middleChain = chains.get(chains.size() / 2);
            chains.remove(chains.size() / 2);

            int sum = chains.stream().mapToInt(Chain::getSizeChain).sum();
            if (sum < (chains.size() * s)) {
                chain = getTrustChainFromFullLayer(chains);
            } else {
                if (middleChain.getSizeChain() < s) {
                    chain = middleChain;
                }
            }
        } else {
            chain = getTrustChainFromFullLayer(chains);
        }
        //lower-trust-chain must full nodes
        //if (chain.getSizeDesired()!=chain.getSizeService()){
        //    chain = null;
        //}
        if (Objects.isNull(chain)){
            log.info("Trust Tree >> Not found lower trust chain from not full trust chain layer >> lowerTrustChain:{} \n", "");
        }else{
            log.info("Trust Tree >> Got lower trust chain from not full trust chain layer >> lowerTrustChain:{} \n", chain.getChainId());
        }
        return chain;
    }


    /**
     * 匹配 业务链/信任链 和 下层可用信任链
     *
     * @param chain   业务链/信任链
     * @param lowerTC 下层信任链
     */
    public void matchLowerTrustChain(Chain chain, Chain lowerTC) {

        //filter root-trust-chain
        if (chain.getChainId().equals(lowerTC.getChainId())) {
            log.info("Trust Tree >> Self chainid is lower chainid, chainId:{} \n", chain.getChainId());
            return;
        }

        saveAssignRecord(chain, lowerTC.getChainId());
        chain.setLowerChainId(lowerTC.getChainId());
        chainRepository.save(chain);

        chainDao.updateSizeChain(lowerTC, 1);

        log.info("Trust Tree >> Chain matched lower trust chain >> chainId:{}, lowerChainId:{} \n", chain.getChainId(), lowerTC.getChainId());

    }


    /**
     * 发送指派信任链交易
     *
     * @param chain 当前链
     */
    public void sendAssignTrustChainTx(Chain chain) {

        if (rootTrustChainName.equals(chain.getName())) {
            log.info("Trust Tree >> No need assign lower trust chain for root trust chain \n");
            return;
        }

        if (null == chain.getLowerChainId()) {
            log.error("Error >> Not Found Lower Trust Chain >> ChainId:{} \n", chain.getChainId());
            return;
        }

        Chain lowerTC = chainRepository.findChainByChainId(chain.getLowerChainId());
        log.info("Trust Tree >> Assign trust chain start >> targetChainId:{}, lowerTrustChainId:{} \n", chain.getChainId(), lowerTC.getChainId());

        try {
            txService.sendAssignTrustChainTx(lowerTC, chain);
        } catch (Exception e) {
            log.error("ERROR : Send tx >> Match Lower TrustChain >> ChainId:{}, LowerTC:{}, error:{} \n", chain.getChainId(), lowerTC.getChainId(), e);
        }
    }


    /**
     * 发送更新指派交易到新加入的节点
     *
     * @param chain 链
     * @param node  新节点
     */
    public void sendUpdateAssignTrustChainTxToNewNode(Chain chain, Node node) {

        if (rootTrustChainName.equals(chain.getName())) {
            log.info("Trust Tree >> No need assign lower trust chain for root trust chain \n");
            return;
        }

        if (null == chain.getLowerChainId()) {
            log.error("Error >> Not Found Lower Trust Chain >> ChainId:{} \n", chain.getChainId());
            return;
        }

        Chain_Node chainNode = chainNodeRepository.findChain_NodeByChainAndNodeAndStatIsNot(chain, node, Chain_Node.Stat.REMOVED);
        Chain lowerChain = chainRepository.findChainByChainId(chain.getLowerChainId());

        try {
            txService.sendAssignTrustChainTxToNewNode(lowerChain, chainNode);
        } catch (Exception e) {
            log.error("Error: Send Update Assign Trust Chain Tx To New Node, error:{} ", e);
        }

    }


    /**
     * 新信任链获取业务链下层信任链
     *
     * @param trustChain 新信任链
     * @param bizChain   业务链
     * @param fromTC     源下层业务链
     * @param toTC       目标下层业务链
     */
    private void replaceLowerTrustChain(Chain trustChain, Chain bizChain, Chain fromTC, Chain toTC) {

        // save relation of old-biz-chain and new-trust-chain
//        bizChain.setLowerChainId(toTC.getChainId());
//        chainRepository.save(bizChain);

        if (!trustChain.getId().equals(toTC.getId())) {
            // add address to new lower trust chain
            List<Chain_Node> upperChainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(bizChain, Chain_Node.Stat.REMOVED);
            List<String> addressList = upperChainNodes.stream().map(Chain_Node::getNode).map(Node::getAddr).map(Address::getAddr).collect(Collectors.toList());

            updateTrustTxAddressOnLowerChain(addressList, toTC, TrustTxAddressUpdTxDTO.Type.ADD);

            // assigin address to new lower trust chain
            matchLowerTrustChain(bizChain, toTC);

            sendAssignTrustChainTx(bizChain);

            // delete address on old lower trust chain
            updateTrustTxAddressOnLowerChain(addressList, fromTC, TrustTxAddressUpdTxDTO.Type.DELETE);

            // update lower-trust-chian load
            chainDao.updateSizeChain(fromTC, -1);
            chainRepository.save(fromTC);
        }

    }


    /**
     * 保存指派记录
     *
     * @param chain            当前链
     * @param nextLowerChainId 下个下层信任链
     */
    private void saveAssignRecord(Chain chain, Long nextLowerChainId) {
        log.info("Trust Tree >> Start save assign record >> chainId:{}, lowerChainId:{} ", chain.getChainId(), nextLowerChainId);
        String record = chain.getAssignRecord();
        if (Objects.isNull(record)) {
            AssignRecordDTO dto = AssignRecordDTO.builder().preChainId(nextLowerChainId).curChainId(nextLowerChainId).build();
            chain.setAssignRecord(JsonUtil.objToJson(new AssignRecordDTO[]{dto}));
            chainRepository.save(chain);
        } else {
            try {
                AssignRecordDTO[] dtos = JsonUtil.jsonToObj(record, AssignRecordDTO[].class);
                if (!dtos[dtos.length - 1].getCurChainId().equals(chain.getLowerChainId())) {
                    log.error("Trust Tree >> Not match last assign lower-trust-chain-id");
                    throw new TrustTreeServiceException("Not match last assign lower-trust-chain-id");
                }
                AssignRecordDTO[] dtos2 = new AssignRecordDTO[dtos.length + 1];
                System.arraycopy(dtos, 0, dtos2, 0, dtos.length);
                dtos2[dtos2.length - 1] = AssignRecordDTO.builder().preChainId(chain.getLowerChainId()).curChainId(nextLowerChainId).build();
                chain.setAssignRecord(JsonUtil.objToJson(dtos2));
                chainRepository.save(chain);
            } catch (IOException e) {
                log.error("Error >> Trust Tree >> save assign record, error:{}",e);
            }
        }
    }


    /**
     * 更新下层链信任交易白名单地址
     *
     * @param addressList 地址列表
     * @param lowerChain  下层链
     * @param type        增加/删除
     */
    public void updateTrustTxAddressOnLowerChain(List<String> addressList, Chain lowerChain, TrustTxAddressUpdTxDTO.Type type) {

        log.info("Trust Tree >> Update trust tx white list on chain >> lowerChainId:{}, type:{}, addressList:{} \n", lowerChain.getChainId(), type.name(), addressList);
        List<String> addressBatch = new ArrayList<>();

        for (int i = 0; i < addressList.size(); i++) {
            addressBatch.add(addressList.get(i));
            try {
                if ((i + 1) % 10 == 0) {
                    txService.sendUpdateTrustTxAddressTx(lowerChain, addressBatch, type);
                    addressBatch.clear();
                }
                if (i == (addressList.size() - 1) && !addressBatch.isEmpty()) {
                    txService.sendUpdateTrustTxAddressTx(lowerChain, addressBatch, type);
                }
            } catch (Exception e) {
                log.error("Error >> Send Trust Tx Address Update, error:{}", e);
            }

        }
    }


    /**
     * 更新下层链信任交易白名单地址
     *
     * @param upperChain 上层链
     * @param type       更新类型
     */
    public void updateTrustTxAddressOnLowerChain(Chain upperChain, TrustTxAddressUpdTxDTO.Type type) {
        List<Chain_Node> upperChainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(upperChain, Chain_Node.Stat.REMOVED);
        List<String> addressList = upperChainNodes.stream().map(Chain_Node::getNode).map(Node::getAddr).map(Address::getAddr).collect(Collectors.toList());
        if (null == upperChain.getLowerChainId()) {
            log.error("Error >> No Lower Trust Chain To Update TrustTx Address >> ChainId:{} \n", upperChain.getChainId());
            return;
        }
        Chain lowerChain = chainRepository.findChainByChainId(upperChain.getLowerChainId());
        updateTrustTxAddressOnLowerChain(addressList, lowerChain, type);
    }


    /**
     * 更新下层链信任交易白名单地址
     *
     * @param upperChain 上层链
     * @param address    地址
     * @param type       更新类型
     */
    public void updateTrustTxAddressOnLowerChain(Chain upperChain, String address, TrustTxAddressUpdTxDTO.Type type) {
        if (null == upperChain.getLowerChainId()) {
            log.error("Error >> No Lower Trust Chain To Update TrustTx Address >> ChainId:{} \n", upperChain.getChainId());
            return;
        }
        Chain lowerChain = chainRepository.findChainByChainId(upperChain.getLowerChainId());
        updateTrustTxAddressOnLowerChain(Collections.singletonList(address), lowerChain, type);
    }


}
