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

package biz.pdxtech.iaas.repository;

import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.Node;
import biz.pdxtech.iaas.entity.DeployInfo;
import biz.pdxtech.iaas.entity.Deploy_Node;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeployNodeRepository extends JpaRepository<Deploy_Node, Long> {

    Deploy_Node findByDeployInfo_IdAndNode_Id(Long deployInfoId, Long nodeId);

    Deploy_Node findDeploy_NodeByDeployInfo_IdAndNode_Ip(Long deployId, String nodeIP);

    Deploy_Node findDeploy_NodeByDeployInfo_IdAndNode_IpAndNode_StatIsNot(Long deployId, String nodeIP, Node.Stat stat);

    Deploy_Node findDeploy_NodeByDeployInfo_IdAndNode_Id(Long deployId, Long nodeId);

    List<Deploy_Node> findDeploy_NodeByChain_ChainIdAndNode_IdAndStatIs(Long chainId, Long nodeId, Deploy_Node.Stat stat);

    List<Deploy_Node> findByChain_Id(Long chainId);

    List<Deploy_Node> findByDeployInfo_Id(Long deployInfoId);

    List<Deploy_Node> findDeploy_NodesByDeployInfoAndAndChainAndStatIsNot(DeployInfo deployInfo, Chain chain, Deploy_Node.Stat stat);

    List<Deploy_Node> findDeploy_NodesByDeployInfoAndAndChainAndStat(DeployInfo deployInfo, Chain chain, Deploy_Node.Stat stat);

    List<Deploy_Node> findDeploy_NodesByDeployInfo_IdAndNode_IdInAndStatIs(List<Long> ids, Long nodeId, Deploy_Node.Stat stat);

    List<Deploy_Node> findDeploy_NodesByNode_IdAndStatIsNot(Long nodeId, Deploy_Node.Stat stat);

    long countByDeployInfoAndChainAndStat(DeployInfo deployInfo, Chain chain, Deploy_Node.Stat stat);

    long countByDeployInfo_IdAndChain_IdAndStat(Long deployInfoId, Long chainId, Deploy_Node.Stat stat);

}
