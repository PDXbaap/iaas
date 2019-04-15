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

import biz.pdxtech.iaas.common.exception.ChainCodeServiceException;
import biz.pdxtech.iaas.proto.dto.DeployInfoDTO;
import biz.pdxtech.iaas.proto.dto.TwoParam;
import biz.pdxtech.iaas.entity.*;
import biz.pdxtech.iaas.repository.*;
import biz.pdxtech.iaas.util.EnumUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChaincodeService {

    @Autowired
    private DeployInfoRepository deployInfoRepository;
    @Autowired
    private ChainNodeRepository chainNodeRepository;
    @Autowired
    private DeployNodeRepository deployNodeRepository;
    @Autowired
    NodeRepository nodeRepository;
    @Autowired
    ChainRepository chainRepository;


    /**
     * 创建合约
     *
     * @param ip
     * @param deployInfo
     */
//    @Transactional
    public void processCreate(String ip, DeployInfo deployInfo) {

        Chain chain = chainRepository.findChainByChainId(Long.valueOf(deployInfo.getChannel()));

        if (Objects.isNull(chain)) {
            log.warn("Not Found Chain! >> ChainId:{}, deployInfo:{} \n", deployInfo.getChannel(), deployInfo);
            return;
        }

        Node node = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);

        //save deploy info
        DeployInfo exsistDeployInfo = deployInfoRepository.findByChaincodeIdAndChannel(deployInfo.getChaincodeId(), deployInfo.getChannel());
        if (exsistDeployInfo == null) {
            deployInfo.setCreatedAt(new Date());
            exsistDeployInfo = deployInfoRepository.save(deployInfo);
            log.info("Chaincode info save success >> chainId:{}, chaincodeId:{} \n", deployInfo.getChannel(), deployInfo.getChaincodeId());
        } else {
            log.info("Chaincode info already exsist >> chainId:{}, chaincodeId:{} \n", deployInfo.getChannel(), deployInfo.getChaincodeId());
        }

        //save deploy node
        Deploy_Node exsistDeployNode = deployNodeRepository.findDeploy_NodeByDeployInfo_IdAndNode_Id(exsistDeployInfo.getId(), node.getId());
        if (exsistDeployNode == null) {
            exsistDeployNode = Deploy_Node.builder()
                    .deployInfo(exsistDeployInfo)
                    .chain(chain)
                    .node(node)
                    .creatAt(new Date())
                    .stat(EnumUtil.getEnumByCode(Deploy_Node.Stat.class, deployInfo.getStatus())).build();
            deployNodeRepository.save(exsistDeployNode);
            log.info("Chaincode on node save success >> chainId:{}, nodeIp:{}, chaincodeId:{} \n", deployInfo.getChannel(), ip, deployInfo.getChaincodeId());
        } else {
            log.info("Chaincode on node already exsist >> chainId:{}, nodeIp:{}, chaincodeId:{} \n", deployInfo.getChannel(), ip, deployInfo.getChaincodeId());
        }

        log.info("Create chaincode finish >> chainId:{}, ip:{}, deployInfo:{} \n", deployInfo.getChannel(), ip, deployInfo);

        try {
            onDeployNodeEvent(exsistDeployNode, chain);
        } catch (Exception e) {
            log.error("On Deploy Node Event Error, error:{}", e);
        }


    }


    /**
     * 修改合约状态
     *
     * @param deployNode    合约节点
     * @param chain         链
     */
    private void onDeployNodeEvent(Deploy_Node deployNode, Chain chain) {

        DeployInfo deployInfo = deployNode.getDeployInfo();

        log.info("Update Deploy Info >> DeployInfoId:{}, ChainId:{}, Status:{} \n", deployInfo.getId(), chain.getId(), deployNode.getStat().name());

        long totalCount = chainNodeRepository.countByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);
        long existStatus = deployNodeRepository.countByDeployInfo_IdAndChain_IdAndStat(deployInfo.getId(), chain.getId(), deployNode.getStat());

        // 4/5 amount
        if (existStatus * 5 >= totalCount * 4) {
            log.info("Update Deploy Info Finish 4/5 >> ChainId:{}, Ip:{} >> totalCount:{}, existStatus:{} \n", chain.getChainId(), deployNode.getNode().getIp(), totalCount, existStatus);
            if (deployInfo.getStatus() != deployNode.getStat().ordinal()) {
                deployInfo.setUpdatedAt(new Date());
                deployInfo.setStatus(deployNode.getStat().ordinal());
                deployInfoRepository.save(deployInfo);
            }
        } else {
            log.info("Update Deploy Info Low Amount >> ChainId:{}, Ip:{} >> totalCount:{}, existStatus:{} \n", chain.getChainId(), deployNode.getNode().getIp(), totalCount, existStatus);

        }

    }


    /**
     * 查询合约
     *
     * @param chaincodeId
     * @param channel
     * @param ip
     * @return
     */
    public DeployInfo findByChaincodeIdAndChannel(@NotNull String chaincodeId, @NotNull String channel, @NotNull String ip) {

        DeployInfo deployInfo = deployInfoRepository.findByChaincodeIdAndChannel(chaincodeId, channel);
        if (Objects.isNull(deployInfo)) {
            log.warn("Not Found DeployInfo >> ChainId:{}, IP:{}, ChainCodeId:{} \n", channel, ip, chaincodeId);
            return null;
        }
        Deploy_Node deployNode = deployNodeRepository.findDeploy_NodeByDeployInfo_IdAndNode_IpAndNode_StatIsNot(deployInfo.getId(), ip, Node.Stat.REMOVED);
        if (Objects.isNull(deployNode)){
            return null;
        }
        deployInfo.setStatus(deployNode.getStat().ordinal());
        return deployInfo;
    }


    /**
     * 更新合约状态
     *
     * @param chaincodeAddress
     * @param channel
     * @param status
     * @param ip
     */
    public void updateDeployNodeStatus(String chaincodeAddress, String channel, int status, String ip) {

        DeployInfo deployInfo = deployInfoRepository.findByChaincodeAddressAndChannel(chaincodeAddress, channel);

        if (deployInfo == null) {
            log.error("Not Found DeployInfo >> chainId:{}, ip:{}, chaincodeAddress:{}, status:{} \n", channel, ip, chaincodeAddress, status);
            throw new ChainCodeServiceException("Update Deploy Node >> Not Found DeployInfo");
        }

        Node node = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);

        if (node == null) {
            log.error("Not Found Node >> chainId:{}, ip:{}, chaincodeAddress:{}, status:{} \n", channel, ip, chaincodeAddress, status);
            throw new ChainCodeServiceException("Update Deploy Node >> Not Found Node");
        }

        //Deploy_Node deployNode = deployNodeRepository.findDeploy_NodeByDeployInfo_IdAndNode_Ip(deployInfo.getId(), ip);
        Deploy_Node deployNode = deployNodeRepository.findDeploy_NodeByDeployInfo_IdAndNode_Id(deployInfo.getId(), node.getId());

        if (deployNode == null) {
            log.warn("Not Found DeployNode >> chainId:{}, ip:{}, chaincodeAddress:{}, status:{} \n", channel, ip, chaincodeAddress, status);
            throw new ChainCodeServiceException("Update Deploy Node >> Not Found DeployNode");
        }

        deployNode.setUpdateAt(new Date());
        deployNode.setStat(EnumUtil.getEnumByCode(Deploy_Node.Stat.class, status));
        deployNodeRepository.save(deployNode);

        Chain chain = chainRepository.findChainByChainId(Long.parseLong(channel));

        try {
            onDeployNodeEvent(deployNode, chain);
        } catch (Exception e) {
            log.error("On Deploy Node Event Error, error:{}", e);
        }

    }


    /**
     * 删除合约
     *
     * @param chaincodeAddress
     * @param channel
     * @param ip
     */
    public void deleteDeployNode(String chaincodeAddress, String channel, String ip) {

        DeployInfo deployInfo = deployInfoRepository.findByChaincodeAddressAndChannel(chaincodeAddress, channel);

        if (null == deployInfo) {
            log.warn("Not Found DeployInfo >> ChainId:{}, IP:{}, ChainCodeAddress:{} \n", channel, ip, chaincodeAddress);
            throw new ChainCodeServiceException("Delete DeployInfo >> Not Found DeployInfo");
        }

        Deploy_Node deployNode = deployNodeRepository.findDeploy_NodeByDeployInfo_IdAndNode_Ip(deployInfo.getId(), ip);

        if (null == deployNode) {
            log.warn("Not Found DeployNode >> ChainId:{}, IP:{}, ChainCodeAddress:{} \n", channel, ip, chaincodeAddress);
            throw new ChainCodeServiceException("Delete DeployInfo >> Not Found DeployNode");
        }
//        Deploy_Node deployNode = getDeploy_nodeByIP(ip, deployInfo);
//        Deploy_Node deployNode = deployNodeRepository.findByDeployInfo_IdAndNode_Id (deployInfoId, nodeId);

        deployNodeRepository.delete(deployNode);
        List<Deploy_Node> deployNodes = deployNodeRepository.findByDeployInfo_Id(deployInfo.getId());

        if (deployNodes == null || deployNodes.size() < 1) {
            deployInfoRepository.delete(deployInfo);
        }
    }


    /**
     * 根据ip和链id查询缺少合约
     *
     * @param ip      机器ip
     * @param chainId 链id
     * @return
     */
    public List<DeployInfoDTO> queryUndownloadChaincode(String ip, String chainId) {

        List<DeployInfo> chaincodeList = deployInfoRepository.findDeployInfosByChannelIs(chainId);

        Node node = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);

        if (Objects.isNull(node)) {
            log.warn("Not Found Node >> ChainId:{}, Ip:{} \n", chainId, ip);
            throw new ChainCodeServiceException("Not Found Node");
        }

        List<Deploy_Node> deployNodeList = deployNodeRepository.findDeploy_NodeByChain_ChainIdAndNode_IdAndStatIs(Long.valueOf(chainId), node.getId(), Deploy_Node.Stat.READY);
        List<DeployInfo> downloadChaincodeList = deployNodeList.stream().map(Deploy_Node::getDeployInfo).collect(Collectors.toList());

        List<DeployInfo> lackList = chaincodeList.stream()
                .filter(deployInfo -> !downloadChaincodeList.contains(deployInfo))
                .filter(deployInfo -> deployInfo.getStatus() != DeployInfo.Stat.ERROR.ordinal())
                .filter(deployInfo -> deployInfo.getStatus() != DeployInfo.Stat.STREAMHANDLING.ordinal()).collect(Collectors.toList());
        List<DeployInfoDTO> deployInfoDTOList = new ArrayList<>();
        for (DeployInfo d : lackList) {
            DeployInfoDTO dto = DeployInfoDTO.builder().build();
            BeanUtils.copyProperties(d, dto);
            deployInfoDTOList.add(dto);
        }
        return deployInfoDTOList;
    }


    @Transactional
    public DeployInfo save(DeployInfo info) {
        return deployInfoRepository.save(info);
    }

    @Transactional
    public void update(int status, long deployTime, String reason, long id) {
        deployInfoRepository.updateDeployStatus(status, deployTime, reason, id);
    }

    @Transactional
    public void delete(long id) {
        deployInfoRepository.deleteById(id);
    }

    @Transactional
    public void delete(List<Long> ids) {
        deployInfoRepository.deleteAllByIdIn(ids);
    }


    public DeployInfo findByChaincodeAddressAndChannel(String chaincodeAddress, String channel, String ip) {

        DeployInfo deployInfo = deployInfoRepository.findByChaincodeAddressAndChannel(chaincodeAddress, channel);
        Deploy_Node deployNode = deployNodeRepository.findDeploy_NodeByDeployInfo_IdAndNode_Ip(deployInfo.getId(), ip);
        deployInfo.setStatus(deployNode.getStat().ordinal());
        return deployInfo;

    }


    public Deploy_Node findByDeployInfo_IdAndNode_Id(Long deployInfoId, Long nodeId) {
        Deploy_Node deployNode = deployNodeRepository.findByDeployInfo_IdAndNode_Id(deployInfoId, nodeId);
        if (deployNode == null) {
            throw new NullPointerException("deployInfoId and nodeId not exsist");
        }
        return deployNode;
    }

    public List<Deploy_Node> findByChainId(Long chainId) {
        return deployNodeRepository.findByChain_Id(chainId);
    }


    public List<DeployInfo> findAllByPbkAndChannel(String pbk, String channel) {
        List<DeployInfo> deployInfos = deployInfoRepository.findAllByPbkAndChannelOrderByIdDesc(pbk, channel);
        return setDeployInfoNodes(deployInfos);
    }

    public List<DeployInfo> findAllByPbk(String pbk) {
        List<DeployInfo> deployInfos = deployInfoRepository.findAllByPbkOrderByIdDesc(pbk);
        return deployInfos;
    }

    public boolean existDeployInfo(String chainId) {
        List<DeployInfo> chaincodeList = deployInfoRepository.findDeployInfosByChannelIs(chainId);
        return !chaincodeList.isEmpty();
    }

    public boolean downloadStatus(String chainId, Node node) {

        List<Deploy_Node> deployNodeList = deployNodeRepository.findDeploy_NodeByChain_ChainIdAndNode_IdAndStatIs(Long.valueOf(chainId), node.getId(), Deploy_Node.Stat.READY);
        List<Deploy_Node> starting = deployNodeList.stream().filter(deployNode -> deployNode.getStat() == Deploy_Node.Stat.READY).collect(Collectors.toList());
        if (starting.isEmpty()) {
            return true;
        }
        for (Deploy_Node deployNode : starting) {
            long span = (System.currentTimeMillis() - deployNode.getCreatAt().getTime()) / (1000 * 60);
//            if (span > 5) {
//                return false;
//            }
        }
        return true;
    }


    private List<DeployInfo> setDeployInfoNodes(List<DeployInfo> deployInfos) {
        return deployInfos.stream().map(
                deployInfo -> {
                    deployInfo.setNodes(deployNodeRepository.findByDeployInfo_Id(deployInfo.getId()).stream().map(ChaincodeService::getTwoParam).collect(Collectors.toList()));
                    return deployInfo;
                }
        ).collect(Collectors.toList());

    }


    private static TwoParam getTwoParam(Deploy_Node deploy_node) {
        return new TwoParam(deploy_node.getNode().getIp(), deploy_node.getStat().ordinal());
    }


}
